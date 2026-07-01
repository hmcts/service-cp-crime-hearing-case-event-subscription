package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.QueueRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;

@ExtendWith(MockitoExtension.class)
class QueueSizeLoggerTest {

    @Mock
    ServiceBusAdministrationClient administrationClient;

    @InjectMocks
    QueueSizeLogger queueSizeLogger;

    @Test
    void logging_queue_sizes_should_query_runtime_properties_for_each_queue() {
        final QueueRuntimeProperties inboundProps = mock(QueueRuntimeProperties.class);
        final QueueRuntimeProperties outboundProps = mock(QueueRuntimeProperties.class);
        when(administrationClient.getQueueRuntimeProperties(NOTIFICATIONS_INBOUND_QUEUE)).thenReturn(inboundProps);
        when(administrationClient.getQueueRuntimeProperties(NOTIFICATIONS_OUTBOUND_QUEUE)).thenReturn(outboundProps);

        queueSizeLogger.logQueueSizes();

        verify(administrationClient).getQueueRuntimeProperties(NOTIFICATIONS_INBOUND_QUEUE);
        verify(administrationClient).getQueueRuntimeProperties(NOTIFICATIONS_OUTBOUND_QUEUE);
    }
}
