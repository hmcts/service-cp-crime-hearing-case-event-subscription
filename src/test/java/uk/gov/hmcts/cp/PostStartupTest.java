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
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;

@ExtendWith(MockitoExtension.class)
class PostStartupTest {

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
        postStartup.postStartupLogging();
        verify(eventTypeRepository).count();
    }

    @Test
    void post_startup_should_log_document_count_without_listing_documents() {
        postStartup.postStartupLogging();
        verify(documentMappingRepository).count();
        verify(documentMappingRepository, never()).findAll();
    }

    @Test
    void post_startup_should_log_dead_letter_queue_sizes() {
        postStartup.postStartupLogging();
        verify(administrationClient).getQueueRuntimeProperties(ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE);
        verify(administrationClient).getQueueRuntimeProperties(ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE);
    }
}
