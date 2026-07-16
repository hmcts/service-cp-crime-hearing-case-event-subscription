package uk.gov.hmcts.cp.audit.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.audit.service.AuditSenderService;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;

@TestPropertySource(properties = {
        "cp.audit.enabled=true",
        "cp.audit.hosts[0]=localhost",
        "cp.audit.port=61616",
        "cp.audit.user=test",
        "cp.audit.password=test",
        "cp.audit.jms.session-cache-size=1",
        "management.health.jms.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jms.autoconfigure.JmsAutoConfiguration"
})
class AuditFilterIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    AuditSenderService auditSenderService;

    @Test
    void calling_audit_excluded_endpoint_without_correlation_id_should_proceed() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validNotificationPayload()))
                .andExpect(status().is(not(equalTo(403))));
    }

    @Test
    void calling_audit_detail_endpoint_without_correlation_id_should_return_403() throws Exception {
        final UUID subscriptionId = UUID.randomUUID();
        final UUID documentId     = UUID.randomUUID();

        mockMvc.perform(get("/client-subscriptions/{subId}/documents/{docId}", subscriptionId, documentId))
                .andExpect(status().isForbidden());
    }

    @Test
    void calling_audit_detail_endpoint_with_correlation_id_should_not_be_blocked_by_audit_filter() throws Exception {
        final UUID subscriptionId = UUID.randomUUID();
        final UUID documentId     = UUID.randomUUID();

        mockMvc.perform(get("/client-subscriptions/{subId}/documents/{docId}", subscriptionId, documentId)
                        .header(CORRELATION_ID_KEY, UUID.randomUUID().toString()))
                .andExpect(status().is(not(equalTo(403))));
    }

    private String validNotificationPayload() {
        return """
                {
                  "eventId": "%s",
                  "hearingId": "%s",
                  "materialId": "%s",
                  "eventType": "WEE_Remand",
                  "payload": "test"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
