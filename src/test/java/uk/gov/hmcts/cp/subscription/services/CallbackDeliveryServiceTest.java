package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.hmac.managers.HmacManager;
import uk.gov.hmcts.cp.openapi.model.EventNotificationPayload;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientHmacEntity;
import uk.gov.hmcts.cp.subscription.mappers.NotificationMapper;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;
import uk.gov.hmcts.cp.subscription.repositories.ClientHmacRepository;
import uk.gov.hmcts.cp.subscription.repositories.ClientEventRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;
import static uk.gov.hmcts.cp.subscription.services.CallbackDeliveryService.EXAMPLE_ENDPOINT;

@ExtendWith(MockitoExtension.class)
class CallbackDeliveryServiceTest {

    @Mock
    JsonMapper jsonMapper;
    @Mock
    ClientEventRepository clientEventRepository;
    @Mock
    ClientHmacRepository clientHmacRepository;
    @Mock
    NotificationMapper notificationMapper;
    @Mock
    HmacManager hmacManager;
    @Mock
    ServiceBusClientService serviceBusClientService;
    @Mock
    HearingEventService hearingEventService;

    private CallbackDeliveryService callbackDeliveryService;
    private CallbackDeliveryService toggleOnService;

    private final UUID documentId = randomUUID();
    private final String callbackUrl = "https://callback.example.com";
    private final UUID subscriptionId = randomUUID();
    private final String hmacKeyId = "kid-v1";
    private final UUID eventId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private final ClientEntity clientEntity = ClientEntity.builder().subscriptionId(subscriptionId).callbackUrl(callbackUrl).build();
    private final ClientHmacEntity clientHmacEntity = ClientHmacEntity.builder().keyId(hmacKeyId).build();
    private final EventPayload eventPayload = EventPayload.builder()
            .eventId(eventId)
            .eventType("PRISON_COURT_REGISTER_GENERATED")
            .build();
    private final EventNotificationPayload payload = EventNotificationPayload.builder().build();
    private final EventNotificationPayloadWrapper payloadWrapper = EventNotificationPayloadWrapper.builder().build();

    @BeforeEach
    void setUp() {
        callbackDeliveryService = new CallbackDeliveryService(
                clientEventRepository, clientHmacRepository, notificationMapper, jsonMapper,
                serviceBusClientService, hmacManager, hearingEventService, false);
        toggleOnService = new CallbackDeliveryService(
                clientEventRepository, clientHmacRepository, notificationMapper, jsonMapper,
                serviceBusClientService, hmacManager, hearingEventService, true);
    }

    @Test
    void submit_should_queue_to_service_bus() {
        when(clientEventRepository.findClientsByEventType("PRISON_COURT_REGISTER_GENERATED")).thenReturn(List.of(clientEntity));
        when(notificationMapper.mapToPayload(documentId, eventPayload)).thenReturn(payload);
        when(clientHmacRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.of(clientHmacEntity));
        when(jsonMapper.toJson(payload)).thenReturn("{payload}");
        when(jsonMapper.toJson(payloadWrapper)).thenReturn("{payload-wrapper}");
        when(hmacManager.calculateSignature(hmacKeyId, "{payload}")).thenReturn("signature");
        when(notificationMapper.mapToWrapper(payload, hmacKeyId, "signature")).thenReturn(payloadWrapper);

        callbackDeliveryService.submitOutboundEvents(eventPayload, documentId);

        verify(serviceBusClientService).queueMessage(NOTIFICATIONS_OUTBOUND_QUEUE, callbackUrl, "{payload-wrapper}", 0);
        verify(hearingEventService, never()).saveIfAbsent(any());
        verify(hearingEventService, never()).saveSubscriptionIfAbsent(any(), any());
    }

