package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The auth counters are useless if nothing can read them. Exposing "prometheus" under
 * management.endpoints.web.exposure.include does nothing on its own — the endpoint only exists when
 * a registry implementation is on the classpath, and without one this scrape 404s.
 */
class ActuatorEndpointsIntegrationTest extends IntegrationTestBase {

    /**
     * Scrapers and probes send no Authorization header, so every exposed actuator endpoint has to
     * answer without one. AuthorizationPolicy exempts the whole /actuator subtree; this checks the
     * exemption survives the real filter chain, with auth.mode=ENFORCE.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/info", "/actuator/prometheus"})
    void actuator_endpoints_should_answer_without_a_token(final String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk());
    }

    @Test
    void successful_auth_should_be_visible_as_a_counter() throws Exception {
        UUID subscriptionId = insertSubscription("https://example.com/event", List.of("PRISON_COURT_REGISTER_GENERATED"));
        mockMvc.perform(get("/client-subscriptions/{id}", subscriptionId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk());

        String scrape = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(scrape).contains("cp_auth_success_total");
    }
}
