package uk.gov.hmcts.cp.subscription.integration.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.cp.openapi.model.EventTypePayload;
import uk.gov.hmcts.cp.openapi.model.EventTypeResponse;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionEventTypeGetControllerIntegrationTest extends IntegrationTestBase {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void get_subscription_should_return_expected() throws Exception {
        EventTypePayload expectedEventType1 = EventTypePayload.builder()
                .eventName("PRISON_COURT_REGISTER_GENERATED")
                .displayName("Prison court register")
                .category("REGISTER")
                .build();
        EventTypePayload expectedEventType2 = EventTypePayload.builder()
                .eventName("WEE_Layout5")
                .displayName("Warrant Supplement")
                .category("WARRANT")
                .build();

        MvcResult result = mockMvc.perform(get("/event-types"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        EventTypeResponse getEventTypes = OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), EventTypeResponse.class);
        assertThat(getEventTypes.getEvents().size()).isEqualTo(41);
        assertThat(getEventTypes.getEvents()).contains(expectedEventType1, expectedEventType2);
    }
}