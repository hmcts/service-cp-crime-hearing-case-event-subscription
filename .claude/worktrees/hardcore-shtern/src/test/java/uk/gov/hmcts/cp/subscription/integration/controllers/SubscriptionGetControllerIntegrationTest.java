package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionGetControllerIntegrationTest extends IntegrationTestBase {

    UUID subscriptionId=UUID.fromString("0a3f88fb-1573-43aa-92be-40ad86e561fe");

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void get_subscription_should_return_expected() throws Exception {
        ClientSubscriptionEntity entity = insertSubscription("https://example.com/event", List.of("PRISON_COURT_REGISTER_GENERATED"));
        mockMvc.perform(get("/client-subscriptions/{id}", entity.getId())
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSubscriptionId").value(entity.getId().toString()))
                .andExpect(jsonPath("$.eventTypes.[0]").value("PRISON_COURT_REGISTER_GENERATED"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void get_no_subscription_should_return_404() throws Exception {
        mockMvc.perform(get("/client-subscriptions/{id}", subscriptionId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"))
                .andExpect(jsonPath("$.message").value("Subscription not found"));
    }
}