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

import org.cloudfoundry.identity.uaa.util.UaaUrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This filter ensures that all requests are targeting a specific identity zone
 * by hostname. If the hostname doesn't match an identity zone, a 404 error is
 * sent.
 *
 */
public class IdentityZoneResolvingFilter extends OncePerRequestFilter implements InitializingBean {

    private final IdentityZoneProvisioning dao;
    private final Set<String> staticResources = Set.of("/resources/", "/vendor/font-awesome/");
    private final Set<String> defaultZoneHostnames = new HashSet<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public IdentityZoneResolvingFilter(final IdentityZoneProvisioning dao) {
        this.dao = dao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String subdomainFromPath = (String) request.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH);
        // Fallback when request was already rewritten (e.g. test simulates post-rewrite with context path set)
        if (subdomainFromPath == null) {
            subdomainFromPath = extractSubdomainFromContextPath(request.getContextPath());
        }

        if (subdomainFromPath != null) {
            String subdomainFromHost = getSubdomain(request.getServerName());
            if (subdomainFromHost != null && !subdomainFromHost.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot use both subdomain and zone path");
                return;
            }
            IdentityZone identityZone = null;
            try {
                identityZone = dao.retrieveBySubdomain(subdomainFromPath);
            } catch (EmptyResultDataAccessException ex) {
                logger.debug("Cannot find identity zone for subdomain {}", subdomainFromPath);
            } catch (Exception ex) {
                String message = "Internal server error while fetching identity zone for subdomain " + subdomainFromPath;
                logger.warn(message, ex);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
                return;
            }
            if (identityZone == null) {
                boolean isStaticResource = staticResources.stream().anyMatch(UaaUrlUtils.getRequestPath(request)::startsWith);
                if (isStaticResource) {
                    filterChain.doFilter(request, response);
                    return;
                }
                request.setAttribute("error_message_code", "zone.not.found");
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot find identity zone for subdomain " + subdomainFromPath);
                return;
            }
            try {
                IdentityZoneHolder.set(identityZone);
                filterChain.doFilter(request, response);
            } finally {
                IdentityZoneHolder.clear();
            }
            return;
        }

        IdentityZone identityZone = null;
        String hostname = request.getServerName();
        String subdomain = getSubdomain(hostname);
        if (subdomain != null) {
            try {
                identityZone = dao.retrieveBySubdomain(subdomain);
            } catch (EmptyResultDataAccessException ex) {
                logger.debug("Cannot find identity zone for subdomain {}", subdomain);
            } catch (Exception ex) {
                String message = "Internal server error while fetching identity zone for subdomain" + subdomain;
                logger.warn(message, ex);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
                return;
            }
        }
        if (identityZone == null) {
            // skip filter to static resources in order to serve images and css in case of invalid zones
            boolean isStaticResource = staticResources.stream().anyMatch(UaaUrlUtils.getRequestPath(request)::startsWith);
            if (isStaticResource) {
                filterChain.doFilter(request, response);
                return;
            }

            request.setAttribute("error_message_code", "zone.not.found");
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot find identity zone for subdomain " + subdomain);
            return;
        }
        try {
            IdentityZoneHolder.set(identityZone);
            filterChain.doFilter(request, response);
        } finally {
            IdentityZoneHolder.clear();
        }
    }

    private static final String ZONE_PATH_PREFIX = "/z/";

    /**
     * If context path ends with /z/{subdomain}, return the subdomain; otherwise null.
     * Supports both /z/myzone and /uaa/z/myzone. Allows zone resolution when the request
     * was already rewritten (e.g. tests simulating post-rewrite with context path set).
     */
    private String extractSubdomainFromContextPath(String contextPath) {
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

    private String getSubdomain(String hostname) {
        String lowerHostName = hostname.toLowerCase();
        if (defaultZoneHostnames.contains(lowerHostName)) {
            return "";
        }
        for (String internalHostname : defaultZoneHostnames) {
            if (lowerHostName.endsWith("." + internalHostname)) {
                return lowerHostName.substring(0, lowerHostName.length() - internalHostname.length() - 1);
            }
        }
        //UAA is catch all if we haven't configured anything
        if (defaultZoneHostnames.size() == 1 && defaultZoneHostnames.contains("localhost")) {
            logger.debug("No root domains configured, UAA is catch-all domain for host:{}", hostname);
            return "";
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Unable to determine subdomain for host:{}; root domains:{}", hostname, Arrays.toString(defaultZoneHostnames.toArray()));
        }
        return null;
    }

    public void setAdditionalInternalHostnames(Set<String> hostnames) {
        if (hostnames != null) {
            hostnames
                    .forEach(
                            entry -> this.defaultZoneHostnames.add(entry.toLowerCase())
                    );
        }
    }

    public void setDefaultInternalHostnames(Set<String> hostnames) {
        if (hostnames != null) {
            hostnames
                    .forEach(
                            entry -> this.defaultZoneHostnames.add(entry.toLowerCase())
                    );
        }
    }

    public synchronized void restoreDefaultHostnames(Set<String> hostnames) {
        this.defaultZoneHostnames.clear();
        if (hostnames != null) {
            hostnames
                    .forEach(
                            entry -> this.defaultZoneHostnames.add(entry.toLowerCase())
                    );
        }
    }

    public Set<String> getDefaultZoneHostnames() {
        return new HashSet<>(defaultZoneHostnames);
    }

    @Override
    public void afterPropertiesSet() throws ServletException {
        super.afterPropertiesSet();
        String domainArray = Arrays.toString(getDefaultZoneHostnames().toArray());
        logger.info("Zone Resolving Root domains are: {}", domainArray);
    }
}
