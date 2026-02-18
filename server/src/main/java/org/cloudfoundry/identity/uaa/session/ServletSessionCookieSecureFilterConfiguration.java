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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * When using container-managed session (session-store=servlet), the JSESSIONID cookie Secure
 * attribute is driven by UAA's {@code require_https} setting. Configuration is applied via
 * {@link org.cloudfoundry.experimental.boot.UaaBootServerCustomizer} (TomcatContextCustomizer)
 * so the session cookie config is set before the context is initialised.
 */
@Configuration
@ConditionalOnProperty(name = "servlet.session-store", havingValue = "servlet")
public class ServletSessionCookieSecureFilterConfiguration {
}
