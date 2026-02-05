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
package org.cloudfoundry.identity.uaa.security.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 */
@ParameterizedClass
@MethodSource("useZonePaths")
class UaaRequestMatcherTests {

    static Stream<Arguments> useZonePaths() {
        return Stream.of(
                Arguments.of(false, (String) null),
                Arguments.of(true, UUID.randomUUID().toString())
        );
    }

    private final boolean withZonePrefix;
    private final String zoneId;
    private final UaaRequestMatcher matcher;

    UaaRequestMatcherTests(boolean withZonePrefix, String zoneId) {
        this.withZonePrefix = withZonePrefix;
        this.zoneId = zoneId;
        this.matcher = new UaaRequestMatcher("/somePath", withZonePrefix);
    }

    private MockHttpServletRequest request(String path, String accept, String... parameters) {
        String actualPath = withZonePrefix ? "/z/" + zoneId + path : path;
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/ctx");
        request.setRequestURI("/ctx" + actualPath);
        if (accept != null) {
            request.addHeader("Accept", accept);
        }
        for (int i = 0; i < parameters.length; i += 2) {
            String key = parameters[i];
            String value = parameters[i + 1];
            request.addParameter(key, value);
        }
        return request;
    }

    @Test
    void pathMatcherMatchesExpectedPaths() {
        assertThat(matcher.matches(request("/somePath", null))).isTrue();
        assertThat(matcher.matches(request("/somePath", "application/json"))).isTrue();
        assertThat(matcher.matches(request("/somePath", "application/html"))).isTrue();
        assertThat(matcher.matches(request("/somePath/aak", null))).isTrue();
        assertThat(matcher.matches(request("/somePath?blah=x", null))).isTrue();
        // We don't actually want this for anything but it's a consequence of
        // using substring matching
        assertThat(matcher.matches(request("/somePathOrOther", null))).isTrue();
    }

    @Test
    void pathMatcherMatchesExpectedPathsAndAcceptHeaderNull() {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));
        assertThat(matcher.matches(request("/somePath", null))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // blank strings
            "", "   ", "\n", "\t", " \n", "\t   ",
            // strings with only commas and whitespace -> will be treated like blank strings internally
            ",", "  ,  ", "  , \n", " , , ", " , \t ,\n"
    })
    void pathMatcherMatchesExpectedPathsAndAcceptHeaderBlankOrEmpty(final String blankAcceptHeaderValue) {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));

        assertThat(matcher.matches(request("/somePath", blankAcceptHeaderValue))).isFalse();
    }

    @Test
    void pathMatcherMatchesExpectedPathsAndMatchingAcceptHeader() {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));
        assertThat(matcher.matches(request("/somePath", "application/json"))).isTrue();
    }

    @Test
    void pathMatcherMatchesExpectedPathsAndNonMatchingAcceptHeader() {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));
        assertThat(matcher.matches(request("/somePath", "application/html"))).isFalse();
    }

    @Test
    void pathMatcherMatchesExpectedPathsAndRequestParameters() {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));
        matcher.setParameters(Collections.singletonMap("response_type", "token"));
        assertThat(matcher.matches(request("/somePath", null, "response_type", "token"))).isTrue();
    }

    @Test
    void pathMatcherMatchesExpectedPathsAndMultipleRequestParameters() {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("source", "foo");
        params.put("response_type", "token");
        matcher.setParameters(params);
        assertThat(matcher.matches(request("/somePath", null, "response_type", "token"))).isFalse();
        assertThat(matcher.matches(request("/somePath", null, "response_type", "token", "source", "foo"))).isTrue();
    }

    @Test
    void pathMatcherMatchesExpectedPathsAndEmptyParameters() {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));
        matcher.setParameters(Collections.singletonMap("code", ""));
        assertThat(matcher.matches(request("/somePath", null, "code", "FOO"))).isTrue();
        assertThat(matcher.matches(request("/somePath", null))).isFalse();
    }

    @Test
    void pathMatcherMatchesExpectedPathsAndRequestParametersWithAcceptHeader() {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));
        matcher.setParameters(Collections.singletonMap("response_type", "token"));
        assertThat(matcher.matches(request("/somePath", "application/json", "response_type", "token"))).isTrue();
    }

    @Test
    void pathMatcherMatchesExpectedPathsAndRequestParametersWithNonMatchingAcceptHeader() {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));
        matcher.setParameters(Collections.singletonMap("response_type", "token"));
        assertThat(matcher.matches(request("/somePath", "application/html", "response_type", "token"))).isFalse();
    }

    @Test
    void pathMatcherMatchesWithMultipleAccepts() {
        // Accept only JSON
        matcher.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON.toString()));
        assertThat(matcher
                .matches(request("/somePath",
                        "%s,%s".formatted(MediaType.APPLICATION_JSON.toString(),
                                MediaType.APPLICATION_XML.toString())))).isTrue();
    }
    @Test
    void pathMatcherMatchesWithMultipleAcceptTargets() {
        // Accept only JSON
        matcher.setAccept(Arrays.asList(MediaType.APPLICATION_JSON.toString(),
                MediaType.APPLICATION_FORM_URLENCODED.toString()));
        assertThat(matcher
                .matches(request("/somePath",
                        "%s,%s".formatted(MediaType.APPLICATION_JSON.toString(),
                                MediaType.APPLICATION_XML.toString())))).isTrue();
    }

    @Test
    void pathMatcherMatchesWithSingleHeader() {
        matcher.setHeaders(Collections.singletonMap("Authorization", Collections.singletonList("Basic")));
        MockHttpServletRequest testRequest = request(
                "/somePath",
                "%s,%s".formatted(MediaType.APPLICATION_JSON.toString(),
                        MediaType.APPLICATION_XML.toString()));
        testRequest.addHeader("Authorization", "Basic abc");
        assertThat(matcher
                .matches(testRequest)).isTrue();
    }

    @Test
    void pathMatcherDoesNotMatchInvalidHeader() {
        matcher.setHeaders(Collections.singletonMap("Authorization", Collections.singletonList("Basic")));
        MockHttpServletRequest testRequest = request(
                "/somePath",
                "%s,%s".formatted(MediaType.APPLICATION_JSON.toString(),
                        MediaType.APPLICATION_XML.toString()));
        assertThat(matcher
                .matches(testRequest)).isFalse();
    }

    @Test
    void pathMatcherMatchesOneOfMultipleHeaders() {
        Map<String, List<String>> configMap = new HashMap<>();
        configMap.put("Authorization", Arrays.asList(new String[]{"Basic", "Bearer"}));
        matcher.setHeaders(configMap);
        MockHttpServletRequest testRequest = request(
                "/somePath",
                "%s,%s".formatted(MediaType.APPLICATION_JSON.toString(),
                        MediaType.APPLICATION_XML.toString()));
        testRequest.addHeader("Authorization", "Basic abc");
        assertThat(matcher
                .matches(testRequest)).isFalse();
    }

    @Test
    void pathMatcherDoesNotMatchOneOfMultipleHeaders() {
        Map<String, List<String>> configMap = new HashMap<>();
        configMap.put("Authorization", Arrays.asList(new String[]{"Basic", "Bearer"}));
        matcher.setHeaders(configMap);
        MockHttpServletRequest testRequest = request(
                "/somePath",
                "%s,%s".formatted(MediaType.APPLICATION_JSON.toString(),
                        MediaType.APPLICATION_XML.toString()));
        testRequest.addHeader("Authorization", "non matching header value");
        assertThat(matcher
                .matches(testRequest)).isFalse();
    }
}
