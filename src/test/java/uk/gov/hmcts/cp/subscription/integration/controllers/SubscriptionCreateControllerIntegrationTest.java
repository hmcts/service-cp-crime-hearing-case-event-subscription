package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.cp.filters.UUIDService;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientEventEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesRegex;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionCreateControllerIntegrationTest extends IntegrationTestBase {

    private static final String SUBSCRIPTION_REQUEST_VALID = "stubs/requests/subscription/subscription-request-valid.json";
    private static final String SUBSCRIPTION_REQUEST_VALID_MULTIPLE_EVENTS = "stubs/requests/subscription/subscription-request-valid-multiple-events.json";

    @MockitoBean
    UUIDService uuidService;

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    String correlationId = "7fb1372b-a701-4e3c-840d-e22cac5af69f";

    @Test
    void create_subscription_should_save_subscription_with_hmac() throws Exception {
        when(uuidService.randomString()).thenReturn(correlationId);
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
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
                .andExpect(jsonPath("$.hmac.keyId", matchesRegex("^kid-v1.*")))
                .andExpect(jsonPath("$.hmac.secret", matchesRegex("[a-zA-Z0-9/+]*=")));
        assertClientInDb();
        assertThat(clientHmacRepository.findAll()).hasSize(1);
        assertClientEventsInDb();
    }

    @Test
    void create_subscription_should_save_subscription_with_hmac_and_multiple_eventTypes() throws Exception {
        when(uuidService.randomString()).thenReturn(correlationId);
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID_MULTIPLE_EVENTS);
        mockMvc.perform(post("/client-subscriptions")
                .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(header().stringValues("X-Correlation-Id", correlationId))
                .andExpect(jsonPath("$.clientSubscriptionId").exists())
                .andExpect(jsonPath("$.eventTypes.[0]").value("PRISON_COURT_REGISTER_GENERATED"))
                .andExpect(jsonPath("$.eventTypes.[1]").value("WEE_Layout5"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.hmac.keyId", matchesRegex("^kid-v1.*")))
                .andExpect(jsonPath("$.hmac.secret", matchesRegex("[a-zA-Z0-9/+]*=")));

        assertClientInDb();
        assertThat(clientHmacRepository.findAll()).hasSize(1);
        List<ClientEventEntity> clientEventEntities = clientEventRepository.findAll();
        assertThat(clientEventEntities).hasSize(2);
        assertThat(clientEventEntities.getFirst().getEventTypeId()).isEqualTo(1);
        assertThat(clientEventEntities.getLast().getEventTypeId()).isEqualTo(2);
    }

    @Test
    void duplicate_subscription_should_return_409_with_existing_subscription_id() throws Exception {
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        mockMvc.perform(post("/client-subscriptions")
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        List<ClientEventEntity> saved = clientEventRepository.findAll();
        assertThat(saved).hasSize(1);
        String existingId = saved.getFirst().getSubscriptionId().toString();

        mockMvc.perform(post("/client-subscriptions")
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("subscription already exist with " + existingId));
    }

    private void assertClientInDb() {
        List<ClientEntity> clientEntities = clientRepository.findAll();
        assertThat(clientEntities).hasSize(1);
        assertThat(clientEntities.getFirst().getSubscriptionId()).isNotNull();
    }

    private void assertClientEventsInDb() {
        List<ClientEventEntity> clientEventEntities = clientEventRepository.findAll();
        assertThat(clientEventEntities).hasSize(1);
        assertThat(clientEventEntities.getFirst().getSubscriptionId()).isNotNull();
        assertThat(clientEventEntities.getFirst().getEventTypeId()).isEqualTo(1);
    }
}