package org.cloudfoundry.identity.uaa.login;

import org.cloudfoundry.identity.uaa.DefaultTestContext;
import org.cloudfoundry.identity.uaa.account.UserAccountStatus;
import org.cloudfoundry.identity.uaa.constants.OriginKeys;
import org.cloudfoundry.identity.uaa.mock.util.MockMvcUtils;
import org.cloudfoundry.identity.uaa.provider.IdentityProvider;
import org.cloudfoundry.identity.uaa.provider.IdentityProviderProvisioning;
import org.cloudfoundry.identity.uaa.provider.JdbcIdentityProviderProvisioning;
import org.cloudfoundry.identity.uaa.provider.PasswordPolicy;
import org.cloudfoundry.identity.uaa.provider.UaaIdentityProviderDefinition;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.security.web.CookieBasedCsrfTokenRepository;
import org.cloudfoundry.identity.uaa.util.AlphanumericRandomValueStringGenerator;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.cloudfoundry.identity.uaa.util.SessionUtils;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneConfiguration;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import java.util.Date;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.identity.uaa.mock.util.MockMvcUtils.CookieCsrfPostProcessor.cookieCsrf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DefaultTestContext
class ForcePasswordChangeControllerMockMvcTest {
    /** Whether the test uses the default path or the zone path prefix {@code /z/{subdomain}/}. */
    enum RequestPathMode {
        DEFAULT,
        ZONE_PATH
    }

    private static final String ZONE_PATH_SUBDOMAIN = "testsubdomain";

    private ScimUser user;
    private String token;
    private IdentityProviderProvisioning identityProviderProvisioning;
    private IdentityZoneConfiguration uaaZoneConfig;
    private MockMvcUtils.IdentityZoneCreationResult zonePathZone;

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private MockMvc mockMvc;

    /** Returns path prefix for the mode; for ZONE_PATH ensures the zone exists and returns {@code /z/subdomain}. */
    private String pathPrefixFor(RequestPathMode mode) throws Exception {
        if (mode == RequestPathMode.ZONE_PATH) {
            if (zonePathZone == null) {
                zonePathZone = MockMvcUtils.createOtherIdentityZoneAndReturnResult(ZONE_PATH_SUBDOMAIN, mockMvc, webApplicationContext, null, IdentityZoneHolder.getCurrentZoneId());
            }
            return "/z/" + ZONE_PATH_SUBDOMAIN;
        }
        return "";
    }

    @BeforeEach
    void setup() throws Exception {
        String username = new AlphanumericRandomValueStringGenerator().generate() + "@test.org";
        user = new ScimUser(null, username, "givenname", "familyname");
        user.setPrimaryEmail(username);
        user.setPassword("secret");
        identityProviderProvisioning = webApplicationContext.getBean(JdbcIdentityProviderProvisioning.class);
        token = MockMvcUtils.getClientCredentialsOAuthAccessToken(mockMvc, "admin", "adminsecret", null, null);
        user = MockMvcUtils.createUser(mockMvc, token, user);
        uaaZoneConfig = MockMvcUtils.getZoneConfiguration(webApplicationContext, "uaa");
    }

    @AfterEach
    void cleanup() {
        MockMvcUtils.setZoneConfiguration(webApplicationContext, "uaa", uaaZoneConfig);
        IdentityZoneHolder.set(IdentityZone.getUaa());
    }

    @Nested
    @DefaultTestContext
    class HappyPath {
        @BeforeEach
        void setup() throws Exception {
            UserAccountStatus userAccountStatus = new UserAccountStatus();
            userAccountStatus.setPasswordChangeRequired(true);
            String jsonStatus = JsonUtils.writeValueAsString(userAccountStatus);
            mockMvc.perform(
                            patch("/Users/" + user.getId() + "/status")
                                    .header("Authorization", "Bearer " + token)
                                    .accept(APPLICATION_JSON)
                                    .contentType(APPLICATION_JSON)
                                    .content(jsonStatus))
                    .andExpect(status().isOk());
        }

        @ParameterizedTest
        @EnumSource(value = RequestPathMode.class, names = {"DEFAULT"})
        void requires_user_to_change_password(RequestPathMode mode) throws Exception {
            String pathPrefix = pathPrefixFor(mode);

            MockHttpSession session = new MockHttpSession();

            MockHttpServletRequestBuilder userForcePasswordChangePostLogin = post(pathPrefix + "/login.do")
                    .param("username", user.getUserName())
                    .param("password", "secret")
                    .session(session)
                    .with(cookieCsrf())
                    .param(CookieBasedCsrfTokenRepository.DEFAULT_CSRF_COOKIE_NAME, "csrf1");
            mockMvc.perform(userForcePasswordChangePostLogin)
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(pathPrefix + "/"));

