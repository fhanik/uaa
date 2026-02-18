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

import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.session.Session;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.identity.uaa.session.ZoneNamespacedSessionRepository.MapSession;

class ZoneNamespacedSessionRepositoryTest {

    private ZoneNamespacedSessionRepository repository;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        repository = new ZoneNamespacedSessionRepository();
        repository.setDefaultMaxInactiveInterval(Duration.ofSeconds(1800));
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        IdentityZoneHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void createSession() {
        Session session = repository.createSession();
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotNull();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(1800));
    }

    @Test
    void saveAndFindById_respectsZone() {
        IdentityZoneHolder.set(IdentityZone.getUaa());
        Session session = repository.createSession();
        session.setAttribute("key", "uaa-value");
        repository.save(session);
        String id = session.getId();

        Session found = repository.findById(id);
        assertThat(found).isNotNull();
        assertThat((String) found.getAttribute("key")).isEqualTo("uaa-value");

        IdentityZone other = new IdentityZone();
        other.setId("other-zone");
        other.setSubdomain("other");
        IdentityZoneHolder.set(other);
        // Same cookie, different zone: findById returns an empty session with the same id (no new cookie)
        Session otherSession = repository.findById(id);
        assertThat(otherSession).isNotNull();
        assertThat(otherSession.getId()).isEqualTo(id);
        otherSession.setAttribute("key", "other-value");
        repository.save(otherSession);

        Session foundOther = repository.findById(id);
        assertThat(foundOther).isNotNull();
        assertThat((String) foundOther.getAttribute("key")).isEqualTo("other-value");

        IdentityZoneHolder.set(IdentityZone.getUaa());
        found = repository.findById(id);
        assertThat(found).isNotNull();
        assertThat((String) found.getAttribute("key")).isEqualTo("uaa-value");
    }

    @Test
    void deleteById_removesOnlyCurrentZone() {
        IdentityZoneHolder.set(IdentityZone.getUaa());
        Session session = repository.createSession();
        repository.save(session);
        String id = session.getId();

        IdentityZone other = new IdentityZone();
        other.setId("other-zone");
        other.setSubdomain("other");
        IdentityZoneHolder.set(other);
        Session otherSession = repository.findById(id);
        assertThat(otherSession).isNotNull();
        repository.save(otherSession);

        repository.deleteById(id);

        // Other zone: no session data but same cookie; findById returns empty session with same id (may have internal zone id attribute)
        Session found = repository.findById(id);
        assertThat(found).isNotNull();
        var names = new java.util.HashSet<>(found.getAttributeNames());
        names.remove(ZoneNamespacedSessionRepository.SESSION_ATTR_ZONE_ID);
        assertThat(names).isEmpty();
        IdentityZoneHolder.set(IdentityZone.getUaa());
        assertThat(repository.findById(id)).isNotNull();
    }

    @Test
    void deleteById_setsClearCookieWhenLastZone() {
        IdentityZoneHolder.set(IdentityZone.getUaa());
        Session session = repository.createSession();
        repository.save(session);
        String id = session.getId();

        repository.deleteById(id);
        assertThat(ZoneNamespacedSessionRepository.shouldClearSessionCookie(request)).isTrue();
    }

    @Test
    void deleteById_doesNotSetClearCookieWhenOtherZonesRemain() {
        IdentityZoneHolder.set(IdentityZone.getUaa());
        Session session = repository.createSession();
        repository.save(session);
        String id = session.getId();

        IdentityZone other = new IdentityZone();
        other.setId("other-zone");
        other.setSubdomain("other");
        IdentityZoneHolder.set(other);
        Session otherSession = repository.findById(id);
        assertThat(otherSession).isNotNull();
        repository.save(otherSession);

        IdentityZoneHolder.set(IdentityZone.getUaa());
        repository.deleteById(id);
        assertThat(ZoneNamespacedSessionRepository.shouldClearSessionCookie(request)).isFalse();
    }

    @Test
    void migrateFromPreviousSessionId() {
        IdentityZoneHolder.set(IdentityZone.getUaa());
        ZoneNamespacedSessionRepository.setPreviousSessionId(request, "old-id");

        Session oldSession = repository.createSession();
        ((MapSession) oldSession).setId("old-id");
        oldSession.setAttribute("k", "v");
        repository.save(oldSession);

        Session newSession = repository.createSession();
        ((MapSession) newSession).setId("new-id");
        newSession.setAttribute("k", "v2");
        repository.save(newSession);

        // After migration old-id is removed from store; findById may return empty session for client-sent id
        Session oldFound = repository.findById("old-id");
        assertThat(oldFound == null || oldFound.getAttribute("k") == null).isTrue();
        assertThat(repository.findById("new-id")).isNotNull();
        assertThat((String) repository.findById("new-id").getAttribute("k")).isEqualTo("v2");

        IdentityZone other = new IdentityZone();
        other.setId("other-zone");
        other.setSubdomain("other");
        IdentityZoneHolder.set(other);
        Session otherOld = repository.createSession();
        ((MapSession) otherOld).setId("old-id");
        otherOld.setAttribute("k", "other-v");
        repository.save(otherOld);

        request.setAttribute(ZoneNamespacedSessionRepository.class.getName() + ".previousSessionId", "old-id");
        Session otherNew = repository.createSession();
        ((MapSession) otherNew).setId("new-id");
        otherNew.setAttribute("k", "other-v2");
        repository.save(otherNew);

        IdentityZoneHolder.set(IdentityZone.getUaa());
        Session found = repository.findById("new-id");
        assertThat((String) found.getAttribute("k")).isEqualTo("v2");
        IdentityZoneHolder.set(other);
        Session foundOther = repository.findById("new-id");
        assertThat((String) foundOther.getAttribute("k")).isEqualTo("other-v2");
    }

    @Test
    void findById_returnsNullForExpiredSession() {
        IdentityZoneHolder.set(IdentityZone.getUaa());
        Session session = repository.createSession();
        session.setMaxInactiveInterval(Duration.ofSeconds(60));
        session.setLastAccessedTime(java.time.Instant.now().minus(Duration.ofHours(1)));
        repository.save(session);
        String id = session.getId();

        Session found = repository.findById(id);
        assertThat(found).isNull();
    }
}
