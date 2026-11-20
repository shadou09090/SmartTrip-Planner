package com.project.back_end_proyecto.config;

import com.project.back_end_proyecto.controller.LocalDevAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDevAuthProfileIsolationTest {
    private static final Map<String, Object> TEST_PROPERTIES = Map.of(
            "smarttrip.jwt.secret", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "smarttrip.local-auth.bootstrap-token", "local-test-bootstrap-token-0123456789abcdef");

    @Test
    void devIssuerExistsOnlyInLocalNoDbAndOnlyWhenExplicitBootstrapTokenIsConfigured() {
        assertIssuerPresence("local-no-db", true, true);
        assertIssuerPresence("oracle", true, false);
        assertIssuerPresence("prod", true, false);
        assertIssuerPresence("production", true, false);
        assertIssuerPresence("local-no-db", false, false);
    }

    private static void assertIssuerPresence(String activeProfile, boolean configured, boolean expectedPresent) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(activeProfile);
            Map<String, Object> properties = configured ? TEST_PROPERTIES :
                    Map.of("smarttrip.jwt.secret", TEST_PROPERTIES.get("smarttrip.jwt.secret"));
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("local-dev-auth-test", properties));
            context.register(LocalNoDbJwtConfig.class, LocalDevAuthController.class);
            context.refresh();
            assertEquals(expectedPresent, context.getBeansOfType(LocalDevAuthController.class).size() == 1,
                    "Unexpected local token issuer presence for profile " + activeProfile);
            if (activeProfile.equals("local-no-db") && configured) {
                assertTrue(context.containsBean("localNoDbJwtDecoder"));
            }
            if (!activeProfile.equals("local-no-db")) {
                assertFalse(context.containsBean("localNoDbJwtDecoder"));
            }
        }
    }
}
