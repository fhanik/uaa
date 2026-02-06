package org.cloudfoundry.identity.uaa.login;

import org.cloudfoundry.identity.uaa.TestClassNullifier;
import org.cloudfoundry.identity.uaa.account.ResetPasswordService;
import org.cloudfoundry.identity.uaa.authentication.UaaAuthentication;
import org.cloudfoundry.identity.uaa.authentication.UaaPrincipal;
import org.cloudfoundry.identity.uaa.extensions.PollutionPreventionExtension;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.cloudfoundry.identity.uaa.zone.beans.IdentityZoneManagerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(PollutionPreventionExtension.class)
@SpringJUnitConfig(classes = {ThymeleafAdditional.class, ThymeleafConfig.class})
class ForcePasswordChangeControllerTest extends TestClassNullifier {

    /** Whether the test uses the default path or the zone path prefix {@code /z/{subdomain}/}. */
    enum RequestPathMode {
        DEFAULT,
        ZONE_PATH
    }

    private static final String ZONE_PATH_SUBDOMAIN = "testsubdomain";

    private MockMvc mockMvc;
    private ResourcePropertySource mockResourcePropertySource;
    private UaaAuthentication mockUaaAuthentication;

    private static String pathPrefixFor(RequestPathMode mode) {
        return mode == RequestPathMode.ZONE_PATH ? "/z/" + ZONE_PATH_SUBDOMAIN : "";
    }

    @BeforeEach
    void beforeEach() {
        mockResourcePropertySource = mock(ResourcePropertySource.class);
        ForcePasswordChangeController controller = new ForcePasswordChangeController(
                mockResourcePropertySource,
                mock(ResetPasswordService.class),
                new IdentityZoneManagerImpl()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setViewResolvers(getResolver())
                .build();

        mockUaaAuthentication = mock(UaaAuthentication.class);
        UaaPrincipal mockUaaPrincipal = mock(UaaPrincipal.class);
        when(mockUaaAuthentication.getPrincipal()).thenReturn(mockUaaPrincipal);
        when(mockUaaPrincipal.getEmail()).thenReturn("mail");
        SecurityContextHolder.getContext().setAuthentication(mockUaaAuthentication);
    }

    @AfterEach
    void afterEach() {
        SecurityContextHolder.clearContext();
        IdentityZoneHolder.set(IdentityZone.getUaa());
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void forcePasswordChange(RequestPathMode mode) throws Exception {
        String prefix = pathPrefixFor(mode);
        mockMvc.perform(get(prefix + "/force_password_change"))
                .andExpect(status().isOk())
                .andExpect(view().name("force_password_change"))
                .andExpect(model().attribute("email", "mail"));
        mockMvc.perform(get(prefix + "/force_password_change/"))
                .andExpect(status().isOk())
                .andExpect(view().name("force_password_change"))
                .andExpect(model().attribute("email", "mail"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void redirectToLogInIfPasswordIsNotExpired(RequestPathMode mode) throws Exception {
        String prefix = pathPrefixFor(mode);
        mockMvc.perform(get(prefix + "/force_password_change"))
                .andExpect(status().isOk())
                .andExpect(view().name("force_password_change"));
        mockMvc.perform(get(prefix + "/force_password_change/"))
                .andExpect(status().isOk())
                .andExpect(view().name("force_password_change"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void handleForcePasswordChange(RequestPathMode mode) throws Exception {
        String prefix = pathPrefixFor(mode);
        if (mode == RequestPathMode.DEFAULT) {
            mockMvc.perform(
                            post("/uaa/force_password_change")
                                    .param("password", "pwd")
                                    .param("password_confirmation", "pwd")
                                    .contextPath("/uaa"))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/uaa/force_password_change_completed"));
        } else {
            mockMvc.perform(
                            post(prefix + "/force_password_change")
                                    .param("password", "pwd")
                                    .param("password_confirmation", "pwd"))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(prefix + "/force_password_change_completed"));
        }
        verify(mockUaaAuthentication, times(1)).setAuthenticatedTime(anyLong());
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void handleForcePasswordChangeWithRedirect(RequestPathMode mode) throws Exception {
        String prefix = pathPrefixFor(mode);
        mockMvc.perform(
                        post(prefix + "/force_password_change")
                                .param("password", "pwd")
                                .param("password_confirmation", "pwd"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(prefix + "/force_password_change_completed"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void passwordAndConfirmAreDifferent(RequestPathMode mode) throws Exception {
        String prefix = pathPrefixFor(mode);
        when(mockResourcePropertySource.getProperty("force_password_change.form_error")).thenReturn("Passwords must match and not be empty.");
        mockMvc.perform(
                        post(prefix + "/force_password_change")
                                .param("password", "pwd")
                                .param("password_confirmation", "nopwd"))
                .andExpect(status().isUnprocessableEntity());
    }
}
