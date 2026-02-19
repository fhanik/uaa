package org.cloudfoundry.identity.uaa.zone;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Runs exactly after {@link ZonePathContextRewritingFilter}. Wraps the request so that
 * {@link HttpServletRequest#getSession()} returns a context-path-scoped sub-session
 * ({@link ZonePathHttpSession}) stored inside the container session. Wraps the response
 * to block clearing the JSESSIONID cookie; when the filter completes, if no sub-sessions
 * remain, it may clear the JSESSIONID cookie (if the response is not committed).
 */
public class ZoneContextPathSessionFilter extends OncePerRequestFilter {

    public static final String BEAN_NAME = "zoneContextPathSessionFilter";

    private static final String JSESSIONID = "JSESSIONID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ZoneContextPathSessionRequestWrapper wrappedRequest = new ZoneContextPathSessionRequestWrapper(request);
        ZoneContextPathSessionResponseWrapper wrappedResponse = new ZoneContextPathSessionResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            maybeClearJSessionIdIfNoSubSessions(request, wrappedResponse);
        }
    }

    private void maybeClearJSessionIdIfNoSubSessions(HttpServletRequest request,
                                                     ZoneContextPathSessionResponseWrapper response) {
        if (response.isCommitted()) {
            return;
        }
        HttpSession containerSession = request.getSession(false);
        if (containerSession == null) {
            return;
        }
        String prefix = ZoneContextPathSessionRequestWrapper.ATTRIBUTE_NAME_PREFIX;
        Enumeration<String> names = containerSession.getAttributeNames();
        if (names == null) {
            names = Collections.emptyEnumeration();
        }
        while (names.hasMoreElements()) {
            if (names.nextElement().startsWith(prefix)) {
                return; // at least one context-path sub-session still present
            }
        }
        String path = request.getContextPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        HttpServletResponse rawResponse = (HttpServletResponse) response.getResponse();
        rawResponse.addHeader("Set-Cookie", JSESSIONID + "=; Max-Age=0; Path=" + path);
    }
}
