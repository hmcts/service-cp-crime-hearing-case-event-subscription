package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.filters.CorrelationIdService;
import uk.gov.hmcts.cp.hmac.services.EncodingService;
import uk.gov.hmcts.cp.hmac.services.HmacKeyService;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionCreateControllerIntegrationTest extends IntegrationTestBase {

    private static final String SUBSCRIPTION_REQUEST_VALID = "stubs/requests/subscription/subscription-request-valid.json";

    @MockitoBean
    CorrelationIdService correlationIdService;
    @Autowired
    HmacKeyService hmacKeyService;
    @Autowired
    EncodingService encodingService;

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    String correlationId = "7fb1372b-a701-4e3c-840d-e22cac5af69f";

    @Test
    void create_subscription_should_save_subscription_with_hmac() throws Exception {
        when(correlationIdService.randomString()).thenReturn(correlationId);
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        String expectedSecret = encodingService.encodeWithBase64(hmacKeyService.generateKey().getSecret());
        mockMvc.perform(post("/client-subscriptions")
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(header().stringValues("X-Correlation-Id", correlationId))
                .andExpect(jsonPath("$.clientSubscriptionId").exists())
                .andExpect(jsonPath("$.eventTypes.[0]").value("PRISON_COURT_REGISTER_GENERATED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.hmac.keyId").value("kid-f4f5dc10-d6d8-4e94-8b02-459c4121aad0"))
                .andExpect(jsonPath("$.hmac.secret").value(expectedSecret));
        assertThatEventTypesAreSortedInDatabase();
    }

    @Test
    void duplicate_subscription_should_return_409_with_existing_subscription_id() throws Exception {
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        mockMvc.perform(post("/client-subscriptions")
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        List<ClientSubscriptionEntity> saved = subscriptionRepository.findAll();
        assertThat(saved).hasSize(1);
        String existingId = saved.get(0).getId().toString();

        mockMvc.perform(post("/client-subscriptions")
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("subscription already exist with " + existingId));
    }

    private void assertThatEventTypesAreSortedInDatabase() {
        List<ClientSubscriptionEntity> entities = subscriptionRepository.findAll();
        assertThat(entities).hasSize(1);
        assertThat(entities.getFirst().getEventTypes().getFirst()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
    }
}