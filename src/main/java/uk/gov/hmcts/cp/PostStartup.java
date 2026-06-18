package uk.gov.hmcts.cp;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.QueueRuntimeProperties;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;

import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;

@Slf4j
@Service
@AllArgsConstructor
public class PostStartup {
    private EventTypeRepository eventTypeRepository;
    private DocumentMappingRepository documentMappingRepository;
    private ServiceBusAdministrationClient administrationClient;

    @PostConstruct
    public void postStartupLogging() {
        log.info("PostStartup Database contains {} eventTypes", eventTypeRepository.count());
        logDocumentMappingCount();
        logDeadLetterQueueSizes();
    }

    private void logDeadLetterQueueSizes() {
        logDeadLetterCount(NOTIFICATIONS_INBOUND_QUEUE);
        logDeadLetterCount(NOTIFICATIONS_OUTBOUND_QUEUE);
    }

    private void logDeadLetterCount(final String queueName) {
        try {
            final QueueRuntimeProperties runtimeProperties = administrationClient.getQueueRuntimeProperties(queueName);
            log.info("PostStartup Queue {} deadLetterMessageCount:{} activeMessageCount:{}",
                    queueName, runtimeProperties.getDeadLetterMessageCount(), runtimeProperties.getActiveMessageCount());
        } catch (Exception e) {
            log.warn("PostStartup Failed to get dead letter count for queue {}: {}", queueName, e.getMessage());
        }
    }

    private void logDocumentMappingCount() {
        log.info("PostStartup Database contains {} documents", documentMappingRepository.count());
    }
}
