package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;

import java.net.URI;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.openapi.model.EventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.openapi.model.EventType.PCR;

class SubscriptionControllerIntegrationTest extends IntegrationTestBase {

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
        mockMvc.perform(post("/clientSubscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("client-id-todo", "1234")
                        .content(body))
                .andExpect(status().isOk());
    }
}