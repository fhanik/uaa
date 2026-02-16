package org.cloudfoundry.identity.uaa.login;

import org.cloudfoundry.identity.uaa.TestClassNullifier;
import org.cloudfoundry.identity.uaa.extensions.PollutionPreventionExtension;
import org.cloudfoundry.identity.uaa.util.ZoneRequestPathMode;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

import static org.hamcrest.CoreMatchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * View tests for SessionController. Each test is parameterized by {@link ZoneRequestPathMode} so that
 * both default paths ({@code /session}, {@code /session_management}) and zone paths ({@code /z/{subdomain}/session}, ...) are covered.
 * Asserts that paths in the response content (e.g. script src) are correct for the mode.
 */
@ExtendWith(PollutionPreventionExtension.class)
@WebAppConfiguration
@SpringJUnitConfig(classes = SessionControllerViewZonePathTests.ContextConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SessionControllerViewZonePathTests extends TestClassNullifier {

    private static final String CLIENT_ID = "test-client";
    private static final String MESSAGE_ORIGIN = "https://origin.example.com";

    /** GET request for the given path; uses /z/{subdomain} when mode is ZONE_PATH so handler mapping matches. */
    private static MockHttpServletRequestBuilder request(ZoneRequestPathMode mode, String pathSuffix) {
        if (mode.redirectPrefix().isEmpty()) {
            return get(pathSuffix);
        }
        return get("/z/{subdomain}" + pathSuffix, mode.getSubdomain());
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void beforeEach() {
        SecurityContextHolder.clearContext();
        IdentityZoneHolder.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterEach
    void afterEach() {
        SecurityContextHolder.clearContext();
        IdentityZoneHolder.clear();
    }

    @ParameterizedTest
    @EnumSource(ZoneRequestPathMode.class)
    void sessionPageReturnsOkAndContainsExpectedPaths(ZoneRequestPathMode mode) throws Exception {
        mode.setZone();
        String scriptPath = "/resources/javascripts/session/session_message_handler.js";
        mockMvc.perform(request(mode, "/session")
                        .param("clientId", CLIENT_ID)
                        .param("messageOrigin", MESSAGE_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"" + scriptPath + "\"")))
                .andExpect(content().string(containsString(CLIENT_ID)))
                .andExpect(content().string(containsString(MESSAGE_ORIGIN)));
    }

    @ParameterizedTest
    @EnumSource(ZoneRequestPathMode.class)
    void sessionManagementPageReturnsOkAndContainsExpectedPaths(ZoneRequestPathMode mode) throws Exception {
        mode.setZone();
        String sjcl = "/resources/javascripts/session/sjcl.js";
        String sessionJs = "/resources/javascripts/session/session.js";
        String handlerJs = "/resources/javascripts/session/session_management_message_handler.js";
        mockMvc.perform(request(mode, "/session_management")
                        .param("clientId", CLIENT_ID)
                        .param("messageOrigin", MESSAGE_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"" + sjcl + "\"")))
                .andExpect(content().string(containsString("src=\"" + sessionJs + "\"")))
                .andExpect(content().string(containsString("src=\"" + handlerJs + "\"")))
                .andExpect(content().string(containsString(CLIENT_ID)))
                .andExpect(content().string(containsString(MESSAGE_ORIGIN)));
    }

    @EnableWebMvc
    @Import(ThymeleafConfig.class)
    static class ContextConfiguration implements WebMvcConfigurer {

        @Override
        public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
            configurer.enable();
        }

        @Bean
        SessionController sessionController() {
            return new SessionController();
        }
    }
}
