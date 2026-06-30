package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.QueueRuntimeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueueSizeLogger {

    private final ServiceBusAdministrationClient administrationClient;

    @Scheduled(cron = "${queue-size.log.cron}")
    public void logQueueSizes() {
        administrationClient.listQueues().forEach(queue -> {
            final QueueRuntimeProperties props = administrationClient.getQueueRuntimeProperties(queue.getName());
            log.info("queueSize queue:{} activeMessages:{} deadLetterMessages:{}",
                props.getName(),
                props.getActiveMessageCount(),
                props.getDeadLetterMessageCount());
        });
    }
}
