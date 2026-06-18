package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.servicebus.mapper.ServiceBusMapper;
import uk.gov.hmcts.cp.servicebus.mapper.ServiceBusWrapperMapper;
import uk.gov.hmcts.cp.servicebus.model.ServiceBusWrappedMessage;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;

@Service
@AllArgsConstructor
@Slf4j
public class ServiceBusClientService {

    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_EMPTY_RECEIVES = 3;

    private final ServiceBusClientFactory clientFactory;
    private final ServiceBusWrapperMapper wrapperMapper;
    private final ServiceBusMapper mapper;
    private final JsonMapper jsonMapper;
    private final ServiceBusRetryService retryService;

    public void queueMessage(final String queueName, final String targetUrl, final String messageString, final int failureCount) {
        final ServiceBusSenderClient serviceBusSenderClient = clientFactory.senderClient(queueName);
        final UUID correlationId = UUID.fromString(MDC.get(CORRELATION_ID_KEY));
        final ServiceBusWrappedMessage wrappedMessage = wrapperMapper.newWrapper(correlationId, failureCount, targetUrl, messageString);
        final OffsetDateTime nextTryTime = retryService.getNextTryTime(failureCount);
        final ServiceBusMessage serviceBusMessage = mapper.newMessage(jsonMapper.toJson(wrappedMessage), nextTryTime);
        log.info("Sending message to queue {} ...", queueName);
        serviceBusSenderClient.sendMessage(serviceBusMessage);
        serviceBusSenderClient.close();
        log.info("Sent message to queue:{} with failCount:{} nextTryTime:{}", queueName, failureCount, nextTryTime);
    }

    public int clearDeadLetterQueue(final String queueName, final int olderThanDays) {
        final int count = doClearDeadLetterQueue(queueName, olderThanDays);
        log.info("Cleared {} DLQ messages older than {} days from queue:{}", count, olderThanDays, queueName);
        return count;
    }

    private int doClearDeadLetterQueue(final String queueName, final int olderThanDays) {
        final OffsetDateTime cutoff = OffsetDateTime.now().minusDays(olderThanDays);
        final Set<Long> skipped = new HashSet<>();
        int count = 0;
        try (ServiceBusReceiverClient receiver = clientFactory.deadLetterReceiverClient(queueName)) {
            ServiceBusReceivedMessage message = nextMessage(receiver);
            while (message != null) {
                if (skipped.contains(message.getSequenceNumber())) {
                    receiver.abandon(message);
                    break;
                }
                final OffsetDateTime enqueuedAt = message.getEnqueuedTime();
                if (enqueuedAt.isBefore(cutoff)) {
                    log.info("Clearing DLQ message id:{} enqueuedAt:{} from queue:{}", message.getMessageId(), enqueuedAt, queueName);
                    receiver.complete(message);
                    count++;
                } else {
                    receiver.abandon(message);
                    skipped.add(message.getSequenceNumber());
                }
                message = nextMessage(receiver);
            }
        }
        return count;
    }

    /**
     * Receives the next dead-letter message, retrying on empty receives. The first receive often
     * times out while the AMQP link is still being established, so a single empty result does not
     * mean the queue is drained — only give up after {@link #MAX_EMPTY_RECEIVES} empty receives.
     */
    private ServiceBusReceivedMessage nextMessage(final ServiceBusReceiverClient receiver) {
        for (int attempt = 0; attempt < MAX_EMPTY_RECEIVES; attempt++) {
            final Iterator<ServiceBusReceivedMessage> it = receiver.receiveMessages(1, RECEIVE_TIMEOUT).iterator();
            if (it.hasNext()) {
                return it.next();
            }
        }
        return null;
    }
}