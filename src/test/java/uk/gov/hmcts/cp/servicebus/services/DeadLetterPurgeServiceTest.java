package uk.gov.hmcts.cp.servicebus.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;

@ExtendWith(MockitoExtension.class)
class DeadLetterPurgeServiceTest {

    @Mock
    ServiceBusClientService serviceBusClientService;

    @InjectMocks
    DeadLetterPurgeService deadLetterPurgeService;

    @Test
    void purge_should_clear_both_queues_older_than_configured_retention() {
        ReflectionTestUtils.setField(deadLetterPurgeService, "retentionDays", 30);

        deadLetterPurgeService.purgeOldDeadLetters();

        verify(serviceBusClientService).clearDeadLetterQueue(NOTIFICATIONS_INBOUND_QUEUE, 30);
        verify(serviceBusClientService).clearDeadLetterQueue(NOTIFICATIONS_OUTBOUND_QUEUE, 30);
    }
}
