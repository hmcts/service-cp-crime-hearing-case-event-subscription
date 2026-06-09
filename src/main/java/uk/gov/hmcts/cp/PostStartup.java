package uk.gov.hmcts.cp;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.QueueRuntimeProperties;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.config.EnvironmentName;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;
import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.DEV;
import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.PRD;
import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.PRP;
import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.SIT;

/**
 * We can add any debug logging we might need such as database checks
 */
@Slf4j
@Service
@AllArgsConstructor
public class PostStartup {
    private AppProperties appProperties;
    private ServiceBusClientService serviceBusClientService;
    private EventTypeRepository eventTypeRepository;
    private DocumentMappingRepository documentMappingRepository;
    private ServiceBusAdministrationClient administrationClient;

    // Temporary: May 2026 DLQ purge for PRD/PRP — remove after cleanup is validated
    public static final OffsetDateTime MAY_2026_START = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    public static final OffsetDateTime MAY_2026_END = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @PostConstruct
    public void postStartupLogging() {
        log.info("PostStartup Database contains {} eventTypes", eventTypeRepository.count());
        logRecentDocumentMappings();
        clearDlqIfRequired();
        logDeadLetterQueueSizes();
    }

    private void clearDlqIfRequired() {
        final EnvironmentName env = appProperties.getEnvironmentName();
        if (env == DEV || env == SIT) {
            log.info("PostStartup clearing all DLQ messages for environment:{}", env);
            serviceBusClientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, 0);
            serviceBusClientService.clearDeadLetterQueue(NOTIFICATIONS_OUTBOUND_QUEUE, 0);
        } else if (env == PRD || env == PRP) {
            log.info("PostStartup clearing May 2026 DLQ messages for environment:{}", env);
            serviceBusClientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, MAY_2026_START, MAY_2026_END);
            serviceBusClientService.clearDeadLetterQueue(NOTIFICATIONS_OUTBOUND_QUEUE, MAY_2026_START, MAY_2026_END);
        }
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

    private void logRecentDocumentMappings() {
        log.info("PostStartup Database contains {} document mappings", documentMappingRepository.count());
        final OffsetDateTime lastMonthDate = OffsetDateTime.now().minusMonths(1);
        documentMappingRepository.findAll().stream().filter(d -> d.getCreatedAt().isAfter(lastMonthDate)).forEach(
                d -> log.info("PostStartup Database contains documentId:{} materialId:{} createdAt:{}", d.getDocumentId(), d.getMaterialId(), d.getCreatedAt())
        );
    }
}