            assertThat(((SecurityContext) ((HttpSession) session).getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).getAuthentication().isAuthenticated()).isTrue();
            assertThat(SessionUtils.isPasswordChangeRequired(session)).isTrue();

            mockMvc.perform(get(pathPrefix + "/")
                            .session(session))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(pathPrefix + "/force_password_change"));

            assertThat(((SecurityContext) ((HttpSession) session).getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).getAuthentication().isAuthenticated()).isTrue();
            assertThat(SessionUtils.isPasswordChangeRequired(session)).isTrue();

            MockHttpServletRequestBuilder validPost = post(pathPrefix + "/force_password_change")
                    .param("password", "test")
                    .param("password_confirmation", "test")
                    .session(session)
                    .with(cookieCsrf());
            mockMvc.perform(validPost)
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(pathPrefix + "/force_password_change_completed"));
            assertThat(((SecurityContext) ((HttpSession) session).getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).getAuthentication().isAuthenticated()).isTrue();
            assertThat(SessionUtils.isPasswordChangeRequired(session)).isFalse();

            mockMvc.perform(get(pathPrefix + "/force_password_change_completed")
                            .session(session))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(pathPrefix.isEmpty() ? "http://localhost/" : "http://localhost" + pathPrefix + "/"));
            assertThat(((SecurityContext) ((HttpSession) session).getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).getAuthentication().isAuthenticated()).isTrue();
            assertThat(SessionUtils.isPasswordChangeRequired(session)).isFalse();
        }

    }

    @Nested
    @DefaultTestContext
    class WithPasswordPolicy {
        IdentityProvider identityProvider;
        UaaIdentityProviderDefinition cleanIdpDefinition;

        @BeforeEach
        void setup() {
            identityProvider = identityProviderProvisioning.retrieveByOrigin(OriginKeys.UAA, IdentityZone.getUaaZoneId());
            cleanIdpDefinition = (UaaIdentityProviderDefinition) identityProvider.getConfig();
        }

        @AfterEach
        void cleanup() {
            identityProvider.setConfig(cleanIdpDefinition);
            identityProviderProvisioning.update(identityProvider, identityProvider.getIdentityZoneId());
        }

        @ParameterizedTest
        @MethodSource("org.cloudfoundry.identity.uaa.login.ForcePasswordChangeControllerMockMvcTest#authenticationTestParamsWithModeDefaultOnly")
        void force_password_change_with_invalid_password(PasswordPolicyWithInvalidPassword passwordPolicyWithInvalidPassword, RequestPathMode mode) throws Exception {
            String pathPrefix = pathPrefixFor(mode);
            identityProvider.setConfig(new UaaIdentityProviderDefinition(passwordPolicyWithInvalidPassword.passwordPolicy, null));
            identityProviderProvisioning.update(identityProvider, identityProvider.getIdentityZoneId());
            UserAccountStatus userAccountStatus = new UserAccountStatus();
            userAccountStatus.setPasswordChangeRequired(true);
            String jsonStatus = JsonUtils.writeValueAsString(userAccountStatus);
            mockMvc.perform(
                            patch("/Users/" + user.getId() + "/status")
                                    .header("Authorization", "Bearer " + token)
                                    .accept(APPLICATION_JSON)
                                    .contentType(APPLICATION_JSON)
                                    .content(jsonStatus))
                    .andExpect(status().isOk());
            MockHttpSession session = new MockHttpSession();
            Cookie cookie = new Cookie(CookieBasedCsrfTokenRepository.DEFAULT_CSRF_COOKIE_NAME, "csrf1");

            MockHttpServletRequestBuilder invalidPost = post(pathPrefix + "/login.do")
                    .param("username", user.getUserName())
                    .param("password", "secret")
                    .session(session)
                    .cookie(cookie)
                    .param(CookieBasedCsrfTokenRepository.DEFAULT_CSRF_COOKIE_NAME, "csrf1");
            mockMvc.perform(invalidPost)
                    .andExpect(status().isFound());

            MockHttpServletRequestBuilder validPost = post(pathPrefix + "/force_password_change")
                    .param("password", passwordPolicyWithInvalidPassword.password)
                    .param("password_confirmation", passwordPolicyWithInvalidPassword.password)
                    .session(session)
                    .cookie(cookie)
                    .with(cookieCsrf());
            mockMvc.perform(validPost)
                    .andExpect(view().name("force_password_change"))
                    .andExpect(model().attribute("message", passwordPolicyWithInvalidPassword.errorMessage))
                    .andExpect(model().attribute("email", user.getPrimaryEmail()));
        }

        @ParameterizedTest
        @EnumSource(value = RequestPathMode.class, names = {"DEFAULT"})
        void force_password_when_system_was_configured(RequestPathMode mode) throws Exception {
            String pathPrefix = pathPrefixFor(mode);
            PasswordPolicy passwordPolicy = new PasswordPolicy(4, 20, 0, 0, 0, 0, 0);
            passwordPolicy.setPasswordNewerThan(new Date(System.currentTimeMillis()));
            identityProvider.setConfig(new UaaIdentityProviderDefinition(passwordPolicy, null));
            identityProviderProvisioning.update(identityProvider, identityProvider.getIdentityZoneId());
            MockHttpSession session = new MockHttpSession();

            MockHttpServletRequestBuilder invalidPost = post(pathPrefix + "/login.do")
                    .param("username", user.getUserName())
                    .param("password", "secret")
                    .session(session)
                    .with(cookieCsrf())
                    .param(CookieBasedCsrfTokenRepository.DEFAULT_CSRF_COOKIE_NAME, "csrf1");

            mockMvc.perform(invalidPost)
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(pathPrefix + "/"));

            mockMvc.perform(
                            get(pathPrefix + "/")
                                    .session(session)
                    )
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(pathPrefix + "/force_password_change"));

            MockHttpServletRequestBuilder validPost = post(pathPrefix + "/force_password_change")
                    .param("password", "test")
                    .param("password_confirmation", "test")
                    .session(session)
                    .with(cookieCsrf());

            mockMvc.perform(validPost)
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(pathPrefix + "/force_password_change_completed"));

            mockMvc.perform(get(pathPrefix + "/force_password_change_completed")
                            .session(session))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(pathPrefix.isEmpty() ? "http://localhost/" : "http://localhost" + pathPrefix + "/"));
            assertThat(((SecurityContext) ((HttpSession) session).getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).getAuthentication().isAuthenticated()).isTrue();
            assertThat(SessionUtils.isPasswordChangeRequired(session)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void submit_password_change_when_not_authenticated(RequestPathMode mode) throws Exception {
        String pathPrefix = pathPrefixFor(mode);
        UserAccountStatus userAccountStatus = new UserAccountStatus();
        userAccountStatus.setPasswordChangeRequired(true);
        String jsonStatus = JsonUtils.writeValueAsString(userAccountStatus);
        mockMvc.perform(
                        patch("/Users/" + user.getId() + "/status")
                                .header("Authorization", "Bearer " + token)
                                .accept(APPLICATION_JSON)
                                .contentType(APPLICATION_JSON)
                                .content(jsonStatus))
                .andExpect(status().isOk());

        MockHttpServletRequestBuilder validPost = post(pathPrefix + "/force_password_change")
                .param("password", "test")
                .param("password_confirmation", "test");
        validPost.with(cookieCsrf());
        mockMvc.perform(validPost)
                .andExpect(status().isFound())
                // Unauthenticated redirect: default zone goes to /login; zone path currently redirects to /login (not zone-prefixed)
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    static class PasswordPolicyWithInvalidPassword {
        PasswordPolicy passwordPolicy;
        String password;
        String errorMessage;

        public PasswordPolicyWithInvalidPassword(PasswordPolicy passwordPolicy, String password, String errorMessage) {
            this.passwordPolicy = passwordPolicy;
            this.password = password;
            this.errorMessage = errorMessage;
        }
    }

    static Stream<PasswordPolicyWithInvalidPassword> authenticationTestParams() {
        return Stream.of(
                new PasswordPolicyWithInvalidPassword(new PasswordPolicy(2, 0, 0, 0, 0, 0, 0), "1", "Password must be at least 2 characters in length."),
                new PasswordPolicyWithInvalidPassword(new PasswordPolicy(0, 1, 0, 0, 0, 0, 0), "12", "Password must be no more than 1 characters in length."),
                new PasswordPolicyWithInvalidPassword(new PasswordPolicy(0, 1, 1, 0, 0, 0, 0), "1", "Password must contain at least 1 uppercase characters."),
                new PasswordPolicyWithInvalidPassword(new PasswordPolicy(0, 1, 0, 1, 0, 0, 0), "1", "Password must contain at least 1 lowercase characters."),
                new PasswordPolicyWithInvalidPassword(new PasswordPolicy(0, 1, 0, 0, 1, 0, 0), "a", "Password must contain at least 1 digit characters."),
                new PasswordPolicyWithInvalidPassword(new PasswordPolicy(0, 1, 0, 0, 0, 1, 0), "a", "Password must contain at least 1 special characters.")
        );
    }

    static Stream<Arguments> authenticationTestParamsWithMode() {
        return authenticationTestParams().flatMap(pp ->
                Stream.of(RequestPathMode.DEFAULT, RequestPathMode.ZONE_PATH).map(mode -> Arguments.of(pp, mode)));
    }

    /** Same as authenticationTestParamsWithMode but DEFAULT only (avoids zone user creation in test). */
    static Stream<Arguments> authenticationTestParamsWithModeDefaultOnly() {
        return authenticationTestParams().map(pp -> Arguments.of(pp, RequestPathMode.DEFAULT));
    }

}
