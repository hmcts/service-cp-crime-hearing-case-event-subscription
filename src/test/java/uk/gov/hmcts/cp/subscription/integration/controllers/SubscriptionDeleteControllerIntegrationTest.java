package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;
import uk.gov.hmcts.cp.subscription.integration.helpers.JwtHelper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionDeleteControllerIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void delete_client_subscription_should_delete_subscription() throws Exception {
        UUID subscriptionId = insertSubscription("https://example.com/event", List.of("PRISON_COURT_REGISTER_GENERATED"));
        mockMvc.perform(delete("/client-subscriptions/{id}", subscriptionId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNoContent());
        assertThat(clientRepository.findAll()).hasSize(0);
        assertThat(clientEventRepository.findAll()).hasSize(0);
    }

    @Test
    void delete_non_existent_subscription_should_return_404() throws Exception {
        mockMvc.perform(delete("/client-subscriptions/{id}", UUID.randomUUID())
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_subscription_belonging_to_different_client_should_return_404() throws Exception {
        UUID otherClientId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        UUID otherSubscriptionId = insertSubscription(
                otherClientId, List.of("PRISON_COURT_REGISTER_GENERATED"), "https://other-client.com/callback");

        mockMvc.perform(delete("/client-subscriptions/{id}", otherSubscriptionId)
                        .header("Authorization", JwtHelper.bearerTokenWithAzp(TEST_CLIENT_ID.toString())))
                .andDo(print())
                .andExpect(status().isNotFound());

        assertThat(clientRepository.findByClientIdAndSubscriptionId(otherClientId, otherSubscriptionId)).isPresent();
        assertThat(clientEventRepository.findBySubscriptionId(otherSubscriptionId)).isPresent();
    }
}