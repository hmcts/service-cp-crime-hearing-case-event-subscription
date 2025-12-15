package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.model.EntityEventType;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.repositories.SubscriptionRepository;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.openapi.model.EventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.openapi.model.EventType.PCR;

class SubscriptionGetControllerIntegrationTest extends IntegrationTestBase {

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

    @Test
    @Transactional
    void get_no_subscription_should_return_404() throws Exception {
        mockMvc.perform(get("/client-subscriptions/{id}", UUID.randomUUID())
                        .header("client-id-todo", "1234"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(content().string("No row with the given identifier exists"));
    }
}