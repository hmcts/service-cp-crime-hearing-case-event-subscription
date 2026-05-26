package uk.gov.hmcts.cp.servicebus.integration;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.subscription.clients.CallbackClient;
import uk.gov.hmcts.cp.subscription.integration.config.TestContainersInitialise;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_OUTBOUND_QUEUE;

@Slf4j
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@TestPropertySource(properties = {
        "vault.enabled=false",
        "service-bus.max-tries=2",
        "service-bus.retry-msecs=0"
})
@ExtendWith(MockitoExtension.class)
public class ServiceBusPcrOutboundIntegrationTest extends ServiceBusIntegrationTestBase {

    @MockitoBean
    CallbackClient callbackClient;

    @Captor
    ArgumentCaptor<EventNotificationPayloadWrapper> captor;

    @BeforeEach
    void setUp() {
        assumeTrue(adminService.isServiceBusReady(),
                "ServiceBus is not running. Run gradlew composeUp / composeDown");
        processorService.stopMessageProcessor(PCR_OUTBOUND_QUEUE);
        testService.dropQueueIfExists(PCR_OUTBOUND_QUEUE);

        adminService.createQueue(PCR_OUTBOUND_QUEUE);
        processorService.startMessageProcessor(PCR_OUTBOUND_QUEUE);
    }

    @AfterEach
    void afterEach() {
        processorService.stopMessageProcessor(PCR_OUTBOUND_QUEUE);
    }

    @SneakyThrows
    @Test
    void multiple_messages_should_process_and_send_to_client() {
        // We would like to run multiple processors but we need to run multiple apps to do that
        // See api-test for this
        for (int n = 0; n < 10; n++) {
            String callbackUrl = String.format("http://callback%d", n);
            queueMessageForCallbackUrl(callbackUrl);
        }
        Thread.sleep(2000);
        for (int n = 0; n < 10; n++) {
            String callbackUrl = String.format("http://callback%d", n);
            verify(callbackClient).sendNotification(eq(callbackUrl), captor.capture());
            assertCallbackPayload(captor.getValue());
        }
    }

    @SneakyThrows
    @Test
    void process_message_should_retry_n_times_then_stop_because_sent_to_DLQ() {
        doThrow(new RuntimeException("Error")).when(callbackClient).sendNotification(eq("http://callback"), any(EventNotificationPayloadWrapper.class));
        queueMessageForCallbackUrl("http://callback");
        Thread.sleep(4000);
        verify(callbackClient, times(2)).sendNotification(eq("http://callback"), any(EventNotificationPayloadWrapper.class));
    }

    private void queueMessageForCallbackUrl(String callbackUrl) {
        EventNotificationPayloadWrapper payload = EventNotificationPayloadWrapper.builder()
                .keyId("keyId")
                .signature("signature")
                .build();
        MDC.put(CORRELATION_ID_KEY, UUID.randomUUID().toString());
        clientService.queueMessage(PCR_OUTBOUND_QUEUE, callbackUrl, jsonMapper.toJson(payload), 0);
        MDC.clear();
    }

    private void assertCallbackPayload(EventNotificationPayloadWrapper payloadWrapper) {
        assertThat(payloadWrapper.getKeyId()).isEqualTo("keyId");
        assertThat(payloadWrapper.getSignature()).isEqualTo("signature");
    }
}
