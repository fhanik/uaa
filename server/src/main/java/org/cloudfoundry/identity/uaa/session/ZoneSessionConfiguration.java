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

import org.cloudfoundry.identity.uaa.UaaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

import java.time.Duration;

/**
 * Configuration for zone-namespaced session support (path-based zones).
 * When using memory session store, provides a repository that namespaces sessions by (sessionId, zoneId).
 * ZoneNamespacedSessionFilter runs in the security chain after IdentityZoneResolvingFilter (so zone
 * and rate limiting etc. are unchanged).
 */
@Configuration
@EnableSpringHttpSession
public class ZoneSessionConfiguration {

    @Bean
    @Primary
    public SessionRepository<Session> zoneNamespacedSessionRepository(UaaProperties.Servlet servlet) {
        ZoneNamespacedSessionRepository repository = new ZoneNamespacedSessionRepository();
        repository.setDefaultMaxInactiveInterval(Duration.ofSeconds(servlet.idleTimeout()));
        return repository;
    }

    @Bean
    public ZoneNamespacedSessionFilter zoneNamespacedSessionFilter() {
        return new ZoneNamespacedSessionFilter();
    }

    /**
     * Registration is disabled so the filter is only added via the security chain
     * (after IdentityZoneResolvingFilter), not at servlet level.
     */
    @Bean
    public FilterRegistrationBean<ZoneNamespacedSessionFilter> zoneNamespacedSessionFilterRegistration(
            ZoneNamespacedSessionFilter zoneNamespacedSessionFilter) {
        FilterRegistrationBean<ZoneNamespacedSessionFilter> bean =
                new FilterRegistrationBean<>(zoneNamespacedSessionFilter);
        bean.setEnabled(false);
        return bean;
    }

}
