package org.cloudfoundry.identity.uaa;

import org.cloudfoundry.experimental.boot.UaaBootConfiguration;
import org.cloudfoundry.identity.uaa.extensions.PollutionPreventionExtension;
import org.cloudfoundry.identity.uaa.impl.config.YamlServletProfileInitializer;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Same as {@link DefaultTestContext} but adds {@code servlet.session-store=memory} with highest
 * precedence so the context uses {@link org.cloudfoundry.identity.uaa.session.ZoneNamespacedSessionRepository}.
 * Use for tests that need zone-namespaced sessions (e.g. browse-back across path-based zones).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(PollutionPreventionExtension.class)
@WebAppConfiguration
@EnableWebMvc
@SpringBootTest(
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.main.allow-circular-references=true",
                "logging.level.org.springframework.security=TRACE"
        },
        classes = {
                UaaBootConfiguration.class,
                UaaApplicationConfiguration.class,
                TestClientAndMockMvcTestConfig.class,
                DatabasePropertiesOverrideConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@SpringJUnitConfig(initializers = {
        TestPropertyInitializer.class,
        YamlServletProfileInitializer.class,
        AlwaysMemorySessionInitializer.class
})
@EnableAutoConfiguration(exclude = {
        SessionAutoConfiguration.class,
        LdapAutoConfiguration.class
})
@TestPropertySource(properties = "uaa.zone.session.test.context=true")
public @interface ZonePathSessionTestContext {
}
