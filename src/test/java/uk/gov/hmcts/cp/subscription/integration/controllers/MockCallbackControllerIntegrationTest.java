package uk.gov.hmcts.cp.subscription.integration.controllers;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.subscription.integration.stubs.SubscriptionStub.jsonMapper;

@TestPropertySource(properties = {
        "vault.enabled=false",
        "environment.name=DEV"
})
class MockCallbackControllerIntegrationTest extends IntegrationTestBase {

    private static final String MOCK_CALLBACK_URI = "/mock-callback";
    private static final String MOCK_CALLBACK_RECEIVED_URI = "/mock-callback/received";
    private static final String PAYLOAD = "{\"eventId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"eventType\":\"PRISON_COURT_REGISTER_GENERATED\"}";
    private static final String SUBSCRIPTION_REQUEST = """
            {
              "notificationEndpoint": { "callbackUrl": "https://mock-callback-test.example.com" },
              "eventTypes": ["PRISON_COURT_REGISTER_GENERATED"]
            }
            """;

    private String keyId;
    private String secret;

    @BeforeEach
    void setUp() throws Exception {
        clearAllTables();
        mockMvc.perform(delete(MOCK_CALLBACK_RECEIVED_URI)).andExpect(status().isOk());

        final MvcResult result = mockMvc.perform(post(CLIENT_SUBSCRIPTIONS_URI)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBSCRIPTION_REQUEST))
                .andExpect(status().isCreated())
                .andReturn();

        final String body = result.getResponse().getContentAsString();
        keyId = jsonMapper.getStringAtPath(body, "/hmac/keyId");
        secret = jsonMapper.getStringAtPath(body, "/hmac/secret");
    }

    @Test
    void valid_signature_should_return_200() throws Exception {
        final String signature = signPayload(secret, PAYLOAD);

        mockMvc.perform(post(MOCK_CALLBACK_URI)
                        .header("X-Key-Id", keyId)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk());
    }

    @Test
    void invalid_signature_should_return_401() throws Exception {
        mockMvc.perform(post(MOCK_CALLBACK_URI)
                        .header("X-Key-Id", keyId)
                        .header("X-Signature", "invalid-signature==")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_received_callbacks_should_return_empty_list_initially() throws Exception {
        final MvcResult result = mockMvc.perform(get(MOCK_CALLBACK_RECEIVED_URI))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    void valid_callback_should_appear_in_received_list() throws Exception {
        final String signature = signPayload(secret, PAYLOAD);

        mockMvc.perform(post(MOCK_CALLBACK_URI)
                        .header("X-Key-Id", keyId)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        final MvcResult result = mockMvc.perform(get(MOCK_CALLBACK_RECEIVED_URI))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    }

    @Test
    void invalid_callback_should_not_appear_in_received_list() throws Exception {
        mockMvc.perform(post(MOCK_CALLBACK_URI)
                        .header("X-Key-Id", keyId)
                        .header("X-Signature", "invalid-signature==")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());

        final MvcResult result = mockMvc.perform(get(MOCK_CALLBACK_RECEIVED_URI))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    void delete_received_callbacks_should_clear_the_list() throws Exception {
        final String signature = signPayload(secret, PAYLOAD);
        mockMvc.perform(post(MOCK_CALLBACK_URI)
                        .header("X-Key-Id", keyId)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        mockMvc.perform(delete(MOCK_CALLBACK_RECEIVED_URI))
                .andExpect(status().isOk());

        final MvcResult result = mockMvc.perform(get(MOCK_CALLBACK_RECEIVED_URI))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @SneakyThrows
    private String signPayload(final String base64Secret, final String payload) {
        final byte[] secretBytes = Base64.getDecoder().decode(base64Secret);
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        final byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmac);
    }

}
