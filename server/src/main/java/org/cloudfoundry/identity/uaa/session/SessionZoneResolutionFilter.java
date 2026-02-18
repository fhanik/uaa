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
import org.cloudfoundry.identity.uaa.zone.IdentityZoneProvisioning;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Resolves the identity zone from the request and sets {@link IdentityZoneHolder} before
 * {@link org.springframework.session.web.http.SessionRepositoryFilter} runs. This allows
 * {@link ZoneNamespacedSessionRepository} to load the correct per-zone session.
 * <p>
 * Reads subdomain from request attribute {@link org.cloudfoundry.identity.uaa.zone.ZonePathContextRewritingFilter#ZONE_SUBDOMAIN_FROM_PATH}
 * (set when the request path is /z/{subdomain}/...). If not present, uses default zone.
 * Does not send 404/500; {@link org.cloudfoundry.identity.uaa.zone.IdentityZoneResolvingFilter} in the
 * security chain still performs validation.
 * <p>
 * Sets request attribute {@link #REQUEST_ATTR_ZONE_ID_FOR_SESSION} with the zone id so
 * {@link ZoneNamespacedSessionRepository} can use it when saving the session (save may run
 * after this filter's finally clears {@link IdentityZoneHolder}).
 */
public class SessionZoneResolutionFilter extends OncePerRequestFilter {

    public static final String BEAN_NAME = "sessionZoneResolutionFilter";

    /**
     * Request attribute with the identity zone id for the current request.
     * Used by {@link ZoneNamespacedSessionRepository} so save() uses the correct zone even
     * if it runs after this filter has cleared {@link IdentityZoneHolder}.
     */
    public static final String REQUEST_ATTR_ZONE_ID_FOR_SESSION = SessionZoneResolutionFilter.class.getName() + ".zoneId";

    private final IdentityZoneProvisioning provisioning;

    public SessionZoneResolutionFilter(IdentityZoneProvisioning provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String subdomain = (String) request.getAttribute(
                org.cloudfoundry.identity.uaa.zone.ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH);
        if (subdomain == null) {
            subdomain = extractSubdomainFromContextPath(request.getContextPath());
        }
        IdentityZone zone;
        try {
            if (StringUtils.hasText(subdomain)) {
                zone = provisioning.retrieveBySubdomain(subdomain);
                IdentityZoneHolder.set(zone);
            } else {
                zone = IdentityZone.getUaa();
                IdentityZoneHolder.set(zone);
            }
        } catch (EmptyResultDataAccessException e) {
            zone = IdentityZone.getUaa();
            IdentityZoneHolder.set(zone);
        } catch (Exception e) {
            zone = IdentityZone.getUaa();
            IdentityZoneHolder.set(zone);
        }
        request.setAttribute(REQUEST_ATTR_ZONE_ID_FOR_SESSION, zone.getId());
        try {
            filterChain.doFilter(request, response);
        } finally {
            IdentityZoneHolder.clear();
        }
    }

    private static final String ZONE_PATH_PREFIX = "/z/";

    private static String extractSubdomainFromContextPath(String contextPath) {
        if (!StringUtils.hasText(contextPath) || !contextPath.contains(ZONE_PATH_PREFIX)) {
            return null;
        }
        int idx = contextPath.lastIndexOf(ZONE_PATH_PREFIX);
        if (idx < 0) {
            return null;
        }
        String after = contextPath.substring(idx + ZONE_PATH_PREFIX.length());
        int slash = after.indexOf('/');
        String subdomain = slash < 0 ? after : after.substring(0, slash);
        return StringUtils.hasText(subdomain) ? subdomain : null;
    }
}
