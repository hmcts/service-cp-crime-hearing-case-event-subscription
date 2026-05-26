package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionControllerValidationTest extends IntegrationTestBase {

    private static final String CLIENT_SUBSCRIPTIONS = "/client-subscriptions";
    private static final String CLIENT_SUBSCRIPTIONS_BY_ID = "/client-subscriptions/{id}";
    private static final String SUBSCRIPTION_REQUEST_VALID = "stubs/requests/subscription/subscription-request-valid.json";
    private static final String SUBSCRIPTION_REQUEST_BAD_EVENT = "stubs/requests/subscription/subscription-request-bad-event-type.json";
    private static final String SUBSCRIPTION_REQUEST_INVALID_CALLBACK = "stubs/requests/subscription/subscription-request-invalid-callback-url.json";

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void bad_event_type_should_return_400() throws Exception {
        String body = loadPayload(SUBSCRIPTION_REQUEST_BAD_EVENT);
        mockMvc.perform(post(CLIENT_SUBSCRIPTIONS)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("Invalid event type: BAD"));
    }

    @Test
    void callback_url_invalid_should_return_400() throws Exception {
        String body = loadPayload(SUBSCRIPTION_REQUEST_INVALID_CALLBACK);
        mockMvc.perform(post(CLIENT_SUBSCRIPTIONS)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformed_json_body_should_return_400() throws Exception {
        mockMvc.perform(post(CLIENT_SUBSCRIPTIONS)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{notjson"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void empty_json_body_should_return_400() throws Exception {
        mockMvc.perform(post(CLIENT_SUBSCRIPTIONS)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void missing_body_should_return_400() throws Exception {
        mockMvc.perform(post(CLIENT_SUBSCRIPTIONS)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrong_content_type_should_return_415() throws Exception {
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        mockMvc.perform(post(CLIENT_SUBSCRIPTIONS)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(body))
                .andDo(print())
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void missing_auth_header_on_post_should_return_401() throws Exception {
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        mockMvc.perform(post(CLIENT_SUBSCRIPTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void put_with_non_uuid_id_should_return_400() throws Exception {
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        mockMvc.perform(put(CLIENT_SUBSCRIPTIONS_BY_ID, "not-a-uuid")
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void missing_auth_header_on_put_should_return_401() throws Exception {
        String body = loadPayload(SUBSCRIPTION_REQUEST_VALID);
        mockMvc.perform(put(CLIENT_SUBSCRIPTIONS_BY_ID, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_with_non_uuid_id_should_return_400() throws Exception {
        mockMvc.perform(delete(CLIENT_SUBSCRIPTIONS_BY_ID, "not-a-uuid")
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void missing_auth_header_on_delete_should_return_401() throws Exception {
        mockMvc.perform(delete(CLIENT_SUBSCRIPTIONS_BY_ID, UUID.randomUUID()))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_with_non_uuid_id_should_return_400() throws Exception {
        mockMvc.perform(get(CLIENT_SUBSCRIPTIONS_BY_ID, "not-a-uuid")
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void missing_auth_header_on_get_should_return_401() throws Exception {
        mockMvc.perform(get(CLIENT_SUBSCRIPTIONS_BY_ID, UUID.randomUUID()))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }
}