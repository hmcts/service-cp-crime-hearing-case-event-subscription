package uk.gov.hmcts.cp.servicebus.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.clients.CallbackClient;
import uk.gov.hmcts.cp.subscription.managers.NotificationManager;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_OUTBOUND_QUEUE;

@ExtendWith(MockitoExtension.class)
class ServiceBusHandlersTest {
    @Mock
    JsonMapper jsonMapper;
    @Mock
    NotificationManager notificationManager;
    @Mock
    CallbackClient callbackClient;

    @InjectMocks
    ServiceBusHandlers serviceBusHandlers;

    @Test
    void inbound_pcr_should_handle_ok() {
        EventPayload eventPayload = EventPayload.builder().build();
        when(jsonMapper.fromJson("pcr-json", EventPayload.class)).thenReturn(eventPayload);

        serviceBusHandlers.handleMessage(PCR_INBOUND_QUEUE, null, "pcr-json");

        verify(notificationManager).processPcrNotification(eventPayload);
    }

    @Test
    void outbound_callback_should_handle_ok() {
        EventNotificationPayloadWrapper payloadWrapper = EventNotificationPayloadWrapper.builder().build();
        when(jsonMapper.fromJson("callback-json", EventNotificationPayloadWrapper.class)).thenReturn(payloadWrapper);

        serviceBusHandlers.handleMessage(PCR_OUTBOUND_QUEUE, "https://callback", "callback-json");

        verify(callbackClient).sendNotification("https://callback", payloadWrapper);
    }

    @Test
    void unknown_queue_should_throw_error() {
        assertThrows(RuntimeException.class, () -> serviceBusHandlers.handleMessage("BAD-QUEUE", null, "callback-json"));
    }
}