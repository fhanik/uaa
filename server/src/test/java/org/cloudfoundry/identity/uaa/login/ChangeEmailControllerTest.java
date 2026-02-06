package org.cloudfoundry.identity.uaa.login;

import jakarta.annotation.PostConstruct;
import org.cloudfoundry.identity.uaa.account.ChangeEmailController;
import org.cloudfoundry.identity.uaa.account.ChangeEmailService;
import org.cloudfoundry.identity.uaa.authentication.UaaAuthentication;
import org.cloudfoundry.identity.uaa.authentication.UaaPrincipal;
import org.cloudfoundry.identity.uaa.constants.OriginKeys;
import org.cloudfoundry.identity.uaa.error.UaaException;
import org.cloudfoundry.identity.uaa.extensions.PollutionPreventionExtension;
import org.cloudfoundry.identity.uaa.home.BuildInfo;
import org.cloudfoundry.identity.uaa.user.UaaAuthority;
import org.cloudfoundry.identity.uaa.user.UaaUser;
import org.cloudfoundry.identity.uaa.user.UaaUserDatabase;
import org.cloudfoundry.identity.uaa.util.beans.TestBuildInfo;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.cloudfoundry.identity.uaa.zone.MultitenancyFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@ExtendWith(PollutionPreventionExtension.class)
@WebAppConfiguration
@SpringJUnitConfig(classes = ChangeEmailControllerTest.ContextConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ChangeEmailControllerTest {
    /** Whether the test uses the default path or the zone path prefix {@code /z/{subdomain}/}. */
    enum RequestPathMode {
        DEFAULT,
        ZONE_PATH
    }

    private static final String ZONE_PATH_SUBDOMAIN = "testsubdomain";

    private MockMvc mockMvc;
    @Autowired
    private ChangeEmailService changeEmailService;
    @Autowired
    private UaaUserDatabase uaaUserDatabase;
    @Autowired
    WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        IdentityZoneHolder.set(IdentityZone.getUaa());
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        IdentityZoneHolder.set(IdentityZone.getUaa());
    }

    private String pathPrefixFor(RequestPathMode mode) {
        if (mode == RequestPathMode.ZONE_PATH) {
            IdentityZone zone = MultitenancyFixture.identityZone("test-zone-id", ZONE_PATH_SUBDOMAIN);
            IdentityZoneHolder.set(zone);
            return "/z/" + ZONE_PATH_SUBDOMAIN;
        }
        return "";
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void changeEmailPage(RequestPathMode mode) throws Exception {
        setupSecurityContext();
        String pathPrefix = pathPrefixFor(mode);

        mockMvc.perform(get(pathPrefix + "/change_email").param("client_id", "client-id").param("redirect_uri", "http://example.com/redirect"))
                .andExpect(status().isOk())
                .andExpect(view().name("change_email"))
                .andExpect(model().attribute("email", "user@example.com"))
                .andExpect(model().attribute("client_id", "client-id"))
                .andExpect(model().attribute("redirect_uri", "http://example.com/redirect"))
                .andExpect(xpath("//*[@type='hidden' and @value='client-id']").exists())
                .andExpect(xpath("//*[@type='hidden' and @value='http://example.com/redirect']").exists());
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void changeEmail(RequestPathMode mode) throws Exception {
        setupSecurityContext();
        String pathPrefix = pathPrefixFor(mode);

        MockHttpServletRequestBuilder post = post(pathPrefix + "/change_email.do")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("newEmail", "new@example.com")
                .param("client_id", "app");

        mockMvc.perform(post)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("email_sent?code=email_change"));

        verify(changeEmailService).beginEmailChange("user-id-001", "bob", "new@example.com", "app", null);
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void changeEmailWithClientIdAndRedirectUri(RequestPathMode mode) throws Exception {
        setupSecurityContext();
        String pathPrefix = pathPrefixFor(mode);

        MockHttpServletRequestBuilder post = post(pathPrefix + "/change_email.do")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("newEmail", "new@example.com")
                .param("client_id", "app")
                .param("redirect_uri", "http://redirect.uri");

        mockMvc.perform(post)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("email_sent?code=email_change"));

        verify(changeEmailService).beginEmailChange("user-id-001", "bob", "new@example.com", "app", "http://redirect.uri");
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void changeEmailWithUsernameConflict(RequestPathMode mode) throws Exception {
        setupSecurityContext();
        String pathPrefix = pathPrefixFor(mode);

        doThrow(new UaaException("username already exists", 409)).when(changeEmailService).beginEmailChange("user-id-001", "bob", "new@example.com", "", null);

        MockHttpServletRequestBuilder post = post(pathPrefix + "/change_email.do")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("newEmail", "new@example.com")
                .param("client_id", "");

        mockMvc.perform(post)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("change_email"))
                .andExpect(model().attribute("error_message_code", "username_exists"))
                .andExpect(model().attribute("email", "user@example.com"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void nonUAAOriginUser(RequestPathMode mode) throws Exception {
        String pathPrefix = pathPrefixFor(mode);
        Authentication authentication = new UaaAuthentication(
                new UaaPrincipal("user-id-001", "bob", "user@example.com", "NON-UAA-origin ", null, IdentityZoneHolder.get().getId()),
                Collections.singletonList(UaaAuthority.UAA_USER),
                null
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequestBuilder post = post(pathPrefix + "/change_email.do")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("newEmail", "new@example.com")
                .param("client_id", "app");

        mockMvc.perform(post)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("profile?error_message_code=email_change.non-uaa-origin"));

        Mockito.verifyNoInteractions(changeEmailService);
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void invalidEmail(RequestPathMode mode) throws Exception {
        setupSecurityContext();
        String pathPrefix = pathPrefixFor(mode);

        MockHttpServletRequestBuilder post = post(pathPrefix + "/change_email.do")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("newEmail", "invalid")
                .param("client_id", "app");

        mockMvc.perform(post)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("change_email"))
                .andExpect(model().attribute("error_message_code", "invalid_email"))
                .andExpect(model().attribute("email", "user@example.com"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void verifyEmail(RequestPathMode mode) throws Exception {
        String pathPrefix = pathPrefixFor(mode);
        UaaUser user = new UaaUser("user-id-001", "new@example.com", "password", "new@example.com", Collections.<GrantedAuthority>emptyList(), "name", "name", null, null, OriginKeys.UAA, null, true, IdentityZoneHolder.get().getId(), "user-id-001", null);
        when(uaaUserDatabase.retrieveUserById(anyString())).thenReturn(user);

        Map<String, String> response = new HashMap<>();
        response.put("userId", "user-id-001");
        response.put("username", "new@example.com");
        response.put("email", "new@example.com");
        when(changeEmailService.completeVerification("the_secret_code")).thenReturn(response);

        MockHttpServletRequestBuilder get = get(pathPrefix + "/verify_email")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("code", "the_secret_code");

        mockMvc.perform(get)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("login?success=change_email_success"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void verifyEmailWhenAuthenticated(RequestPathMode mode) throws Exception {
        String pathPrefix = pathPrefixFor(mode);
        UaaUser user = new UaaUser("user-id-001", "new@example.com", "password", "new@example.com", Collections.<GrantedAuthority>emptyList(), "name", "name", null, null, OriginKeys.UAA, null, true, IdentityZoneHolder.get().getId(), "user-id-001", null);
        when(uaaUserDatabase.retrieveUserById(anyString())).thenReturn(user);

        Map<String, String> response = new HashMap<>();
        response.put("userId", "user-id-001");
        response.put("username", "new@example.com");
        response.put("email", "new@example.com");
        when(changeEmailService.completeVerification("the_secret_code")).thenReturn(response);

        setupSecurityContext();

        MockHttpServletRequestBuilder get = get(pathPrefix + "/verify_email")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("code", "the_secret_code");

        mockMvc.perform(get)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("profile?success_message_code=email_change.success"));

        UaaPrincipal principal = (UaaPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getId()).isEqualTo("user-id-001");
        assertThat(principal.getName()).isEqualTo("new@example.com");
        assertThat(principal.getEmail()).isEqualTo("new@example.com");
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void verifyEmailWithRedirectUrl(RequestPathMode mode) throws Exception {
        String pathPrefix = pathPrefixFor(mode);
        UaaUser user = new UaaUser("user-id-001", "new@example.com", "password", "new@example.com", Collections.<GrantedAuthority>emptyList(), "name", "name", null, null, OriginKeys.UAA, null, true, IdentityZoneHolder.get().getId(), "user-id-001", null);
        when(uaaUserDatabase.retrieveUserById(anyString())).thenReturn(user);

        Map<String, String> response = new HashMap<>();
        response.put("userId", "user-id-001");
        response.put("username", "new@example.com");
        response.put("email", "new@example.com");
        response.put("redirect_url", "//example.com/callback");
        when(changeEmailService.completeVerification("the_secret_code")).thenReturn(response);

        MockHttpServletRequestBuilder get = get(pathPrefix + "/verify_email")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("code", "the_secret_code");

        mockMvc.perform(get)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("login?success=change_email_success&form_redirect_uri=//example.com/callback"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void verifyEmailWithRedirectWhenAuthenticated(RequestPathMode mode) throws Exception {
        String pathPrefix = pathPrefixFor(mode);
        UaaUser user = new UaaUser("user-id-001", "new@example.com", "password", "new@example.com", Collections.<GrantedAuthority>emptyList(), "name", "name", null, null, OriginKeys.UAA, null, true, IdentityZoneHolder.get().getId(), "user-id-001", null);
        when(uaaUserDatabase.retrieveUserById(anyString())).thenReturn(user);

        Map<String, String> response = new HashMap<>();
        response.put("userId", "user-id-001");
        response.put("username", "new@example.com");
        response.put("email", "new@example.com");
        response.put("redirect_url", "//example.com/callback");
        when(changeEmailService.completeVerification("the_secret_code")).thenReturn(response);

        setupSecurityContext();

        MockHttpServletRequestBuilder get = get(pathPrefix + "/verify_email")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("code", "the_secret_code");

        mockMvc.perform(get)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("//example.com/callback"));

        UaaPrincipal principal = (UaaPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getId()).isEqualTo("user-id-001");
        assertThat(principal.getName()).isEqualTo("new@example.com");
        assertThat(principal.getEmail()).isEqualTo("new@example.com");

    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void verifyEmailWithInvalidCode(RequestPathMode mode) throws Exception {
        String pathPrefix = pathPrefixFor(mode);
        Authentication authentication = new AnonymousAuthenticationToken(
                "anon",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(changeEmailService.completeVerification("the_secret_code")).thenThrow(new UaaException("Bad Request", 400));
        MockHttpServletRequestBuilder get = get(pathPrefix + "/verify_email")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("code", "the_secret_code");

        mockMvc.perform(get)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("error"));

        setupSecurityContext();

        mockMvc.perform(get(pathPrefix + "/verify_email")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("code", "the_secret_code"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("profile?error_message_code=email_change.invalid_code"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void verifyEmailWhenAutheticatedAsOtherUser(RequestPathMode mode) throws Exception {
        String pathPrefix = pathPrefixFor(mode);
        UaaUser user = new UaaUser("user-id-002", "new2@example.com", "password", "new2@example.com", Collections.<GrantedAuthority>emptyList(), "name", "name", null, null, OriginKeys.UAA, null, true, IdentityZoneHolder.get().getId(), "user-id-002", null);
        when(uaaUserDatabase.retrieveUserById(anyString())).thenReturn(user);

        Map<String, String> response = new HashMap<>();
        response.put("userId", "user-id-002");
        response.put("username", "new2@example.com");
        response.put("email", "new2@example.com");
        when(changeEmailService.completeVerification("the_secret_code")).thenReturn(response);

        setupSecurityContext();

        MockHttpServletRequestBuilder get = get(pathPrefix + "/verify_email")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("code", "the_secret_code");

        mockMvc.perform(get)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("profile?success_message_code=email_change.success"));

        UaaPrincipal principal = (UaaPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getId()).isEqualTo("user-id-001");
        assertThat(principal.getName()).isEqualTo("bob");
        assertThat(principal.getEmail()).isEqualTo("user@example.com");
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void verifyEmailDoesNotDeleteAuthenticationMethods(RequestPathMode mode) throws Exception {
        String pathPrefix = pathPrefixFor(mode);
        UaaUser user = new UaaUser("user-id-001", "new@example.com", "password", "new@example.com", Collections.<GrantedAuthority>emptyList(), "name", "name", null, null, OriginKeys.UAA, null, true, IdentityZoneHolder.get().getId(), "user-id-001", null);
        when(uaaUserDatabase.retrieveUserById(anyString())).thenReturn(user);

        Map<String, String> response = new HashMap<>();
        response.put("userId", "user-id-001");
        response.put("username", "new@example.com");
        response.put("email", "new@example.com");
        when(changeEmailService.completeVerification("the_secret_code")).thenReturn(response);

        setupSecurityContext();
        UaaAuthentication authentication = (UaaAuthentication) SecurityContextHolder.getContext().getAuthentication();
        authentication.setAuthenticationMethods(Collections.singleton("pwd"));

        MockHttpServletRequestBuilder get = get(pathPrefix + "/verify_email")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("code", "the_secret_code");

        mockMvc.perform(get)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("profile?success_message_code=email_change.success"));

        UaaPrincipal principal = (UaaPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getId()).isEqualTo("user-id-001");
        assertThat(principal.getName()).isEqualTo("new@example.com");
        assertThat(principal.getEmail()).isEqualTo("new@example.com");

        authentication = (UaaAuthentication) SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthenticationMethods())
                .contains("pwd")
                .hasSize(1);
    }

    private void setupSecurityContext() {
        Authentication authentication = new UaaAuthentication(
                new UaaPrincipal("user-id-001", "bob", "user@example.com", OriginKeys.UAA, null, IdentityZoneHolder.get().getId()),
                Collections.singletonList(UaaAuthority.UAA_USER),
                null
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @EnableWebMvc
    @Import(ThymeleafConfig.class)
    static class ContextConfiguration implements WebMvcConfigurer {

        @Autowired
        private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

        @PostConstruct
        public void init() {
            requestMappingHandlerAdapter.setIgnoreDefaultModelOnRedirect(false);
        }

        @Override
        public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
            configurer.enable();
        }

        @Bean
        BuildInfo buildInfo() {
            return new TestBuildInfo();
        }

        @Bean
        public ResourceBundleMessageSource messageSource() {
            ResourceBundleMessageSource resourceBundleMessageSource = new ResourceBundleMessageSource();
            resourceBundleMessageSource.setBasename("messages");
            return resourceBundleMessageSource;
        }

        @Bean
        ChangeEmailService changeEmailService() {
            return mock(ChangeEmailService.class);
        }

        @Bean
        UaaUserDatabase uaaUserDatabase() {
            return mock(UaaUserDatabase.class);
        }

        @Bean
        ChangeEmailController changeEmailController(
                final ChangeEmailService changeEmailService,
                final UaaUserDatabase uaaUserDatabase) {
            return new ChangeEmailController(
                    changeEmailService,
                    uaaUserDatabase);
        }
    }
}
