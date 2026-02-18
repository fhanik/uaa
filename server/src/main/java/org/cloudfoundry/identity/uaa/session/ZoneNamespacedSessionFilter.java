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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that supports zone-namespaced session handling when using path-based zones.
 * Runs after IdentityZoneResolvingFilter and before SessionRepositoryFilter.
 * <ul>
 *   <li>Sets the current request's session id (from JSESSIONID cookie) so that
 *       ZoneNamespacedSessionRepository can migrate zone sessions when the session id changes.</li>
 *   <li>After the chain, clears the JSESSIONID cookie when no sessions remain for that id.</li>
 * </ul>
 */
public class ZoneNamespacedSessionFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME_JSESSIONID = "JSESSIONID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String sessionIdFromCookie = getSessionIdFromCookie(request);
        ZoneNamespacedSessionRepository.setPreviousSessionId(request, sessionIdFromCookie);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (ZoneNamespacedSessionRepository.shouldClearSessionCookie(request)) {
                clearSessionCookie(response, request.getContextPath());
            }
        }
    }

    private static String getSessionIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME_JSESSIONID.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static void clearSessionCookie(HttpServletResponse response, String contextPath) {
        Cookie cookie = new Cookie(COOKIE_NAME_JSESSIONID, "");
        cookie.setMaxAge(0);
        cookie.setPath(contextPath != null && !contextPath.isEmpty() ? contextPath : "/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }
}
