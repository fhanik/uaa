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

import org.cloudfoundry.identity.uaa.zone.IdentityZoneProvisioning;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.session.MapSessionRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import jakarta.servlet.Filter;

/**
 * Registers {@link SessionZoneResolutionFilter} so the identity zone is set before
 * the session is loaded. Only active when using memory session store (zone-namespaced sessions).
 */
@Configuration
@ConditionalOnBean(MapSessionRepository.class)
public class SessionZoneResolutionFilterConfiguration {

    @Bean(name = SessionZoneResolutionFilter.BEAN_NAME)
    public SessionZoneResolutionFilter sessionZoneResolutionFilter(IdentityZoneProvisioning provisioning) {
        return new SessionZoneResolutionFilter(provisioning);
    }

    @Bean
    public FilterRegistrationBean<SessionZoneResolutionFilter> sessionZoneResolutionFilterRegistration(
            SessionZoneResolutionFilter filter) {
        FilterRegistrationBean<SessionZoneResolutionFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 49); // after ZonePathContextRewritingFilter (48), before SessionRepositoryFilter (50)
        bean.addUrlPatterns("/*");
        return bean;
    }

    /**
     * Register the session filter after zone resolution so ZoneNamespacedSessionRepository
     * sees the correct zone. When this is active, UaaBootConfiguration does not register
     * springSessionRepositoryFilter (avoid double registration).
     */
    @Bean
    public FilterRegistrationBean<Filter> zoneAwareSpringSessionRepositoryFilterRegistration(
            @org.springframework.beans.factory.annotation.Qualifier("springSessionRepositoryFilter") Filter sessionFilter) {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>(sessionFilter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 50); // after SessionZoneResolutionFilter (49)
        bean.addUrlPatterns("/*");
        return bean;
    }
}
