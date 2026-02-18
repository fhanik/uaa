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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ZoneNamespacedSessionFilterTest {

    private ZoneNamespacedSessionFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new ZoneNamespacedSessionFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Test
    void setsPreviousSessionIdFromCookie() throws Exception {
        request.setCookies(new Cookie("JSESSIONID", "session-123"));
        filter.doFilter(request, response, chain);
        assertThat(request.getAttribute(ZoneNamespacedSessionRepository.class.getName() + ".previousSessionId"))
                .isEqualTo("session-123");
    }

    @Test
    void doesNotSetPreviousSessionIdWhenNoCookie() throws Exception {
        filter.doFilter(request, response, chain);
        assertThat(request.getAttribute(ZoneNamespacedSessionRepository.class.getName() + ".previousSessionId"))
                .isNull();
    }

    @Test
    void clearsCookieWhenAttributeSet() throws Exception {
        request.setAttribute(ZoneNamespacedSessionRepository.REQUEST_ATTR_CLEAR_SESSION_COOKIE, Boolean.TRUE);
        request.setContextPath("/uaa");
        filter.doFilter(request, response, chain);
        assertThat(response.getCookies()).hasSize(1);
        assertThat(response.getCookies()[0].getName()).isEqualTo("JSESSIONID");
        assertThat(response.getCookies()[0].getMaxAge()).isEqualTo(0);
        assertThat(response.getCookies()[0].getPath()).isEqualTo("/uaa");
    }

    @Test
    void doesNotClearCookieWhenAttributeNotSet() throws Exception {
        filter.doFilter(request, response, chain);
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void invokesChain() throws Exception {
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
