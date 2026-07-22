package uk.gov.hmcts.cp.audit.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.audit.service.AuditSenderService;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "cp.audit.enabled=true")
class AuditFilterIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    AuditSenderService auditSenderService;

    @BeforeEach
    void setUp() {
        clearAllTables();
    }

    @Test
    void calling_audit_excluded_endpoint_should_not_send_audit_event() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loadPayload("stubs/requests/progression/pcr-notification-from-progression.json")))
                .andExpect(status().isAccepted());
        verify(auditSenderService, never()).send(any());
    }

    @Test
    void calling_audit_included_endpoint_should_send_audit_event() throws Exception {
        final UUID subscriptionId = insertSubscription("https://callback", List.of("PRISON_COURT_REGISTER_GENERATED"));
        mockMvc.perform(get("/client-subscriptions/{subscriptionId}", subscriptionId)
                        .header(AUTHORIZATION, AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk());
        verify(auditSenderService, times(2)).send(any());
    }

    @Test
    void calling_audit_included_endpoint_when_audit_fails_should_still_return_ok() throws Exception {
        final UUID subscriptionId = insertSubscription("https://callback", List.of("PRISON_COURT_REGISTER_GENERATED"));
        doThrow(new RuntimeException("audit broker down")).when(auditSenderService).send(any());
        mockMvc.perform(get("/client-subscriptions/{subscriptionId}", subscriptionId)
                        .header(AUTHORIZATION, AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk());
    }
}
