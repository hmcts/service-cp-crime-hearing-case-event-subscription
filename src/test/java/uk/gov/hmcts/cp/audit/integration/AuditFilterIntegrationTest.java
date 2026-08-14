package uk.gov.hmcts.cp.audit.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.audit.config.ArtemisAuditAutoConfiguration;
import uk.gov.hmcts.cp.audit.model.AuditMessage;
import uk.gov.hmcts.cp.audit.service.AuditClockService;
import uk.gov.hmcts.cp.audit.service.AuditSenderService;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    /** Sent as the CJSCPPUID request header — the source the audit library reads the user from. */
    private static final String CJSCPPUID_HEADER = "CJSCPPUID";
    private static final String TEST_USER_ID = "99999999-8888-7777-6666-555555555555";
    /**
     * The production mapper, not a locally-configured one — a local mapper with
     * WRITE_DATES_AS_TIMESTAMPS disabled is what previously hid the numeric-timestamp bug that
     * audit2dls rejected.
     */
    private static final ObjectMapper MAPPER = new ArtemisAuditAutoConfiguration().auditObjectMapper();

    @MockitoBean
    AuditSenderService auditSenderService;

    @MockitoBean(name = "auditClockService")
    AuditClockService auditClockService;

    ArgumentCaptor<AuditMessage> payloadCaptor;

    @BeforeEach
    void setUp() {
        clearAllTables();
        when(auditClockService.now()).thenReturn(FIXED_TIME);
        payloadCaptor = ArgumentCaptor.forClass(AuditMessage.class);
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
                        .header(AUTHORIZATION, AUTHORIZATION_HEADER_VALUE)
                        .header(CJSCPPUID_HEADER, TEST_USER_ID))
                .andExpect(status().isOk());

        verify(auditSenderService, times(2)).send(payloadCaptor.capture());
        final List<AuditMessage> payloads = payloadCaptor.getAllValues();
        final UUID correlationId = payloads.get(0).getContent().getCorrelationId();
        final UUID metadataId = payloads.get(0).getMetadata().getId();

        JSONAssert.assertEquals(
                expectedRequest(correlationId, subscriptionId, metadataId),
                MAPPER.writeValueAsString(payloads.get(0)),
                JSONCompareMode.STRICT);

        JSONAssert.assertEquals(
                expectedResponse(correlationId, subscriptionId, metadataId),
                MAPPER.writeValueAsString(payloads.get(1)),
                JSONCompareMode.STRICT);
    }

    @Test
    void audit_event_should_carry_no_user_when_the_cjscppuid_header_is_absent() throws Exception {
        final UUID subscriptionId = insertSubscription("https://callback", List.of("PRISON_COURT_REGISTER_GENERATED"));

        mockMvc.perform(get("/client-subscriptions/{subscriptionId}", subscriptionId)
                        .header(AUTHORIZATION, AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk());

        verify(auditSenderService, times(2)).send(payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues())
                .allSatisfy(message -> assertThat(message.getMetadata().getContext().user()).isNull());
    }

    @Test
    void getting_subscription_should_log_audit_payload_json() throws Exception {
        final UUID subscriptionId = insertSubscription("https://callback", List.of("PRISON_COURT_REGISTER_GENERATED"));

        mockMvc.perform(get("/client-subscriptions/{subscriptionId}", subscriptionId)
                        .header(AUTHORIZATION, AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk());

        verify(auditSenderService, times(2)).send(payloadCaptor.capture());
        final List<AuditMessage> payloads = payloadCaptor.getAllValues();

        System.out.println("=== REQUEST audit payload ===");
        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payloads.get(0)));
        System.out.println("=== RESPONSE audit payload ===");
        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payloads.get(1)));
    }

    @Test
    void calling_audit_included_endpoint_when_audit_fails_should_still_return_ok() throws Exception {
        final UUID subscriptionId = insertSubscription("https://callback", List.of("PRISON_COURT_REGISTER_GENERATED"));
        doThrow(new RuntimeException("audit broker down")).when(auditSenderService).send(any());

        mockMvc.perform(get("/client-subscriptions/{subscriptionId}", subscriptionId)
                        .header(AUTHORIZATION, AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk());
    }

    private String expectedRequest(final UUID correlationId, final UUID subscriptionId, final UUID metadataId) {
        return """
                {
                  "_metadata": {
                    "id":        "%s",
                    "name":      "audit.events.audit-recorded",
                    "createdAt": "2026-01-01T10:00:00Z",
                    "context":   { "user": "%s" }
                  },
                  "origin":    "hearing-results-document",
                  "component": "QUERY_API",
                  "timestamp": "2026-01-01T10:00:00Z",
                  "content": {
                    "eventName":       "hrds.get-client-subscription",
                    "eventType":       "REQUEST",
                    "action":          "View",
                    "clientId":        "11111111-2222-3333-4444-555555555555",
                    "correlationId":   "%s",
                    "responseStatus":  null,
                    "materialId":      null,
                    "caseId":          null,
                    "hearingId":       null,
                    "courtDocumentId": null,
                    "pathParams": { "clientSubscriptionId": "%s" }
                  }
                }
                """.formatted(metadataId, TEST_USER_ID, correlationId, subscriptionId);
    }

    private String expectedResponse(final UUID correlationId, final UUID subscriptionId, final UUID metadataId) {
        return """
                {
                  "_metadata": {
                    "id":        "%s",
                    "name":      "audit.events.audit-recorded",
                    "createdAt": "2026-01-01T10:00:00Z",
                    "context":   { "user": "%s" }
                  },
                  "origin":    "hearing-results-document",
                  "component": "QUERY_API",
                  "timestamp": "2026-01-01T10:00:00Z",
                  "content": {
                    "eventName":       "hrds.get-client-subscription",
                    "eventType":       "RESPONSE",
                    "action":          "View",
                    "clientId":        "11111111-2222-3333-4444-555555555555",
                    "correlationId":   "%s",
                    "responseStatus":  200,
                    "materialId":      null,
                    "caseId":          null,
                    "hearingId":       null,
                    "courtDocumentId": null,
                    "pathParams": { "clientSubscriptionId": "%s" }
                  }
                }
                """.formatted(metadataId, TEST_USER_ID, correlationId, subscriptionId);
    }
}
