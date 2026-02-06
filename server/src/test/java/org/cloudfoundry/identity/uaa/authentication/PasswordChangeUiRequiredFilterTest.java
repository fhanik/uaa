package org.cloudfoundry.identity.uaa.authentication;

import org.cloudfoundry.identity.uaa.extensions.PollutionPreventionExtension;
import org.cloudfoundry.identity.uaa.util.SessionUtils;
import org.cloudfoundry.identity.uaa.web.UaaSavedRequestCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.SavedRequest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(PollutionPreventionExtension.class)
@ExtendWith(MockitoExtension.class)
class PasswordChangeUiRequiredFilterTest {

    /** Whether the test uses the default path or the zone path prefix {@code /z/{subdomain}/}. */
    enum RequestPathMode {
        DEFAULT,
        ZONE_PATH
    }

    private static final String ZONE_PATH_SUBDOMAIN = "testsubdomain";

    private MockHttpServletRequest mockHttpServletRequest;

    @Mock
    private UaaSavedRequestCache mockRequestCache;

    @Mock
    private UaaAuthentication mockUaaAuthentication;

    @Mock
    private HttpServletResponse mockHttpServletResponse;

    @Mock
    private FilterChain mockFilterChain;

    @InjectMocks
    private PasswordChangeUiRequiredFilter passwordChangeUiRequiredFilter;

    @BeforeEach
    void setUp() {
        mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletRequest.setContextPath("");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static String pathFor(RequestPathMode mode, String path) {
        return mode == RequestPathMode.ZONE_PATH ? "/z/" + ZONE_PATH_SUBDOMAIN + path : path;
    }

    private void setRequestPath(RequestPathMode mode, String path) {
        String fullPath = pathFor(mode, path);
        mockHttpServletRequest.setRequestURI(fullPath);
        mockHttpServletRequest.setServletPath(fullPath);
        mockHttpServletRequest.setPathInfo(null);
    }

    @Test
    void notAuthenticated() throws Exception {
        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);
        verify(mockFilterChain, times(1)).doFilter(same(mockHttpServletRequest), same(mockHttpServletResponse));
    }

