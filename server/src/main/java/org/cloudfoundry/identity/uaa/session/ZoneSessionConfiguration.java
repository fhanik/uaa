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
import org.cloudfoundry.identity.uaa.zone.IdentityZoneResolvingFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;

import java.time.Duration;

/**
 * Configuration for zone-namespaced session support (path-based zones).
 * When using memory session store, provides a repository that namespaces sessions by (sessionId, zoneId)
 * and ensures zone resolution runs before session resolution at servlet level.
 */
@Configuration
@ConditionalOnBean(MapSessionRepository.class)
public class ZoneSessionConfiguration {

    @Bean
    @Primary
    public SessionRepository<?> zoneNamespacedSessionRepository(UaaProperties.Servlet servlet) {
        ZoneNamespacedSessionRepository repository = new ZoneNamespacedSessionRepository();
        repository.setDefaultMaxInactiveInterval(Duration.ofSeconds(servlet.idleTimeout()));
        return repository;
    }

    @Bean
    public ZoneNamespacedSessionFilter zoneNamespacedSessionFilter() {
        return new ZoneNamespacedSessionFilter();
    }

    @Bean
    public FilterRegistrationBean<ZoneNamespacedSessionFilter> zoneNamespacedSessionFilterRegistration(
            ZoneNamespacedSessionFilter zoneNamespacedSessionFilter) {
        FilterRegistrationBean<ZoneNamespacedSessionFilter> bean =
                new FilterRegistrationBean<>(zoneNamespacedSessionFilter);
        bean.setEnabled(false);
        return bean;
    }

    /**
     * Registers IdentityZoneResolvingFilter at servlet level with highest precedence
     * so that zone is set before SessionRepositoryFilter runs.
     */
    @Bean
    public FilterRegistrationBean<IdentityZoneResolvingFilter> zoneResolutionServletFilterRegistration(
            IdentityZoneResolvingFilter identityZoneResolvingFilter) {
        FilterRegistrationBean<IdentityZoneResolvingFilter> bean =
                new FilterRegistrationBean<>(identityZoneResolvingFilter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }

}
