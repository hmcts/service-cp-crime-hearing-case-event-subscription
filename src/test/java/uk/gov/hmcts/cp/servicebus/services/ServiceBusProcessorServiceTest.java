package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import uk.gov.hmcts.cp.openapi.model.EventNotificationPayload;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;
import uk.gov.hmcts.cp.servicebus.mapper.ServiceBusMapper;
import uk.gov.hmcts.cp.servicebus.model.ServiceBusWrappedMessage;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;

@ExtendWith(MockitoExtension.class)
class ServiceBusProcessorServiceTest {
    @Mock
    JsonMapper jsonMapper = new JsonMapper();
    @Mock
    ServiceBusAdminService adminService;
    @Mock
    ServiceBusMapper serviceBusMapper;
    @Mock
    ServiceBusProperties properties;
    @Mock
    ServiceBusClientFactory clientFactory;
    @Mock
    ServiceBusClientService serviceBusClientService;
    @Mock
    ServiceBusHandlers serviceBusHandlers;

    @InjectMocks
    ServiceBusProcessorService serviceBusProcessorService;

    @Mock
    BinaryData binaryData;
    @Mock
    ServiceBusReceivedMessage serviceBusReceivedMessage;

    @Mock
    ServiceBusReceivedMessageContext context;
    @Mock
    ServiceBusWrappedMessage wrappedMessage;

    private String callbackUrl = "http://callback";

    @Test
    void initialise_should_wait_60_secs_for_service_bus() {
        // its tricky to mock the buildProcessClient so we need to rely on our integrations tests here
        // And actually we only use stop for int tests so that we can cleanup so we dont need unit test
        // i.e. struggle with when(clientBuilder.processMessage(any(Consumer.class))).thenReturn(clientBuilder);
    }

    @Test
    void stop_and_start_should_work_ok() {
        // its tricky to mock the buildProcessClient so we need to rely on our integrations tests here
        // And actually we only use stop for int tests so that we can cleanup so we dont need unit test
    }

    @Test
    void handle_message_should_pass_to_callback_client_with_success() {
        when(wrappedMessage.getCorrelationId()).thenReturn(UUID.randomUUID());
        when(context.getMessage()).thenReturn(serviceBusReceivedMessage);
        when(serviceBusReceivedMessage.getBody()).thenReturn(binaryData);
        when(jsonMapper.fromJson("binaryData", ServiceBusWrappedMessage.class)).thenReturn(wrappedMessage);
        when(wrappedMessage.getMessage()).thenReturn("message");
        when(wrappedMessage.getTargetUrl()).thenReturn(callbackUrl);

        serviceBusProcessorService.handleMessage(NOTIFICATIONS_OUTBOUND_QUEUE, context);

        verify(serviceBusHandlers).handleMessage(NOTIFICATIONS_OUTBOUND_QUEUE, callbackUrl, "message");
    }

    @Test
    void handle_message_should_requeue_if_handler_errors() {
        EventNotificationPayload notificationPayload = EventNotificationPayload.builder().build();
        when(wrappedMessage.getCorrelationId()).thenReturn(UUID.randomUUID());
        when(context.getMessage()).thenReturn(serviceBusReceivedMessage);
        when(serviceBusReceivedMessage.getBody()).thenReturn(binaryData);
        when(jsonMapper.fromJson("binaryData", ServiceBusWrappedMessage.class)).thenReturn(wrappedMessage);
        when(wrappedMessage.getMessage()).thenReturn("wrapped-message");
        when(jsonMapper.fromJson("wrapped-message", EventNotificationPayload.class)).thenReturn(notificationPayload);
        when(wrappedMessage.getTargetUrl()).thenReturn(callbackUrl);
        doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).when(serviceBusHandlers)
                .handleMessage(NOTIFICATIONS_OUTBOUND_QUEUE, callbackUrl, "message");

        when(properties.getMaxTries()).thenReturn(2);

        serviceBusProcessorService.handleMessage(NOTIFICATIONS_OUTBOUND_QUEUE, context);

        verify(serviceBusClientService).queueMessage(NOTIFICATIONS_OUTBOUND_QUEUE, callbackUrl, "wrapped-message", 1);
    }

    @Test
    void handle_message_should_error_if_callback_client_errors_finally() {
        when(wrappedMessage.getCorrelationId()).thenReturn(UUID.randomUUID());
        when(context.getMessage()).thenReturn(serviceBusReceivedMessage);
        when(serviceBusReceivedMessage.getBody()).thenReturn(binaryData);
        when(jsonMapper.fromJson("binaryData", ServiceBusWrappedMessage.class)).thenReturn(wrappedMessage);
        when(wrappedMessage.getMessage()).thenReturn("message");
        when(wrappedMessage.getTargetUrl()).thenReturn(callbackUrl);
        doThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).when(serviceBusHandlers)
                .handleMessage(NOTIFICATIONS_OUTBOUND_QUEUE, callbackUrl, "message");
        when(properties.getMaxTries()).thenReturn(1);

        assertThrows(HttpServerErrorException.class, () -> serviceBusProcessorService.handleMessage(NOTIFICATIONS_OUTBOUND_QUEUE, context));
    }
}