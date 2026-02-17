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
package org.cloudfoundry.identity.uaa.zone;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs first in the filter chain. If the request path (after context path) starts with
 * {@code /z/{subdomain}/}, rewrites the request so that the context path includes
 * {@code /z/{subdomain}}. Downstream code then sees a normal path (e.g. {@code /login})
 * and builds URLs using {@code getContextPath()} which already includes the zone path.
 * <p>
 * Does not validate the zone; {@link IdentityZoneResolvingFilter} looks up the zone by
 * subdomain and rejects invalid or missing zones. Sets request attribute
 * {@link #ZONE_SUBDOMAIN_FROM_PATH} with the subdomain string for {@link IdentityZoneResolvingFilter}.
 */
public class ZonePathContextRewritingFilter extends OncePerRequestFilter {

    private static final String ZONE_PATH_PREFIX = "/z/";

    /**
     * Request attribute set when the request was rewritten for a path-based zone.
     * Value is the subdomain string (e.g. "myzone"). Read by {@link IdentityZoneResolvingFilter} to look up and validate the zone.
     */
    public static final String ZONE_SUBDOMAIN_FROM_PATH = "org.cloudfoundry.identity.uaa.zone.ZoneSubdomainFromPath";

    public ZonePathContextRewritingFilter() {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
        String requestURI = request.getRequestURI() != null ? request.getRequestURI() : "";
        String pathAfterContext = requestURI.startsWith(contextPath)
                ? requestURI.substring(contextPath.length())
                : requestURI;
        if (pathAfterContext.isEmpty()) {
            pathAfterContext = "/";
        }
        if (!pathAfterContext.startsWith("/")) {
            pathAfterContext = "/" + pathAfterContext;
        }

        if (!pathAfterContext.startsWith(ZONE_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String subdomain = extractSubdomainFromPath(pathAfterContext);
        if (subdomain == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String pathAfterZonePrefix = pathAfterContext.substring(ZONE_PATH_PREFIX.length() + subdomain.length());
        if (!pathAfterZonePrefix.startsWith("/")) {
            pathAfterZonePrefix = "/" + pathAfterZonePrefix;
        }
        if (pathAfterZonePrefix.isEmpty()) {
            pathAfterZonePrefix = "/";
        }

        String baseContext = (contextPath != null && contextPath.endsWith("/"))
                ? contextPath.substring(0, contextPath.length() - 1) : contextPath;
        String newContextPath = (baseContext != null ? baseContext : "") + ZONE_PATH_PREFIX + subdomain;
        HttpServletRequest wrappedRequest = new ZonePathRewrittenRequest(request, newContextPath, pathAfterZonePrefix);
        wrappedRequest.setAttribute(ZONE_SUBDOMAIN_FROM_PATH, subdomain);

        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * Returns the subdomain if path starts with /z/{subdomain}/ (with at least one character after the second slash), otherwise null.
     */
    private String extractSubdomainFromPath(String path) {
        if (path == null || !path.startsWith(ZONE_PATH_PREFIX)) {
            return null;
        }
        String afterPrefix = path.substring(ZONE_PATH_PREFIX.length());
        int slash = afterPrefix.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String subdomain = afterPrefix.substring(0, slash);
        return StringUtils.hasText(subdomain) ? subdomain : null;
    }

    /**
     * Wraps the request so that getContextPath(), getServletPath(), getRequestURI(), getPathInfo(), getRequestURL()
     * reflect the rewritten path (zone prefix absorbed into context path).
     */
    private static final class ZonePathRewrittenRequest extends HttpServletRequestWrapper {

        private final String contextPath;
        private final String servletPath;
        private final String requestURI;

        ZonePathRewrittenRequest(HttpServletRequest request, String contextPath, String pathAfterZone) {
            super(request);
            this.contextPath = contextPath;
            this.servletPath = pathAfterZone;
            this.requestURI = contextPath + pathAfterZone;
        }

        @Override
        public String getContextPath() {
            return contextPath;
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public String getRequestURI() {
            return requestURI;
        }

        @Override
        public String getPathTranslated() {
            return null;
        }

        @Override
        public StringBuffer getRequestURL() {
            HttpServletRequest req = (HttpServletRequest) getRequest();
            String scheme = req.getScheme();
            String serverName = req.getServerName();
            int serverPort = req.getServerPort();
            StringBuilder url = new StringBuilder();
            url.append(scheme).append("://").append(serverName);
            if (("http".equals(scheme) && serverPort != 80) || ("https".equals(scheme) && serverPort != 443)) {
                url.append(':').append(serverPort);
            }
            url.append(requestURI);
            return new StringBuffer(url);
        }
    }
}
