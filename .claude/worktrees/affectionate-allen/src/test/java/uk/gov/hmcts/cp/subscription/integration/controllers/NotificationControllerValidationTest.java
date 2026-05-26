package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.subscription.controllers.GlobalExceptionHandler;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;
import uk.gov.hmcts.cp.subscription.managers.NotificationManager;

import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandler.class)
class NotificationControllerValidationTest extends IntegrationTestBase {

    private static final String NOTIFICATION_URI = "/notifications";
    private static final String PCR_REQUEST_VALID = "stubs/requests/progression/pcr-request-valid.json";
    private static final String PCR_REQUEST_MISSING_MATERIAL = "stubs/requests/progression/pcr-request-missing-material.json";
    private static final String PCR_REQUEST_MISSING_EVENT = "stubs/requests/progression/pcr-request-missing-event.json";
    private static final String SUBSCRIPTION_DOCUMENT_URI = "/client-subscriptions/{clientSubscriptionId}/documents/{documentId}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationManager notificationManager;

    UUID subscriptionId = randomUUID();
    UUID documentId = randomUUID();

    @Test
    void bad_content_type_should_return_415() throws Exception {
        String pcrPayload = loadPayload(PCR_REQUEST_VALID);

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(pcrPayload))
                .andDo(print())
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void invalid_payload_missing_materialid_should_return_400() throws Exception {
        String pcrPayload = loadPayload(PCR_REQUEST_MISSING_MATERIAL);

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pcrPayload))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalid_payload_missing_eventid_should_return_400() throws Exception {
        String pcrPayload = loadPayload(PCR_REQUEST_MISSING_EVENT);

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pcrPayload))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformed_payload_should_return_400() throws Exception {
        String malformedJson = "{ \"eventId\": \"invalid-uuid\", \"materialId\": }";

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void empty_payload_missing_eventid_should_return_400() throws Exception {
        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void missing_body_should_return_400() throws Exception {
        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_document_should_return_403_when_subscription_does_not_have_access() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: subscription does not have access to this document"))
                .when(notificationManager).getPcrDocumentContent(subscriptionId, TEST_CLIENT_ID, documentId);

        mockMvc.perform(get(SUBSCRIPTION_DOCUMENT_URI,
                        subscriptionId, documentId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("Access denied: subscription does not have access to this document"));
    }

    @Test
    void get_document_should_return_400_when_invalid_subscription_uuid() throws Exception {
        mockMvc.perform(get(SUBSCRIPTION_DOCUMENT_URI,
                        "invalid-uuid", documentId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_document_should_return_400_when_invalid_document_uuid() throws Exception {
        mockMvc.perform(get(SUBSCRIPTION_DOCUMENT_URI,
                        subscriptionId, "invalid-uuid")
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_document_should_return_404_when_document_not_found() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId))
                .when(notificationManager).getPcrDocumentContent(subscriptionId, TEST_CLIENT_ID, documentId);

        mockMvc.perform(get(SUBSCRIPTION_DOCUMENT_URI,
                        subscriptionId, documentId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void callbackUrl_delivery_failure_should_return_502() throws Exception {
        String pcrPayload = loadPayload(PCR_REQUEST_VALID);

        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CallbackUrl delivery failed"))
                .when(notificationManager).processPcrNotification(any());

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pcrPayload))
                .andDo(print())
                .andExpect(status().isBadGateway());
    }
}
