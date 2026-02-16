package org.cloudfoundry.identity.uaa.web;

import org.cloudfoundry.identity.uaa.util.UaaUrlUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds zone-aware nav URLs to the model only when rendering a view (not on redirects),
 * so the nav fragment and other templates get correct profile/logout links for zone paths.
 */
public class NavModelInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (modelAndView == null || modelAndView.getViewName() == null) {
            return;
        }
        if (modelAndView.getViewName().startsWith("redirect:")) {
            return;
        }
        String prefix = UaaUrlUtils.getZonePathPrefix(request);
        modelAndView.addObject("pathPrefix", prefix);
        modelAndView.addObject("profileUrl", StringUtils.hasText(prefix) ? prefix + "/profile" : "/profile");
        modelAndView.addObject("logoutUrl", StringUtils.hasText(prefix) ? prefix + "/logout.do" : "/logout.do");
    }
}
