package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.QueueRuntimeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueueSizeLogger {

    private final ServiceBusAdministrationClient administrationClient;

    @Scheduled(cron = "${queue-size.log.cron}")
    public void logQueueSizes() {
        logQueueSize(NOTIFICATIONS_INBOUND_QUEUE);
        logQueueSize(NOTIFICATIONS_OUTBOUND_QUEUE);
    }

    private void logQueueSize(final String queueName) {
        final QueueRuntimeProperties props = administrationClient.getQueueRuntimeProperties(queueName);
        log.info("queueSize queue:{} activeMessages:{} scheduledMessages:{} deadLetterMessages:{}",
            queueName,
            props.getActiveMessageCount(),
            props.getScheduledMessageCount(),
            props.getDeadLetterMessageCount());
    }
}
