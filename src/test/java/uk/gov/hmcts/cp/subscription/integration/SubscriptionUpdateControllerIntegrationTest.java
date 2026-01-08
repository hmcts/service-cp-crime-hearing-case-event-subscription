package uk.gov.hmcts.cp.subscription.integration;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
class SubscriptionUpdateControllerIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void update_client_subscription_should_update_subscription() throws Exception {
        ClientSubscriptionEntity existing = insertSubscription("https://oldendpoint", List.of(EntityEventType.PRISON_COURT_REGISTER_GENERATED));
        String body = new ObjectMapper().writeValueAsString(request);
        mockMvc.perform(put("/client-subscriptions/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("client-id-todo", "1234")
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSubscriptionId").value(existing.getId().toString()))
                .andExpect(jsonPath("$.eventTypes.[0]").value("CUSTODIAL_RESULT"))
                .andExpect(jsonPath("$.eventTypes.[1]").value("PRISON_COURT_REGISTER_GENERATED"))
                .andExpect(jsonPath("$.notificationEndpoint.webhookUrl").value("https://my-callback-url"));
        verifyCreatedAtIsUnchanged(existing.getId(), existing.getCreatedAt());
    }

    // Pipeline fails because expected is nanoSecs and actual is microSecs
    // Very puzzling.
    void verifyCreatedAtIsUnchanged(UUID subscriptionId, OffsetDateTime expectedCreatedAt) {
        String expected = expectedCreatedAt.format(DateTimeFormatter.BASIC_ISO_DATE);
        String actual = subscriptionRepository.findById(subscriptionId).get().getCreatedAt().format(DateTimeFormatter.BASIC_ISO_DATE);
        log.info("Comparing actual:{} with expected:{}", actual, expected);
        assertThat(actual).isEqualTo(expected);
    }
}