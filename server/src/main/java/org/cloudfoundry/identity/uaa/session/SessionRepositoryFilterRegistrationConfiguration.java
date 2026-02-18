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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Registers {@link SessionRepositoryFilter} (bean name {@code springSessionRepositoryFilter}).
 * When zone-namespaced sessions are used (memory store), {@link SessionZoneResolutionFilter}
 * is present and this registration is disabled so that
 * {@link SessionZoneResolutionFilterConfiguration} can register the session filter with
 * correct order (after zone resolution).
 */
@Configuration
@ConditionalOnBean(name = "springSessionRepositoryFilter")
public class SessionRepositoryFilterRegistrationConfiguration {

    @Bean
    public FilterRegistrationBean<SessionRepositoryFilter<?>> sessionRepositoryFilterRegistration(
            SessionRepositoryFilter<?> filter,
            @Autowired(required = false) SessionZoneResolutionFilter sessionZoneResolutionFilter) {
        FilterRegistrationBean<SessionRepositoryFilter<?>> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setEnabled(sessionZoneResolutionFilter == null);
        return registration;
    }
}
