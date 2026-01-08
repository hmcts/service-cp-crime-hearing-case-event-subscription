package uk.gov.hmcts.cp.subscription.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionSaveControllerIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void save_client_subscription_should_save_subscription() throws Exception {
        String body = new ObjectMapper().writeValueAsString(request);
        mockMvc.perform(post("/client-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("client-id-todo", "1234")
                        .content(body))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientSubscriptionId").exists())
                .andExpect(jsonPath("$.eventTypes.[0]").value("CUSTODIAL_RESULT"))
                .andExpect(jsonPath("$.eventTypes.[1]").value("PRISON_COURT_REGISTER_GENERATED"))
                .andExpect(jsonPath("$.createdAt").exists());
        assertThatEventTypesAreSortedInDatabase();
    }

    private void assertThatEventTypesAreSortedInDatabase() {
        List<ClientSubscriptionEntity> entities = subscriptionRepository.findAll();
        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).getEventTypes().get(0)).isEqualTo(EntityEventType.CUSTODIAL_RESULT);
        assertThat(entities.get(0).getEventTypes().get(1)).isEqualTo(EntityEventType.PRISON_COURT_REGISTER_GENERATED);
    }
}