    @Test
    void submit_should_skip_when_example_endpoint() {
        ClientEntity clientWithExample = ClientEntity.builder().subscriptionId(subscriptionId).callbackUrl(EXAMPLE_ENDPOINT).build();
        when(clientEventRepository.findClientsByEventType("PRISON_COURT_REGISTER_GENERATED")).thenReturn(List.of(clientWithExample));
        when(notificationMapper.mapToPayload(documentId, eventPayload)).thenReturn(payload);
        when(clientHmacRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.of(clientHmacEntity));
        when(jsonMapper.toJson(payload)).thenReturn("{payload}");
        when(hmacManager.calculateSignature(hmacKeyId, "{payload}")).thenReturn("signature");
        when(notificationMapper.mapToWrapper(payload, hmacKeyId, "signature")).thenReturn(payloadWrapper);

        callbackDeliveryService.submitOutboundEvents(eventPayload, documentId);

        verify(serviceBusClientService, never()).queueMessage(anyString(), anyString(), anyString(), anyInt());
        verify(hearingEventService, never()).saveIfAbsent(any());
        verify(hearingEventService, never()).saveSubscriptionIfAbsent(any(), any());
    }

    @Test
    void submit_should_not_persist_when_toggle_off() {
        when(clientEventRepository.findClientsByEventType(anyString())).thenReturn(List.of());
        when(notificationMapper.mapToPayload(documentId, eventPayload)).thenReturn(payload);

        callbackDeliveryService.submitOutboundEvents(eventPayload, documentId);

        verify(hearingEventService, never()).saveIfAbsent(any());
        verify(hearingEventService, never()).saveSubscriptionIfAbsent(any(), any());
    }

    @Test
    void submit_should_call_saveIfAbsent_when_toggle_on() {
        when(clientEventRepository.findClientsByEventType(anyString())).thenReturn(List.of());
        when(notificationMapper.mapToPayload(documentId, eventPayload)).thenReturn(payload);
        when(hearingEventService.saveIfAbsent(eventPayload)).thenReturn(randomUUID());

        toggleOnService.submitOutboundEvents(eventPayload, documentId);

        verify(hearingEventService).saveIfAbsent(eventPayload);
        verify(hearingEventService, never()).saveSubscriptionIfAbsent(any(), any());
    }

    @Test
    void submit_should_not_call_saveSubscriptionIfAbsent_when_hearingEventId_is_null() {
        when(clientEventRepository.findClientsByEventType(anyString())).thenReturn(List.of(clientEntity));
        when(notificationMapper.mapToPayload(documentId, eventPayload)).thenReturn(payload);
        when(hearingEventService.saveIfAbsent(eventPayload)).thenReturn(null);
        when(clientHmacRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.of(clientHmacEntity));
        when(jsonMapper.toJson(payload)).thenReturn("{payload}");
        when(hmacManager.calculateSignature(hmacKeyId, "{payload}")).thenReturn("signature");
        when(notificationMapper.mapToWrapper(payload, hmacKeyId, "signature")).thenReturn(payloadWrapper);
        when(jsonMapper.toJson(payloadWrapper)).thenReturn("{payload-wrapper}");

        toggleOnService.submitOutboundEvents(eventPayload, documentId);

        verify(hearingEventService).saveIfAbsent(eventPayload);
        verify(hearingEventService, never()).saveSubscriptionIfAbsent(any(), any());
    }

    @Test
    void submit_should_call_saveSubscriptionIfAbsent_per_client_when_toggle_on() {
        UUID generatedHearingEventId = randomUUID();
        when(clientEventRepository.findClientsByEventType(anyString())).thenReturn(List.of(clientEntity));
        when(notificationMapper.mapToPayload(documentId, eventPayload)).thenReturn(payload);
        when(hearingEventService.saveIfAbsent(eventPayload)).thenReturn(generatedHearingEventId);
        when(clientHmacRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.of(clientHmacEntity));
        when(jsonMapper.toJson(payload)).thenReturn("{payload}");
        when(hmacManager.calculateSignature(hmacKeyId, "{payload}")).thenReturn("signature");
        when(notificationMapper.mapToWrapper(payload, hmacKeyId, "signature")).thenReturn(payloadWrapper);
        when(jsonMapper.toJson(payloadWrapper)).thenReturn("{payload-wrapper}");

        toggleOnService.submitOutboundEvents(eventPayload, documentId);

        verify(hearingEventService).saveIfAbsent(eventPayload);
        verify(hearingEventService).saveSubscriptionIfAbsent(eq(subscriptionId), eq(generatedHearingEventId));
    }
}