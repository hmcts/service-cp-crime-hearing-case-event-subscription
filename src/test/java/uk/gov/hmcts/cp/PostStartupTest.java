package uk.gov.hmcts.cp;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.QueueRuntimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.PostStartup.MAY_2026_END;
import static uk.gov.hmcts.cp.PostStartup.MAY_2026_START;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;
import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.DEV;
import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.PRD;
import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.SIT;

@ExtendWith(MockitoExtension.class)
class PostStartupTest {

    @Mock
    AppProperties appProperties;
    @Mock
    ServiceBusClientService serviceBusClientService;
    @Mock
    DocumentMappingRepository documentMappingRepository;
    @Mock
    EventTypeRepository eventTypeRepository;
    @Mock
    ServiceBusAdministrationClient administrationClient;
    @Mock
    QueueRuntimeProperties queueRuntimeProperties;

    @InjectMocks
    PostStartup postStartup;

    @BeforeEach
    void setUp() {
        when(administrationClient.getQueueRuntimeProperties(NOTIFICATIONS_INBOUND_QUEUE)).thenReturn(queueRuntimeProperties);
        when(administrationClient.getQueueRuntimeProperties(NOTIFICATIONS_OUTBOUND_QUEUE)).thenReturn(queueRuntimeProperties);
    }

    @Test
    void post_startup_should_log_event_type_count() {
        when(appProperties.getEnvironmentName()).thenReturn(SIT);
        postStartup.postStartupLogging();
        verify(eventTypeRepository).count();
    }

    @Test
    void post_startup_should_log_document_mappings() {
        when(appProperties.getEnvironmentName()).thenReturn(SIT);
        postStartup.postStartupLogging();
        verify(documentMappingRepository).findAll();
    }

    @Test
    void post_startup_should_log_dead_letter_queue_sizes() {
        when(appProperties.getEnvironmentName()).thenReturn(SIT);
        postStartup.postStartupLogging();
        verify(administrationClient).getQueueRuntimeProperties(ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE);
        verify(administrationClient).getQueueRuntimeProperties(ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE);
    }

    @Test
    void post_startup_should_clear_all_dlq_for_dev_environment() {
        when(appProperties.getEnvironmentName()).thenReturn(DEV);

        postStartup.postStartupLogging();

        verify(serviceBusClientService).clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, 0);
        verify(serviceBusClientService).clearDeadLetterQueue(NOTIFICATIONS_OUTBOUND_QUEUE, 0);
    }

    @Test
    void post_startup_should_clear_may_2026_dlq_for_prd_environment() {
        final OffsetDateTime expectedFrom = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        final OffsetDateTime expectedTo = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        when(appProperties.getEnvironmentName()).thenReturn(PRD);

        postStartup.postStartupLogging();

        verify(serviceBusClientService).clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, expectedFrom, expectedTo);
        verify(serviceBusClientService).clearDeadLetterQueue(NOTIFICATIONS_OUTBOUND_QUEUE, expectedFrom, expectedTo);
    }

    @Test
    void post_startup_should_clear_month_of_may_dlqs() {
        when(appProperties.getEnvironmentName()).thenReturn(PRD);

        postStartup.postStartupLogging();

        verify(serviceBusClientService).clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, MAY_2026_START, MAY_2026_END);
        verify(serviceBusClientService).clearDeadLetterQueue(NOTIFICATIONS_OUTBOUND_QUEUE, MAY_2026_START, MAY_2026_END);
    }
}