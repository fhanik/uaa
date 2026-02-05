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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This filter ensures that all requests are targeting a specific identity zone
 * by hostname or by path prefix /z/{subdomain}/. If the hostname doesn't match
 * an identity zone, a 404 error is sent. Using both a subdomain (host) and a /z/
 * path is not allowed and returns 400.
 */
public class IdentityZoneResolvingFilter extends OncePerRequestFilter implements InitializingBean {

    private static final String ZONE_PATH_PREFIX = "/z/";

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
        String requestPath = UaaUrlUtils.getRequestPath(request);
        String subdomainFromHost = getSubdomainFromHost(request.getServerName());
        String subdomainFromPath = getSubdomainFromPath(requestPath);

        // 400: path starts with /z/ and host has a zone subdomain
        if (requestPath.startsWith(ZONE_PATH_PREFIX) && StringUtils.hasText(subdomainFromHost)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot use both subdomain and zone path");
            return;
        }

        // Host always overrides path domain - path domain only works if there is no Host subdomain
        String subdomain = StringUtils.hasText(subdomainFromPath) && "".equals(subdomainFromHost) ?
                subdomainFromPath : subdomainFromHost;

        IdentityZone identityZone = null;
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
            boolean isStaticResource = staticResources.stream().anyMatch(requestPath::startsWith);
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

    /**
     * Returns the subdomain if path starts with /z/{subdomain}/, otherwise null.
     */
    private String getSubdomainFromPath(String path) {
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

    private String getSubdomainFromHost(String hostname) {
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
