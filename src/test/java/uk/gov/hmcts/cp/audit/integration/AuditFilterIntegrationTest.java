package uk.gov.hmcts.cp.audit.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.audit.model.AuditPayload;
import uk.gov.hmcts.cp.audit.service.AuditClockService;
import uk.gov.hmcts.cp.audit.service.AuditSenderService;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "cp.audit.enabled=true")
class AuditFilterIntegrationTest extends IntegrationTestBase {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T10:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean
    AuditSenderService auditSenderService;

    @MockitoBean(name = "auditClockService")
    AuditClockService auditClockService;

    ArgumentCaptor<AuditPayload> payloadCaptor;

    @BeforeEach
    void setUp() {
        clearAllTables();
        when(auditClockService.now()).thenReturn(FIXED_TIME);
        payloadCaptor = ArgumentCaptor.forClass(AuditPayload.class);
    }

    @Test
    void calling_audit_excluded_endpoint_should_not_send_audit_event() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"UNKNOWN_EVENT_TYPE\",\"eventId\":\"a4554152-10fb-44fe-a015-226f8d547c91\","
                                + "\"materialId\":\"886a3d9c-2543-4fdd-8b5c-1597e3d36ebb\","
                                + "\"hearingId\":\"b2c3d4e5-f6a7-8901-bcde-f12345678901\","
                                + "\"timestamp\":\"2026-05-29T10:23:29Z\","
                                + "\"defendant\":{\"masterDefendantId\":\"f08465c5-0000-0000-0000-000000000000\","
                                + "\"name\":\"Test Defendant\",\"dateOfBirth\":\"2000-01-01\",\"cases\":[{\"urn\":\"TEST123\"}]}}"))
                .andExpect(status().isAccepted());

        verify(auditSenderService, never()).send(any());
    }

    @Test
    void calling_audit_included_endpoint_should_send_request_and_response_audit_events() throws Exception {
        final UUID subscriptionId = insertSubscription("https://callback", List.of("PRISON_COURT_REGISTER_GENERATED"));

        mockMvc.perform(get("/client-subscriptions/{subscriptionId}", subscriptionId)
                        .header(AUTHORIZATION, AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk());

        verify(auditSenderService, times(2)).send(payloadCaptor.capture());
        final List<AuditPayload> payloads = payloadCaptor.getAllValues();
        final UUID correlationId = payloads.get(0).getCorrelationId();

        JSONAssert.assertEquals(
                expectedRequest(correlationId, subscriptionId),
                MAPPER.writeValueAsString(payloads.get(0)),
                JSONCompareMode.STRICT);

        JSONAssert.assertEquals(
                expectedResponse(correlationId, subscriptionId),
                MAPPER.writeValueAsString(payloads.get(1)),
                JSONCompareMode.STRICT);
    }

    @Test
    void calling_audit_included_endpoint_when_audit_fails_should_still_return_ok() throws Exception {
        final UUID subscriptionId = insertSubscription("https://callback", List.of("PRISON_COURT_REGISTER_GENERATED"));
        doThrow(new RuntimeException("audit broker down")).when(auditSenderService).send(any());

        mockMvc.perform(get("/client-subscriptions/{subscriptionId}", subscriptionId)
                        .header(AUTHORIZATION, AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk());
    }

    private String expectedRequest(final UUID correlationId, final UUID subscriptionId) {
        return """
                {
                  "_metadata": {
                    "origin":    "hearing-results-document",
                    "component": "QUERY_API",
                    "eventName": "hrds.get-client-subscription",
                    "timestamp": "2026-01-01T10:00:00Z"
                  },
                  "eventType":       "REQUEST",
                  "action":          "View",
                  "correlationId":   "%s",
                  "responseStatus":  null,
                  "materialId":      null,
                  "caseId":          null,
                  "hearingId":       null,
                  "courtDocumentId": null,
                  "pathParams": { "clientSubscriptionId": "%s" }
                }
                """.formatted(correlationId, subscriptionId);
    }

    private String expectedResponse(final UUID correlationId, final UUID subscriptionId) {
        return """
                {
                  "_metadata": {
                    "origin":    "hearing-results-document",
                    "component": "QUERY_API",
                    "eventName": "hrds.get-client-subscription",
                    "timestamp": "2026-01-01T10:00:00Z"
                  },
                  "eventType":       "RESPONSE",
                  "action":          "View",
                  "correlationId":   "%s",
                  "responseStatus":  200,
                  "materialId":      null,
                  "caseId":          null,
                  "hearingId":       null,
                  "courtDocumentId": null,
                  "pathParams": { "clientSubscriptionId": "%s" }
                }
                """.formatted(correlationId, subscriptionId);
    }
}
