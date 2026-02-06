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
package org.cloudfoundry.identity.uaa.login;

import org.cloudfoundry.identity.uaa.authentication.AccountNotPreCreatedException;
import org.cloudfoundry.identity.uaa.authentication.AccountNotVerifiedException;
import org.cloudfoundry.identity.uaa.authentication.AuthenticationPolicyRejectionException;
import org.cloudfoundry.identity.uaa.authentication.PasswordChangeRequiredException;
import org.cloudfoundry.identity.uaa.util.SessionUtils;
import org.cloudfoundry.identity.uaa.util.UaaUrlUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Component
public class UaaAuthenticationFailureHandler implements AuthenticationFailureHandler, LogoutHandler {
    private final ExceptionMappingAuthenticationFailureHandler delegate;
    private final CurrentUserCookieFactory currentUserCookieFactory;

    @Autowired
    public UaaAuthenticationFailureHandler(CurrentUserCookieFactory currentUserCookieFactory) {
        this(defaultDelegateFailureHandler(), currentUserCookieFactory);
    }

    private static ExceptionMappingAuthenticationFailureHandler defaultDelegateFailureHandler() {
        var handler = new ExceptionMappingAuthenticationFailureHandler();
        handler.setExceptionMappings(
                Map.of(
                        AccountNotVerifiedException.class.getName(), "/login?error=account_not_verified",
                        AuthenticationPolicyRejectionException.class.getName(), "/login?error=account_locked",
                        AccountNotPreCreatedException.class.getName(), "/login?error=account_not_precreated",
                        PasswordChangeRequiredException.class.getName(), "/force_password_change"
                )
        );
        handler.setDefaultFailureUrl("/login?error=login_failure");
        handler.setRedirectStrategy(new ZoneAwareRedirectStrategy());
        return handler;
    }

    /**
     * Prepend zone path prefix (e.g. /z/{subdomain}) to redirect URLs when the request is under /z/{subdomain}/....
     * Otherwise delegates to DefaultRedirectStrategy unchanged.
     */
    private static final class ZoneAwareRedirectStrategy implements RedirectStrategy {
        private final RedirectStrategy defaultStrategy = new DefaultRedirectStrategy();

        @Override
        public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
            String zonePrefix = UaaUrlUtils.getZonePathPrefix(request);
            String targetUrl = url;
            if (!zonePrefix.isEmpty() && url.startsWith("/") && !url.startsWith(zonePrefix)) {
                targetUrl = zonePrefix + url;
            }
            defaultStrategy.sendRedirect(request, response, targetUrl);
        }
    }

    public UaaAuthenticationFailureHandler(ExceptionMappingAuthenticationFailureHandler delegate, CurrentUserCookieFactory currentUserCookieFactory) {
        this.delegate = delegate;
        this.currentUserCookieFactory = currentUserCookieFactory;
        if (delegate != null) {
            delegate.setRedirectStrategy(new ZoneAwareRedirectStrategy());
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        addCookie(request, response);
        if (exception instanceof PasswordChangeRequiredException passwordChangeRequiredException) {
            SessionUtils.setForcePasswordExpiredUser(request.getSession(),
                    passwordChangeRequiredException.getAuthentication());
        }

        if (delegate != null) {
            delegate.onAuthenticationFailure(request, response, exception);
        }
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        addCookie(request, response);
    }

    private void addCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie clearCurrentUserCookie = currentUserCookieFactory.getNullCookie();
        String zonePrefix = UaaUrlUtils.getZonePathPrefix(request);
        if (!zonePrefix.isEmpty()) {
            clearCurrentUserCookie.setPath(zonePrefix);
        }
        response.addCookie(clearCurrentUserCookie);
    }
}
