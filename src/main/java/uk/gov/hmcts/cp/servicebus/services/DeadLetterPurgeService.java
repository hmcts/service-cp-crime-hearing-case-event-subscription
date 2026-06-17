package uk.gov.hmcts.cp.servicebus.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;

/**
 * Daily scheduled purge of dead-letter messages older than the configured retention, in every
 * environment.
 *
 * <p>Runs off the startup thread (not {@code @PostConstruct}) so a large drain can never block
 * application readiness/liveness probes. Messages younger than the retention are left in place.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeadLetterPurgeService {

    private final ServiceBusClientService serviceBusClientService;

    @Value("${dead-letter.purge.retention-days}")
    private int retentionDays;

    @Scheduled(cron = "${dead-letter.purge.cron}")
    public void purgeOldDeadLetters() {
        log.info("DeadLetterPurge starting — removing dead-letter messages older than {} days", retentionDays);
        final int inbound = serviceBusClientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, retentionDays);
        final int outbound = serviceBusClientService.clearDeadLetterQueue(NOTIFICATIONS_OUTBOUND_QUEUE, retentionDays);
        log.info("DeadLetterPurge removed {} inbound and {} outbound dead-letter messages older than {} days",
                inbound, outbound, retentionDays);
    }
}
