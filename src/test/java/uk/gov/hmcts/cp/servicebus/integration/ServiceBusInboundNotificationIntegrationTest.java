package uk.gov.hmcts.cp.servicebus.integration;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.openapi.model.EventPayloadDefendant;
import uk.gov.hmcts.cp.openapi.model.EventPayloadDefendantCustodyEstablishmentDetails;
import uk.gov.hmcts.cp.subscription.integration.config.TestContainersInitialise;
import uk.gov.hmcts.cp.subscription.model.MaterialMetadata;
import uk.gov.hmcts.cp.subscription.services.MaterialService;

import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;

@Slf4j
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@TestPropertySource(properties = {
        "vault.enabled=false",
        "service-bus.max-tries=2",
        "service-bus.retry-msecs=0"
})
public class ServiceBusInboundNotificationIntegrationTest extends ServiceBusIntegrationTestBase {

    @MockitoBean
    MaterialService materialService;

    UUID materialId = UUID.fromString("570e1a66-1341-474c-92b2-b7e89e0a524a");

    @BeforeEach
    void setUp() {
        prepareQueue(NOTIFICATIONS_INBOUND_QUEUE);
    }

    @AfterEach
    void afterEach() {
        processorService.stopMessageProcessor(NOTIFICATIONS_INBOUND_QUEUE);
    }

    @SneakyThrows
    @Test
    void inbound_notification_should_process_material_service() {
        MaterialMetadata materialMetadata = materialMetadata(materialId);
        when(materialService.getMaterialMetadata(materialId)).thenReturn(materialMetadata);
        MDC.put(CORRELATION_ID_KEY, UUID.randomUUID().toString());
        clientService.queueMessage(NOTIFICATIONS_INBOUND_QUEUE, null, jsonMapper.toJson(eventPayload()), 0);
        MDC.clear();

        Thread.sleep(5000);
        verify(materialService).getMaterialMetadata(materialId);
    }

    @SneakyThrows
    @Test
    void process_message_should_retry_n_times_then_send_to_DLQ() {
        when(materialService.getMaterialMetadata(materialId)).thenReturn(null);
        EventPayload eventPayload = EventPayload.builder().eventType("PRISON_COURT_REGISTER_GENERATED").materialId(materialId).build();
        MDC.put(CORRELATION_ID_KEY, UUID.randomUUID().toString());
        clientService.queueMessage(NOTIFICATIONS_INBOUND_QUEUE, null, jsonMapper.toJson(eventPayload), 0);
        MDC.clear();

        Thread.sleep(5000);
        verify(materialService, times(2)).getMaterialMetadata(materialId);
    }

    // There are not nulls on EventPayload so we need to populate it quite fully
    EventPayload eventPayload() {
        EventPayloadDefendantCustodyEstablishmentDetails custodyEstablishmentDetails =
                EventPayloadDefendantCustodyEstablishmentDetails.builder().build();
        EventPayloadDefendant defendant = EventPayloadDefendant.builder()
                .custodyEstablishmentDetails(custodyEstablishmentDetails)
                .build();
        return EventPayload.builder()
                .eventType("PRISON_COURT_REGISTER_GENERATED")
                .materialId(materialId)
                .defendant(defendant)
                .build();
    }

    MaterialMetadata materialMetadata(UUID materialId) {
        return MaterialMetadata.builder()
                .materialId(materialId)
                .build();
    }
}
