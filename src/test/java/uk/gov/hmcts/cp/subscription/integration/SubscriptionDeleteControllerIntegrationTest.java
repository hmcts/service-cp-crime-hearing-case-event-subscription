package uk.gov.hmcts.cp.subscription.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionDeleteControllerIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    @Transactional
    void delete_client_subscription_should_delete_subscription() throws Exception {
        ClientSubscriptionEntity entity = insertSubscription("https://example.com/event", List.of(EntityEventType.PCR));
        mockMvc.perform(delete("/client-subscriptions/{id}", entity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("client-id-todo", "1234"))
                .andDo(print())
                .andExpect(status().isNoContent());
        assertThat(subscriptionRepository.findAll()).hasSize(0);
    }
}