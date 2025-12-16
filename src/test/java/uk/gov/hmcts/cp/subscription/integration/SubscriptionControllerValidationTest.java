package uk.gov.hmcts.cp.subscription.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.repositories.SubscriptionRepository;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.openapi.model.EventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.openapi.model.EventType.PCR;

class SubscriptionControllerValidationTest extends IntegrationTestBase {

    @Autowired
    SubscriptionRepository subscriptionRepository;

    NotificationEndpoint notificationEndpoint = NotificationEndpoint.builder()
            .webhookUrl("https://my-callback-url")
            .build();
    ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
            .notificationEndpoint(notificationEndpoint)
            .eventTypes(List.of(PCR, CUSTODIAL_RESULT))
            .build();

    @Test
    void bad_event_type_should_return_400() throws Exception {
        String body = new ObjectMapper().writeValueAsString(request)
                .replace("PCR", "BAD");
        mockMvc.perform(post("/client-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void webhook_bad_url_should_return_400() throws Exception {
        String body = new ObjectMapper().writeValueAsString(request)
                .replace("https://my-callback-url", "not-a-url");
        mockMvc.perform(post("/client-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }
}