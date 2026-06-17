package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import uk.gov.hmcts.cp.servicebus.mapper.ServiceBusMapper;
import uk.gov.hmcts.cp.servicebus.mapper.ServiceBusWrapperMapper;
import uk.gov.hmcts.cp.servicebus.model.ServiceBusWrappedMessage;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;

@ExtendWith(MockitoExtension.class)
class ServiceBusClientServiceTest {

    @Mock
    ServiceBusClientFactory clientFactory;
    @Mock
    ServiceBusWrapperMapper wrapperMapper;
    @Mock
    ServiceBusRetryService retryService;
    @Mock
    ServiceBusMapper mapper;
    @Mock
    JsonMapper jsonMapper;

    @InjectMocks
    ServiceBusClientService clientService;

    @Mock
    ServiceBusSenderClient senderClient;
    @Mock
    ServiceBusReceiverClient receiverClient;

    OffsetDateTime nextTryTime = OffsetDateTime.now();
    String callbackUrl = "http://callback";
    ServiceBusWrappedMessage wrappedMessage = ServiceBusWrappedMessage.builder().build();
    ServiceBusMessage serviceBusMessage = new ServiceBusMessage("wrapped-Message");

    @Test
    void queue_message_should_pass_to_queue() {
        UUID correlationId = UUID.randomUUID();
        MDC.put(CORRELATION_ID_KEY, correlationId.toString());
        when(clientFactory.senderClient(NOTIFICATIONS_INBOUND_QUEUE)).thenReturn(senderClient);
        when(wrapperMapper.newWrapper(correlationId, 1, callbackUrl, "message")).thenReturn(wrappedMessage);
        when(jsonMapper.toJson(wrappedMessage)).thenReturn("wrapped-message");
        when(retryService.getNextTryTime(1)).thenReturn(nextTryTime);
        when(mapper.newMessage("wrapped-message", nextTryTime)).thenReturn(serviceBusMessage);

        clientService.queueMessage(NOTIFICATIONS_INBOUND_QUEUE, callbackUrl, "message", 1);

        verify(senderClient).sendMessage(serviceBusMessage);
        verify(senderClient).close();
        MDC.remove("correlationId");
    }

    @Test
    void clear_dead_letter_queue_should_complete_old_messages_and_return_count() {
        final ServiceBusReceivedMessage oldMessage = mockMessageEnqueuedAt(OffsetDateTime.now().minusDays(10), 1L);
        when(clientFactory.deadLetterReceiverClient(NOTIFICATIONS_INBOUND_QUEUE)).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any()))
            .thenReturn(IterableStream.of(List.of(oldMessage)))
            .thenReturn(IterableStream.of(Collections.emptyList()));

        assertThat(clientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, 7)).isEqualTo(1);
        verify(receiverClient).complete(oldMessage);
        verify(receiverClient).close();
    }

    @Test
    void clear_dead_letter_queue_should_abandon_recent_messages_and_return_zero() {
        final ServiceBusReceivedMessage recentMessage = mockMessageEnqueuedAt(OffsetDateTime.now().minusDays(2), 1L);
        when(clientFactory.deadLetterReceiverClient(NOTIFICATIONS_INBOUND_QUEUE)).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any()))
            .thenReturn(IterableStream.of(List.of(recentMessage)))
            .thenReturn(IterableStream.of(Collections.emptyList()));

        assertThat(clientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, 7)).isZero();
        verify(receiverClient).abandon(recentMessage);
        verify(receiverClient).close();
    }

    @Test
    void clear_dead_letter_queue_should_stop_when_skipped_sequence_number_seen_again() {
        final ServiceBusReceivedMessage recentMessage = mockMessageEnqueuedAt(OffsetDateTime.now().minusDays(1), 99L);
        when(clientFactory.deadLetterReceiverClient(NOTIFICATIONS_INBOUND_QUEUE)).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any()))
            .thenReturn(IterableStream.of(List.of(recentMessage)))
            .thenReturn(IterableStream.of(List.of(recentMessage)));

        assertThat(clientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, 7)).isZero();
        verify(receiverClient).close();
    }

    @Test
    void clear_dead_letter_queue_should_drain_messages_that_arrive_after_an_initial_empty_receive() {
        // Reproduces prod: the DLQ may hold many of messages, but the FIRST receive returns empty
        // because the AMQP link is still being established (1s timeout fires first). The message is
        // delivered on the next poll. The clear must not give up on a single empty receive.
        final ServiceBusReceivedMessage oldMessage = mockMessageEnqueuedAt(OffsetDateTime.now().minusDays(10), 1L);
        when(clientFactory.deadLetterReceiverClient(NOTIFICATIONS_INBOUND_QUEUE)).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any()))
            .thenReturn(IterableStream.of(Collections.emptyList()))   // link not ready yet
            .thenReturn(IterableStream.of(List.of(oldMessage)))       // message arrives
            .thenReturn(IterableStream.of(Collections.emptyList()));  // drained

        assertThat(clientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, 0)).isEqualTo(1);
        verify(receiverClient).complete(oldMessage);
        verify(receiverClient).close();
    }

    @Test
    void clear_dead_letter_queue_should_return_zero_when_dlq_is_empty() {
        when(clientFactory.deadLetterReceiverClient(NOTIFICATIONS_INBOUND_QUEUE)).thenReturn(receiverClient);
        when(receiverClient.receiveMessages(anyInt(), any())).thenReturn(IterableStream.of(Collections.emptyList()));

        assertThat(clientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, 7)).isZero();
        verify(receiverClient).close();
    }

    private ServiceBusReceivedMessage mockMessageEnqueuedAt(final OffsetDateTime enqueuedAt, final long sequenceNumber) {
        final ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);
        when(message.getEnqueuedTime()).thenReturn(enqueuedAt);
        when(message.getSequenceNumber()).thenReturn(sequenceNumber);
        lenient().when(message.getMessageId()).thenReturn("msg-" + sequenceNumber);
        return message;
    }
}