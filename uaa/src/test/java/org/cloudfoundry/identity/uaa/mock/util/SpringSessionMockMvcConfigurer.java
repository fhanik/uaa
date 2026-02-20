package org.cloudfoundry.identity.uaa.mock.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcConfigurer;
import org.springframework.web.context.WebApplicationContext;

import java.util.Base64;
import java.util.Collections;

/**
 * A {@link MockMvcConfigurer} that bridges pre-populated {@link MockHttpSession} attributes
 * into the Spring Session {@link SessionRepository}, and copies Spring Session attributes
 * back to the {@link MockHttpSession} after each request.
 *
 * <h3>Problem</h3>
 * <p>When {@code SessionRepositoryFilter} is in the MockMvc filter chain, it replaces
 * {@code getSession()} with its own session from the {@link SessionRepository}. This means:
 * <ul>
 *   <li>Attributes set on a {@link MockHttpSession} before a request are invisible to the
 *       filter chain.</li>
 *   <li>{@code mvcResult.getRequest().getSession()} returns the unwrapped
 *       {@link MockHttpSession}, not the Spring Session, so post-request reads see stale data.</li>
 * </ul>
 *
 * <h3>Solution</h3>
 * <p>This configurer hooks into MockMvc's lifecycle at two points:
 * <ol>
 *   <li><b>Before each request</b> ({@link RequestPostProcessor}): if the request has a
 *       {@link MockHttpSession} with attributes, creates a Spring Session in the repository
 *       with the same attributes, and adds a base64-encoded {@code JSESSIONID} cookie so
 *       {@code SessionRepositoryFilter} finds it.</li>
 *   <li><b>After each request</b> ({@code alwaysDo} result handler): looks up the Spring
 *       Session from the repository (using the response's {@code JSESSIONID} cookie or the
 *       request cookie) and copies its attributes back onto the {@code MockHttpSession}
 *       so that post-request assertions see the correct data.</li>
 * </ol>
 */
public class SpringSessionMockMvcConfigurer implements MockMvcConfigurer {

    @Override
    public void afterConfigurerAdded(ConfigurableMockMvcBuilder<?> builder) {
        builder.alwaysDo(result -> {
            MockHttpServletRequest mockRequest = result.getRequest();
            @SuppressWarnings("unchecked")
            SessionRepository<Session> repository =
                    (SessionRepository<Session>) mockRequest.getAttribute(SessionRepository.class.getName());
            if (repository == null) {
                return;
            }

            String sessionId = resolveSessionId(result.getResponse().getCookie("JSESSIONID"), mockRequest);
            if (sessionId == null) {
                return;
            }

            Session springSession = repository.findById(sessionId);
            if (springSession == null) {
                return;
            }

            HttpSession existingSession = mockRequest.getSession(false);
            MockHttpSession target;
            if (existingSession instanceof MockHttpSession mock) {
                target = mock;
            } else {
                target = new MockHttpSession(mockRequest.getServletContext(), sessionId);
                mockRequest.setSession(target);
            }

            clearAttributes(target);
            for (String name : springSession.getAttributeNames()) {
                target.setAttribute(name, springSession.getAttribute(name));
            }
        });
    }

    @Override
    public RequestPostProcessor beforeMockMvcCreated(ConfigurableMockMvcBuilder<?> builder,
                                                     WebApplicationContext context) {
        return request -> {
            HttpSession session = request.getSession(false);
            if (!(session instanceof MockHttpSession mockSession)) {
                return request;
            }

            boolean hasAttributes = mockSession.getAttributeNames().hasMoreElements();
            boolean hasSessionCookie = hasJSessionIdCookie(request.getCookies());

            // If the request already carries a JSESSIONID cookie (e.g., from a
            // previous login) and the MockHttpSession has no attributes to seed,
            // let SessionRepositoryFilter resolve the existing session from the
            // repository — don't create a new one.
            if (!hasAttributes && hasSessionCookie) {
                return request;
            }

            @SuppressWarnings("unchecked")
            SessionRepository<Session> repository = context.getBean(SessionRepository.class);
            Session springSession = repository.createSession();

            for (String name : Collections.list(mockSession.getAttributeNames())) {
                springSession.setAttribute(name, mockSession.getAttribute(name));
            }
            repository.save(springSession);

            String encodedId = Base64.getEncoder().encodeToString(springSession.getId().getBytes());
            request.setCookies(appendCookie(request.getCookies(), new Cookie("JSESSIONID", encodedId)));

            return request;
        };
    }

    private static boolean hasJSessionIdCookie(Cookie[] cookies) {
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if ("JSESSIONID".equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String resolveSessionId(Cookie responseCookie, MockHttpServletRequest request) {
        String encoded = null;
        if (responseCookie != null && responseCookie.getValue() != null && !responseCookie.getValue().isEmpty()) {
            encoded = responseCookie.getValue();
        }
        if (encoded == null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("JSESSIONID".equals(c.getName())) {
                    encoded = c.getValue();
                    break;
                }
            }
        }
        if (encoded == null) {
            return null;
        }
        return decodeBase64(encoded);
    }

    private static String decodeBase64(String value) {
        try {
            return new String(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static void clearAttributes(MockHttpSession session) {
        for (String name : Collections.list(session.getAttributeNames())) {
            session.removeAttribute(name);
        }
    }

    private static Cookie[] appendCookie(Cookie[] existing, Cookie cookie) {
        if (existing == null || existing.length == 0) {
            return new Cookie[]{cookie};
        }
        for (int i = 0; i < existing.length; i++) {
            if (existing[i].getName().equals(cookie.getName())) {
                existing[i] = cookie;
                return existing;
            }
        }
        Cookie[] result = new Cookie[existing.length + 1];
        System.arraycopy(existing, 0, result, 0, existing.length);
        result[existing.length] = cookie;
        return result;
    }

    public static SpringSessionMockMvcConfigurer springSessionBridge() {
        return new SpringSessionMockMvcConfigurer();
    }
}
