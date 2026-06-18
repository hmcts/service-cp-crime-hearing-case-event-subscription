package uk.gov.hmcts.cp.servicebus.integration;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;
import uk.gov.hmcts.cp.servicebus.integration.config.ServiceBusAvailabilityCheck;
import uk.gov.hmcts.cp.subscription.integration.config.PostgresInitialise;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;

@Slf4j
@ContextConfiguration(initializers = {PostgresInitialise.class, ServiceBusAvailabilityCheck.class})
// Matches the property set of the other Service Bus integration tests so this class reuses their
// cached Spring context rather than spinning up a new one (avoids exhausting PostgreSQL connections).
@TestPropertySource(properties = {
        "vault.enabled=false",
        "service-bus.max-tries=2",
        "service-bus.retry-durations=0s"
})
class DeadLetterQueueClearIntegrationTest extends ServiceBusIntegrationTestBase {

    private static final int SEEDED_MESSAGES = 5;

    @Autowired
    ServiceBusProperties serviceBusProperties;

    @BeforeEach
    void setUp() {
        processorService.stopMessageProcessor(NOTIFICATIONS_INBOUND_QUEUE);
        testService.dropQueueIfExists(NOTIFICATIONS_INBOUND_QUEUE);
        adminService.createQueue(NOTIFICATIONS_INBOUND_QUEUE);
    }

    @AfterEach
    void tearDown() {
        testService.dropQueueIfExists(NOTIFICATIONS_INBOUND_QUEUE);
    }

    @Test
    void clear_should_drain_dead_lettered_messages_against_a_real_broker() {
        givenDeadLetteredMessages(SEEDED_MESSAGES);

        final int cleared = clientService.clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, 0);

        assertThat(cleared).isEqualTo(SEEDED_MESSAGES);
    }

    private void givenDeadLetteredMessages(final int count) {
        try (ServiceBusSenderClient sender = new ServiceBusClientBuilder()
                .connectionString(serviceBusProperties.getConnectionString())
                .sender().queueName(NOTIFICATIONS_INBOUND_QUEUE).buildClient()) {
            for (int i = 0; i < count; i++) {
                sender.sendMessage(new ServiceBusMessage("dead-letter-me-" + i));
            }
        }
        try (ServiceBusReceiverClient receiver = new ServiceBusClientBuilder()
                .connectionString(serviceBusProperties.getConnectionString())
                .receiver().queueName(NOTIFICATIONS_INBOUND_QUEUE).buildClient()) {
            receiver.receiveMessages(count, Duration.ofSeconds(10))
                    .forEach(message -> receiver.deadLetter(message));
        }
    }
}
