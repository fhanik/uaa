/*
 * *****************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2016] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.session;

import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session repository that namespaces sessions by (sessionId, zoneId).
 * One JSESSIONID cookie can hold multiple logical sessions, one per identity zone.
 * Zone is taken from IdentityZoneHolder (must be set before session resolution).
 */
public class ZoneNamespacedSessionRepository implements SessionRepository<Session> {

    private static final String REQUEST_ATTR_PREVIOUS_SESSION_ID = ZoneNamespacedSessionRepository.class.getName() + ".previousSessionId";
    static final String REQUEST_ATTR_CLEAR_SESSION_COOKIE = ZoneNamespacedSessionRepository.class.getName() + ".clearSessionCookie";

    private static final Logger logger = LoggerFactory.getLogger(ZoneNamespacedSessionRepository.class);

    private final Map<String, Map<String, Session>> store = new ConcurrentHashMap<>();
    private Duration defaultMaxInactiveInterval = Duration.ofSeconds(1800);

    @Override
    public Session createSession() {
        Session session = new MapSession();
        session.setMaxInactiveInterval(defaultMaxInactiveInterval);
        return session;
    }

    @Override
    public void save(Session session) {
        String zoneId = getZoneId();
        String sessionId = session.getId();

        migrateFromPreviousSessionIdIfNeeded(sessionId);

        store.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(zoneId, copySession(session));
    }

    @Override
    public Session findById(String id) {
        if (id == null) {
            return null;
        }
        String zoneId = getZoneId();
        Map<String, Session> zoneSessions = store.get(id);
        if (zoneSessions == null) {
            return null;
        }
        Session session = zoneSessions.get(zoneId);
        if (session == null || session.isExpired()) {
            if (session != null) {
                zoneSessions.remove(zoneId);
                removeSessionIdIfEmpty(id);
            }
            return null;
        }
        return copySession(session);
    }

    @Override
    public void deleteById(String id) {
        if (id == null) {
            return;
        }
        String zoneId = getZoneId();
        Map<String, Session> zoneSessions = store.get(id);
        if (zoneSessions != null) {
            zoneSessions.remove(zoneId);
            removeSessionIdIfEmpty(id);

            if (!store.containsKey(id)) {
                requestClearSessionCookie();
            }
        }
    }

    public void setDefaultMaxInactiveInterval(Duration defaultMaxInactiveInterval) {
        this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
    }

    private String getZoneId() {
        try {
            return IdentityZoneHolder.get().getId();
        } catch (Exception e) {
            logger.debug("No identity zone in holder, using default");
            return "uaa";
        }
    }

    private void migrateFromPreviousSessionIdIfNeeded(String newSessionId) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        String previousId = (String) request.getAttribute(REQUEST_ATTR_PREVIOUS_SESSION_ID);
        if (previousId == null || previousId.equals(newSessionId)) {
            return;
        }
        Map<String, Session> previousZones = store.remove(previousId);
        if (previousZones != null && !previousZones.isEmpty()) {
            Map<String, Session> newZones = store.computeIfAbsent(newSessionId, k -> new ConcurrentHashMap<>());
            for (Map.Entry<String, Session> e : previousZones.entrySet()) {
                newZones.putIfAbsent(e.getKey(), copySession(e.getValue()));
            }
            logger.debug("Migrated zone sessions from {} to {}", previousId, newSessionId);
        }
    }

    private void removeSessionIdIfEmpty(String id) {
        Map<String, Session> zoneSessions = store.get(id);
        if (zoneSessions != null && zoneSessions.isEmpty()) {
            store.remove(id);
        }
    }

    private void requestClearSessionCookie() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            request.setAttribute(REQUEST_ATTR_CLEAR_SESSION_COOKIE, Boolean.TRUE);
        }
    }

    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static void setPreviousSessionId(HttpServletRequest request, String sessionId) {
        if (request != null && sessionId != null) {
            request.setAttribute(REQUEST_ATTR_PREVIOUS_SESSION_ID, sessionId);
        }
    }

    static boolean shouldClearSessionCookie(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(REQUEST_ATTR_CLEAR_SESSION_COOKIE));
    }

    private static Session copySession(Session source) {
        MapSession copy = new MapSession();
        copy.setId(source.getId());
        copy.setCreationTime(source.getCreationTime());
        copy.setLastAccessedTime(source.getLastAccessedTime());
        copy.setMaxInactiveInterval(source.getMaxInactiveInterval());
        for (String name : source.getAttributeNames()) {
            copy.setAttribute(name, source.getAttribute(name));
        }
        return copy;
    }

    /**
     * Minimal Session implementation that holds session data in memory.
     * Does not reference any container session.
     */
    public static class MapSession implements Session {
        private String id;
        private Instant creationTime = Instant.now();
        private Instant lastAccessedTime = creationTime;
        private Duration maxInactiveInterval = Duration.ofSeconds(1800);
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        public MapSession() {
            this.id = java.util.UUID.randomUUID().toString();
        }

        public MapSession(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String changeSessionId() {
            String newId = java.util.UUID.randomUUID().toString();
            this.id = newId;
            return newId;
        }

        @Override
        public Instant getCreationTime() {
            return creationTime;
        }

        public void setCreationTime(Instant creationTime) {
            this.creationTime = creationTime;
        }

        @Override
        public void setLastAccessedTime(Instant lastAccessedTime) {
            this.lastAccessedTime = lastAccessedTime;
        }

        @Override
        public Instant getLastAccessedTime() {
            return lastAccessedTime;
        }

        @Override
        public void setMaxInactiveInterval(Duration interval) {
            this.maxInactiveInterval = interval;
        }

        @Override
        public Duration getMaxInactiveInterval() {
            return maxInactiveInterval;
        }

        @Override
        public <T> T getAttribute(String name) {
            return (T) attributes.get(name);
        }

        @Override
        public Set<String> getAttributeNames() {
            return Collections.unmodifiableSet(attributes.keySet());
        }

        @Override
        public void setAttribute(String name, Object value) {
            if (value != null) {
                attributes.put(name, value);
            } else {
                attributes.remove(name);
            }
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public boolean isExpired() {
            return maxInactiveInterval.isNegative() || maxInactiveInterval.isZero()
                    ? false
                    : Instant.now().minus(maxInactiveInterval).isAfter(lastAccessedTime);
        }
    }
}
