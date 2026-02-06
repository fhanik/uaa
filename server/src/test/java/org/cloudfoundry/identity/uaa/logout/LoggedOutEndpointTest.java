package org.cloudfoundry.identity.uaa.logout;

import org.cloudfoundry.identity.uaa.TestClassNullifier;
import org.cloudfoundry.identity.uaa.extensions.PollutionPreventionExtension;
import org.cloudfoundry.identity.uaa.home.BuildInfo;
import org.cloudfoundry.identity.uaa.login.ThymeleafConfig;
import org.cloudfoundry.identity.uaa.util.beans.TestBuildInfo;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.cloudfoundry.identity.uaa.zone.MultitenancyFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(PollutionPreventionExtension.class)
@WebAppConfiguration
@SpringJUnitConfig(classes = LoggedOutEndpointTest.ContextConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LoggedOutEndpointTest extends TestClassNullifier {

    /** Whether the test uses the default path or the zone path prefix {@code /z/{subdomain}/}. */
    enum RequestPathMode {
        DEFAULT,
        ZONE_PATH
    }

    /** Whether the request uses no context path or context path {@code /uaa}. */
    enum ContextPathMode {
        NONE,
        UAA
    }

    private static final String ZONE_PATH_SUBDOMAIN = "testsubdomain";
    private static final String UAA_CONTEXT_PATH = "/uaa";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private static String pathPrefixFor(RequestPathMode mode) {
        return mode == RequestPathMode.ZONE_PATH ? "/z/" + ZONE_PATH_SUBDOMAIN : "";
    }

    @BeforeEach
    void setUp() {
        IdentityZoneHolder.set(IdentityZone.getUaa());
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterEach
    void tearDown() {
        IdentityZoneHolder.set(IdentityZone.getUaa());
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void loggedOutPage(RequestPathMode mode) throws Exception {
        if (mode == RequestPathMode.ZONE_PATH) {
            IdentityZoneHolder.set(MultitenancyFixture.identityZone("test-zone-id", ZONE_PATH_SUBDOMAIN));
        }
        String pathPrefix = pathPrefixFor(mode);
        mockMvc.perform(get(pathPrefix + "/logged_out"))
                .andExpect(status().isOk())
                .andExpect(view().name("logged_out"));
    }

    @ParameterizedTest
    @EnumSource(RequestPathMode.class)
    void loggedOutPageLoginLink(RequestPathMode mode) throws Exception {
        if (mode == RequestPathMode.ZONE_PATH) {
            IdentityZoneHolder.set(MultitenancyFixture.identityZone("test-zone-id", ZONE_PATH_SUBDOMAIN));
        }
        String pathPrefix = pathPrefixFor(mode);
        String expectedLoginHref = pathPrefix.isEmpty() ? "href=\"/login\"" : "href=\"/z/" + ZONE_PATH_SUBDOMAIN + "/login\"";
        mockMvc.perform(get(pathPrefix + "/logged_out"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(expectedLoginHref)));
    }

    @ParameterizedTest
    @EnumSource(ContextPathMode.class)
    void loggedOutPageLoginLinkWithContextPath(ContextPathMode contextPathMode) throws Exception {
        String contextPath = contextPathMode == ContextPathMode.UAA ? UAA_CONTEXT_PATH : "";
        String requestPath = contextPath.isEmpty() ? "/logged_out" : contextPath + "/logged_out";
        String expectedLoginHref = contextPath.isEmpty() ? "href=\"/login\"" : "href=\"" + contextPath + "/login\"";
        mockMvc.perform(get(requestPath).contextPath(contextPath))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(expectedLoginHref)));
    }

    @EnableWebMvc
    @Import(ThymeleafConfig.class)
    static class ContextConfiguration implements WebMvcConfigurer {

        @Override
        public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
            configurer.enable();
        }

        @Bean
        BuildInfo buildInfo() {
            return new TestBuildInfo();
        }

        @Bean
        ResourceBundleMessageSource messageSource() {
            ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
            messageSource.setBasename("messages");
            return messageSource;
        }

        @Bean
        LoggedOutEndpoint loggedOutEndpoint() {
            return new LoggedOutEndpoint();
        }
    }
}
