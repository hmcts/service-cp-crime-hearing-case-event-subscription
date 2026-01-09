package uk.gov.hmcts.cp.subscription.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionGetControllerIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void get_subscription_should_return_expected() throws Exception {
        ClientSubscriptionEntity entity = insertSubscription("https://example.com/event", List.of(EntityEventType.PRISON_COURT_REGISTER_GENERATED));
        mockMvc.perform(get("/client-subscriptions/{id}", entity.getId())
                        .header("client-id-todo", "1234"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSubscriptionId").value(entity.getId().toString()))
                .andExpect(jsonPath("$.eventTypes.[0]").value("PRISON_COURT_REGISTER_GENERATED"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void get_no_subscription_should_return_404() throws Exception {
        mockMvc.perform(get("/client-subscriptions/{id}", UUID.randomUUID())
                        .header("client-id-todo", "1234"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(content().string("No row with the given identifier exists"));
    }
}