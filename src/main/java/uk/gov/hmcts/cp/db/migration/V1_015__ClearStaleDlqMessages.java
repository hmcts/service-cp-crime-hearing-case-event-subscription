package uk.gov.hmcts.cp.db.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;

import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;

/**
 * Flyway Java migration — runs exactly once at startup, tracked in flyway_schema_history.
 *
 * Annotating with @Component means Spring Boot automatically registers this as a
 * JavaMigration bean. Flyway picks it up from the Spring context, so full constructor
 * injection is available here just like any other Spring bean.
 *
 * Flyway's distributed lock ensures only one pod runs this even when multiple instances
 * start simultaneously.
 *
 * ServiceBusClientService is safe to inject here — its dependency chain contains no
 * JPA repositories or Flyway beans, so there is no circular dependency risk.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class V1_015__ClearStaleDlqMessages extends BaseJavaMigration {

    private static final int STALE_THRESHOLD_DAYS = 7;

    private final ServiceBusClientService serviceBusClientService;

    @Override
    public void migrate(final Context context) {
        log.info("Running startup maintenance: clearing DLQ messages older than {} days from queue:{}",
                STALE_THRESHOLD_DAYS, NOTIFICATIONS_INBOUND_QUEUE);
        final int cleared = serviceBusClientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, STALE_THRESHOLD_DAYS);
        log.info("Startup maintenance complete: cleared {} stale DLQ messages", cleared);
    }
}
