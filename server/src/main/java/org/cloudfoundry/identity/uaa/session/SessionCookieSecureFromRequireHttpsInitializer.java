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

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.ConfigurableWebApplicationContext;

import java.util.Collections;
import java.util.Map;

/**
 * Runs after YAML config is loaded. When using container session (session-store=servlet),
 * sets {@code server.servlet.session.cookie.secure} from UAA's {@code require_https} so
 * Spring Boot configures the JSESSIONID cookie before the context is initialised.
 */
public class SessionCookieSecureFromRequireHttpsInitializer
        implements org.springframework.context.ApplicationContextInitializer<ConfigurableWebApplicationContext> {

    private static final String SESSION_STORE_SERVLET = "servlet";
    private static final String SERVER_SERVLET_SESSION_COOKIE_SECURE = "server.servlet.session.cookie.secure";

    @Override
    public void initialize(ConfigurableWebApplicationContext applicationContext) {
        ConfigurableEnvironment env = applicationContext.getEnvironment();
        if (!SESSION_STORE_SERVLET.equals(env.getProperty("servlet.session-store", "memory"))) {
            return;
        }
        boolean requireHttps = Boolean.parseBoolean(env.getProperty("require_https", "false"));
        Map<String, Object> source = Collections.singletonMap(SERVER_SERVLET_SESSION_COOKIE_SECURE, requireHttps);
        env.getPropertySources().addFirst(
                new MapPropertySource("sessionCookieSecureFromRequireHttps", source));
    }
}
