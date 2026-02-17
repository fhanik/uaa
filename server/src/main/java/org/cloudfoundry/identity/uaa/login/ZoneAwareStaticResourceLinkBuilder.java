/*
 * ****************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2017] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 * ****************************************************************************
 */
package org.cloudfoundry.identity.uaa.login;

import jakarta.servlet.http.HttpServletRequest;
import org.cloudfoundry.identity.uaa.zone.ZonePathContextRewritingFilter;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.web.servlet.IServletWebExchange;

import java.util.Map;

/**
 * Link builder that uses {@link ZonePathContextRewritingFilter#ZONE_ORIGINAL_CONTEXT_PATH}
 * for URLs starting with {@code /vendor/} or {@code /resources/}, so static assets are not
 * prefixed with the zone path when the request was rewritten by {@link ZonePathContextRewritingFilter}.
 * All other URLs use the default context path (e.g. {@link HttpServletRequest#getContextPath()}).
 */
public class ZoneAwareStaticResourceLinkBuilder extends StandardLinkBuilder {

    @Override
    protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) {
        if (!(context instanceof IWebContext)) {
            return super.computeContextPath(context, base, parameters);
        }
        var exchange = ((IWebContext) context).getExchange();
        if (!(exchange instanceof IServletWebExchange)) {
            return super.computeContextPath(context, base, parameters);
        }
        HttpServletRequest request = (HttpServletRequest) ((IServletWebExchange) exchange).getNativeRequestObject();
        if (base != null && (base.startsWith("/vendor/") || base.startsWith("/resources/"))) {
            String orig = (String) request.getAttribute(ZonePathContextRewritingFilter.ZONE_ORIGINAL_CONTEXT_PATH);
            return orig != null ? orig : request.getContextPath();
        }
        return super.computeContextPath(context, base, parameters);
    }
}
