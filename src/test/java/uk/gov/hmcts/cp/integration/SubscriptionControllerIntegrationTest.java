package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.model.EntityEventType;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.repositories.SubscriptionRepository;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.openapi.model.EventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.openapi.model.EventType.PCR;

class SubscriptionControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    SubscriptionRepository subscriptionRepository;

    NotificationEndpoint notificationEndpoint = NotificationEndpoint.builder()
            .webhookUrl(URI.create("https://my-callback-url"))
            .build();
    ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
            .notificationEndpoint(notificationEndpoint)
            .eventTypes(List.of(PCR, CUSTODIAL_RESULT))
            .build();

    @Test
    @Transactional
    void save_client_subscription_should_save_subscription() throws Exception {
        String body = new ObjectMapper().writeValueAsString(request);
        mockMvc.perform(post("/client-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("client-id-todo", "1234")
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSubscriptionId").exists())
                .andExpect(jsonPath("$.eventTypes.[0]").value("CUSTODIAL_RESULT"))
                .andExpect(jsonPath("$.eventTypes.[1]").value("PCR"))
                .andExpect(jsonPath("$.createdAt").exists());
        assertThatEventTypesAreSortedInDatabase();
    }

    @Test
    @Transactional
    void get_subscription_should_return_expected() throws Exception {
        ClientSubscriptionEntity entity = insertSubscription("http://example.com/event", List.of(EntityEventType.PCR));
        mockMvc.perform(get("/client-subscriptions/{id}", entity.getId())
                        .header("client-id-todo", "1234"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSubscriptionId").value(entity.getId().toString()))
                .andExpect(jsonPath("$.eventTypes.[0]").value("PCR"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    private void assertThatEventTypesAreSortedInDatabase() {
        List<ClientSubscriptionEntity> entities = subscriptionRepository.findAll();
        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).getEventTypes().get(0)).isEqualTo(EntityEventType.CUSTODIAL_RESULT);
        assertThat(entities.get(0).getEventTypes().get(1)).isEqualTo(EntityEventType.PCR);
    }
}