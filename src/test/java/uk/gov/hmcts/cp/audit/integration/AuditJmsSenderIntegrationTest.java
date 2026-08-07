package uk.gov.hmcts.cp.audit.integration;

import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.audit.service.AuditClockService;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "cp.audit.enabled=true")
class AuditJmsSenderIntegrationTest extends IntegrationTestBase {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T10:00:00Z");
    private static final String AUDIT_TOPIC = "jms.topic.auditing.event";

    @MockitoBean
    JmsTemplate jmsTemplate;

    @MockitoBean(name = "auditClockService")
    AuditClockService auditClockService;

    @BeforeEach
    void setUp() {
        clearAllTables();
        when(auditClockService.now()).thenReturn(FIXED_TIME);
    }

    @Test
    void sending_audit_event_should_set_cppname_jms_property_to_audit_recorded() throws Exception {
        final UUID subscriptionId = insertSubscription("https://callback", List.of("PRISON_COURT_REGISTER_GENERATED"));

        mockMvc.perform(get("/client-subscriptions/{subscriptionId}", subscriptionId)
                        .header(AUTHORIZATION, AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk());

        final ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(jmsTemplate, atLeastOnce()).convertAndSend(eq(AUDIT_TOPIC), anyString(), postProcessorCaptor.capture());

        final Message mockMessage = mock(Message.class);
        postProcessorCaptor.getValue().postProcessMessage(mockMessage);
        verify(mockMessage).setStringProperty("CPPNAME", "audit.events.audit-recorded");
    }
}
