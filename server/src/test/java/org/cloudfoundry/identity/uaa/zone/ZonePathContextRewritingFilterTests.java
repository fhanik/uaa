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
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ZonePathContextRewritingFilterTests {

    private ZonePathContextRewritingFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private AtomicReference<HttpServletRequest> requestPassedToChain;

    @BeforeEach
    void setUp() {
        filter = new ZonePathContextRewritingFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        requestPassedToChain = new AtomicReference<>();
    }

    @Test
    void pathWithoutZonePrefix_passesRequestUnchanged() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/login");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        HttpServletRequest passed = requestPassedToChain.get();
        assertThat(passed).isSameAs(request);
        assertThat(passed.getContextPath()).isEqualTo("/uaa");
        assertThat(passed.getRequestURI()).isEqualTo("/uaa/login");
        assertThat(passed.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isNull();
    }

    @Test
    void pathWithOnlyZ_prefix_noSubdomain_passesRequestUnchanged() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/z");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        assertThat(requestPassedToChain.get()).isSameAs(request);
        assertThat(request.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isNull();
    }

    @Test
    void pathWithZAndSubdomainButNoSlashAfter_passesRequestUnchanged() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/z/myzone");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        assertThat(requestPassedToChain.get()).isSameAs(request);
        assertThat(request.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isNull();
    }

    @Test
    void pathWithEmptySubdomainSegment_passesRequestUnchanged() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/z//login");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        assertThat(requestPassedToChain.get()).isSameAs(request);
        assertThat(request.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isNull();
    }

    @Test
    void pathWithZonePrefix_rewritesRequestAndSetsAttribute() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/z/myzone/login");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setScheme("http");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        HttpServletRequest passed = requestPassedToChain.get();
        assertThat(passed).isNotSameAs(request);
        assertThat(passed.getContextPath()).isEqualTo("/uaa/z/myzone");
        assertThat(passed.getServletPath()).isEqualTo("/login");
        assertThat(passed.getRequestURI()).isEqualTo("/uaa/z/myzone/login");
        assertThat(passed.getPathInfo()).isNull();
        assertThat(passed.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isEqualTo("myzone");
    }

    @Test
    void pathWithZonePrefix_trailingSlashOnly_rewritesWithServletPathRoot() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/z/myzone/");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        HttpServletRequest passed = requestPassedToChain.get();
        assertThat(passed.getContextPath()).isEqualTo("/uaa/z/myzone");
        assertThat(passed.getServletPath()).isEqualTo("/");
        assertThat(passed.getRequestURI()).isEqualTo("/uaa/z/myzone/");
        assertThat(passed.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isEqualTo("myzone");
    }

    @Test
    void pathWithZonePrefix_multiplePathSegments_rewritesCorrectly() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/z/myzone/oauth/authorize");
        request.setServerName("localhost");
        request.setServerPort(8443);
        request.setScheme("https");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        HttpServletRequest passed = requestPassedToChain.get();
        assertThat(passed.getContextPath()).isEqualTo("/uaa/z/myzone");
        assertThat(passed.getServletPath()).isEqualTo("/oauth/authorize");
        assertThat(passed.getRequestURI()).isEqualTo("/uaa/z/myzone/oauth/authorize");
        assertThat(passed.getPathInfo()).isNull();
        assertThat(passed.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isEqualTo("myzone");
    }

    @Test
    void emptyContextPath_rewritesCorrectly() throws ServletException, IOException {
        request.setContextPath("");
        request.setRequestURI("/z/testzone/login");
        request.setServerName("localhost");
        request.setServerPort(8080);

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        HttpServletRequest passed = requestPassedToChain.get();
        assertThat(passed.getContextPath()).isEqualTo("/z/testzone");
        assertThat(passed.getServletPath()).isEqualTo("/login");
        assertThat(passed.getRequestURI()).isEqualTo("/z/testzone/login");
        assertThat(passed.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isEqualTo("testzone");
    }

    @Test
    void getRequestURL_onWrappedRequest_returnsRewrittenPath() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/z/foo/login");
        request.setServerName("login.example.com");
        request.setServerPort(443);
        request.setScheme("https");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        HttpServletRequest passed = requestPassedToChain.get();
        StringBuffer url = passed.getRequestURL();
        assertThat(url.toString()).isEqualTo("https://login.example.com/uaa/z/foo/login");
    }

    @Test
    void getRequestURL_withNonStandardPort_includesPort() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/z/bar/profile");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setScheme("http");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        HttpServletRequest passed = requestPassedToChain.get();
        assertThat(passed.getRequestURL().toString()).isEqualTo("http://localhost:8080/uaa/z/bar/profile");
    }

    @Test
    void contextPathSingleSlash_withZonePath_normalizesPathAndRewrites() throws ServletException, IOException {
        request.setContextPath("/");
        request.setRequestURI("/z/rootzone/oauth/token");
        request.setServerName("localhost");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        HttpServletRequest passed = requestPassedToChain.get();
        assertThat(passed.getContextPath()).isEqualTo("/z/rootzone");
        assertThat(passed.getServletPath()).isEqualTo("/oauth/token");
        assertThat(passed.getRequestURI()).isEqualTo("/z/rootzone/oauth/token");
        assertThat(passed.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isEqualTo("rootzone");
    }

    @Test
    void pathAfterContextEmpty_normalizedToSlash_noRewrite() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        assertThat(requestPassedToChain.get()).isSameAs(request);
        assertThat(request.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isNull();
    }

    @Test
    void subdomainWithHyphen_rewritesCorrectly() throws ServletException, IOException {
        request.setContextPath("/uaa");
        request.setRequestURI("/uaa/z/my-zone-name/login");

        FilterChain chain = (req, res) -> requestPassedToChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);

        HttpServletRequest passed = requestPassedToChain.get();
        assertThat(passed.getContextPath()).isEqualTo("/uaa/z/my-zone-name");
        assertThat(passed.getServletPath()).isEqualTo("/login");
        assertThat(passed.getAttribute(ZonePathContextRewritingFilter.ZONE_SUBDOMAIN_FROM_PATH)).isEqualTo("my-zone-name");
    }
}
