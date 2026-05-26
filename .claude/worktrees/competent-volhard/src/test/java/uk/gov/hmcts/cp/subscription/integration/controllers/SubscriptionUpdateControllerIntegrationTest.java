package uk.gov.hmcts.cp.subscription.integration.controllers;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;
import uk.gov.hmcts.cp.subscription.integration.helpers.JwtHelper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
class SubscriptionUpdateControllerIntegrationTest extends IntegrationTestBase {

    private static final String SUBSCRIPTION_REQUEST_VALID = "stubs/requests/subscription/subscription-request-valid.json";

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void update_client_subscription_should_update_subscription() throws Exception {
        ClientSubscriptionEntity existing = insertSubscription("https://oldendpoint", List.of("PRISON_COURT_REGISTER_GENERATED"));
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        mockMvc.perform(put("/client-subscriptions/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSubscriptionId").value(existing.getId().toString()))
                .andExpect(jsonPath("$.eventTypes.[0]").value("PRISON_COURT_REGISTER_GENERATED"))
                .andExpect(jsonPath("$.notificationEndpoint.callbackUrl").value("https://my-callback-url"));
        verifyCreatedAtIsUnchangedAndUpdateAtIsDifferentFromCreatedAt(existing.getId(), existing.getCreatedAt());
    }

    @Test
    void update_non_existent_subscription_should_return_404() throws Exception {
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        mockMvc.perform(put("/client-subscriptions/{id}", UUID.randomUUID())
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void update_subscription_belonging_to_different_client_should_return_404() throws Exception {
        UUID otherClientId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        ClientSubscriptionEntity otherClientSubscription = insertSubscription(
                otherClientId, List.of("PRISON_COURT_REGISTER_GENERATED"), "https://other-client.com/callback");

        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        mockMvc.perform(put("/client-subscriptions/{id}", otherClientSubscription.getId())
                        .header("Authorization", JwtHelper.bearerTokenWithAzp(TEST_CLIENT_ID.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    // Pipeline fails because expected is nanoSecs and actual is microSecs
    // Very puzzling.
    void verifyCreatedAtIsUnchangedAndUpdateAtIsDifferentFromCreatedAt(UUID subscriptionId, OffsetDateTime expectedCreatedAt) {
        ClientSubscriptionEntity entity = subscriptionRepository.findById(subscriptionId).orElseThrow();
        String expected = expectedCreatedAt.format(DateTimeFormatter.BASIC_ISO_DATE);
        String actualCreated = entity.getCreatedAt().format(DateTimeFormatter.BASIC_ISO_DATE);
        log.info("Comparing actual:{} with expected:{}", actualCreated, expected);
        assertThat(actualCreated).isEqualTo(expected);
        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotEqualTo(entity.getCreatedAt());
        assertThat(entity.getUpdatedAt()).isAfter(entity.getCreatedAt());
    }
}