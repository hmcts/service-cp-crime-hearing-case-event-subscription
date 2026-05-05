package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;
import uk.gov.hmcts.cp.subscription.integration.helpers.JwtHelper;
import uk.gov.hmcts.cp.vault.SecretStoreServiceStubImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionRotateSecretControllerIntegrationTest extends IntegrationTestBase {

    private static final String EXISTING_KEY_ID = "kid-v1-keyid";

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void rotate_secret_should_return_200_with_same_key_id_and_new_secret() throws Exception {
        UUID subscriptionId = insertSubscription(TEST_CLIENT_ID, List.of("PRISON_COURT_REGISTER_GENERATED"), "https://example.com/callback", EXISTING_KEY_ID);
        String body = "{\"keyId\":\"" + EXISTING_KEY_ID + "\"}";
        
        mockMvc.perform(post("/client-subscriptions/{id}/secret/rotate", subscriptionId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value(EXISTING_KEY_ID))
                .andExpect(jsonPath("$.secret").exists());

        assertThat(SecretStoreServiceStubImpl.encodedSecret).isNotNull();
    }

    @Test
    void rotate_secret_should_return_404_when_key_id_does_not_match() throws Exception {
        UUID subscriptionId = insertSubscription(TEST_CLIENT_ID, List.of("PRISON_COURT_REGISTER_GENERATED"), "https://example.com/callback", EXISTING_KEY_ID);
        String body = "{\"keyId\":\"kid-v1-wrong\"}";

        mockMvc.perform(post("/client-subscriptions/{id}/secret/rotate", subscriptionId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void rotate_secret_should_return_404_when_subscription_not_found() throws Exception {
        String body = "{\"keyId\":\"" + EXISTING_KEY_ID + "\"}";

        mockMvc.perform(post("/client-subscriptions/{id}/secret/rotate", UUID.randomUUID())
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void rotate_secret_should_return_404_when_subscription_belongs_to_different_client() throws Exception {
        UUID otherClientId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        UUID otherSubscriptionId = insertSubscription(
                otherClientId, List.of("PRISON_COURT_REGISTER_GENERATED"), "https://other.com/callback", EXISTING_KEY_ID);
        String body = "{\"keyId\":\"" + EXISTING_KEY_ID + "\"}";

        mockMvc.perform(post("/client-subscriptions/{id}/secret/rotate", otherSubscriptionId)
                        .header("Authorization", JwtHelper.bearerTokenWithAzp(TEST_CLIENT_ID.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound());
    }
}