    @Test
    void authenticated() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(mockUaaAuthentication);
        when(mockUaaAuthentication.isAuthenticated()).thenReturn(true);
        setRequiresPasswordChange(mockHttpServletRequest, false);
        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);
        verify(mockFilterChain, times(1)).doFilter(same(mockHttpServletRequest), same(mockHttpServletResponse));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void authenticatedPasswordExpired(RequestPathMode mode) throws Exception {
        setRequestPath(mode, "/oauth/authorize");
        SecurityContextHolder.getContext().setAuthentication(mockUaaAuthentication);
        when(mockUaaAuthentication.isAuthenticated()).thenReturn(true);
        setRequiresPasswordChange(mockHttpServletRequest, true);
        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);
        verify(mockFilterChain, never()).doFilter(any(), any());
        verify(mockHttpServletResponse, times(1)).sendRedirect(pathFor(mode, "/force_password_change"));
        verify(mockRequestCache, times(1)).saveRequest(any(), any());
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void loadingChangePasswordPage(RequestPathMode mode) throws Exception {
        setRequestPath(mode, "/force_password_change");
        mockHttpServletRequest.setMethod(HttpMethod.GET.name());
        SecurityContextHolder.getContext().setAuthentication(mockUaaAuthentication);
        when(mockUaaAuthentication.isAuthenticated()).thenReturn(true);
        setRequiresPasswordChange(mockHttpServletRequest, true);
        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);
        verify(mockFilterChain, times(1)).doFilter(same(mockHttpServletRequest), same(mockHttpServletResponse));
        verify(mockHttpServletResponse, never()).sendRedirect(anyString());
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void submitChangePassword(RequestPathMode mode) throws Exception {
        setRequestPath(mode, "/force_password_change");
        mockHttpServletRequest.setMethod(HttpMethod.POST.name());
        SecurityContextHolder.getContext().setAuthentication(mockUaaAuthentication);
        when(mockUaaAuthentication.isAuthenticated()).thenReturn(true);
        setRequiresPasswordChange(mockHttpServletRequest, true);
        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);
        verify(mockFilterChain, times(1)).doFilter(same(mockHttpServletRequest), same(mockHttpServletResponse));
        verify(mockHttpServletResponse, never()).sendRedirect(anyString());
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void followCompletedRedirect(RequestPathMode mode) throws Exception {
        setRequestPath(mode, "/force_password_change_completed");
        mockHttpServletRequest.setMethod(HttpMethod.POST.name());
        SecurityContextHolder.getContext().setAuthentication(mockUaaAuthentication);
        when(mockUaaAuthentication.isAuthenticated()).thenReturn(true);
        setRequiresPasswordChange(mockHttpServletRequest, false);
        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);
        verify(mockFilterChain, never()).doFilter(any(), any());
        verify(mockHttpServletResponse, times(1)).sendRedirect(pathFor(mode, "/"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void followCompletedRedirectWithSavedRequest(RequestPathMode mode) throws Exception {
        String location = "/oauth/authorize";
        SavedRequest savedRequest = getSavedRequest(location);
        when(mockRequestCache.getRequest(any(), any())).thenReturn(savedRequest);
        setRequestPath(mode, "/force_password_change_completed");
        mockHttpServletRequest.setMethod(HttpMethod.POST.name());
        SecurityContextHolder.getContext().setAuthentication(mockUaaAuthentication);
        when(mockUaaAuthentication.isAuthenticated()).thenReturn(true);
        setRequiresPasswordChange(mockHttpServletRequest, false);
        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);
        verify(mockFilterChain, never()).doFilter(any(), any());
        verify(mockHttpServletResponse, times(1)).sendRedirect(location);
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void tryingAccessForcePasswordPage(RequestPathMode mode) throws Exception {
        setRequestPath(mode, "/force_password_change");
        SecurityContextHolder.getContext().setAuthentication(mockUaaAuthentication);
        when(mockUaaAuthentication.isAuthenticated()).thenReturn(true);
        setRequiresPasswordChange(mockHttpServletRequest, false);
        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);
        verify(mockFilterChain, never()).doFilter(any(), any());
        verify(mockHttpServletResponse, times(1)).sendRedirect(pathFor(mode, "/"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void tryingAccessForcePasswordPageNotAuthenticated(RequestPathMode mode) throws Exception {
        setRequestPath(mode, "/force_password_change");
        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);
        verify(mockFilterChain, times(1)).doFilter(same(mockHttpServletRequest), same(mockHttpServletResponse));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void completedButStillRequiresChange(RequestPathMode mode) throws Exception {
        setRequestPath(mode, "/force_password_change_completed");
        mockHttpServletRequest.setMethod(HttpMethod.POST.name());
        SecurityContextHolder.getContext().setAuthentication(mockUaaAuthentication);
        when(mockUaaAuthentication.isAuthenticated()).thenReturn(true);
        setRequiresPasswordChange(mockHttpServletRequest, true);

        passwordChangeUiRequiredFilter.doFilterInternal(mockHttpServletRequest, mockHttpServletResponse, mockFilterChain);

        verify(mockFilterChain, never()).doFilter(any(), any());
        verify(mockHttpServletResponse, times(1)).sendRedirect(pathFor(mode, "/force_password_change"));
    }

    private SavedRequest getSavedRequest(final String redirectUrl) {
        return new SavedRequest() {
            @Override
            public String getRedirectUrl() {
                return redirectUrl;
            }

            @Override
            public List<Cookie> getCookies() {
                return null;
            }

            @Override
            public String getMethod() {
                return null;
            }

            @Override
            public List<String> getHeaderValues(String name) {
                return null;
            }

            @Override
            public Collection<String> getHeaderNames() {
                return null;
            }

            @Override
            public List<Locale> getLocales() {
                return null;
            }

            @Override
            public String[] getParameterValues(String name) {
                return new String[0];
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                return null;
            }
        };
    }

    private void setRequiresPasswordChange(MockHttpServletRequest request, boolean requiresPasswordChange) {
        MockHttpSession httpSession = new MockHttpSession();
        SessionUtils.setPasswordChangeRequired(httpSession, requiresPasswordChange);
        request.setSession(httpSession);
    }
}