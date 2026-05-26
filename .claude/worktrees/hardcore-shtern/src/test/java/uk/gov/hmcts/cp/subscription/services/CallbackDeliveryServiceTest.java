package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.hmac.managers.HmacManager;
import uk.gov.hmcts.cp.openapi.model.EventNotificationPayload;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.mappers.NotificationMapper;
import uk.gov.hmcts.cp.subscription.mappers.SubscriberMapper;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;
import uk.gov.hmcts.cp.subscription.model.Subscriber;
import uk.gov.hmcts.cp.subscription.repositories.SubscriptionRepository;

import java.util.List;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallbackDeliveryServiceTest {
    @Mock
    ServiceBusConfigService serviceBusConfigService;
    @Mock
    JsonMapper jsonMapper;
    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    NotificationMapper notificationMapper;
    @Mock
    SubscriberMapper subscriberMapper;
    @Mock
    CallbackService callbackService;
    @Mock
    HmacManager hmacManager;

    @InjectMocks
    private CallbackDeliveryService callbackDeliveryService;

    private UUID documentId = randomUUID();
    private String callbackUrl = "https://callback.example.com";
    private UUID subscriptionId = randomUUID();
    private String hmacKeyId = "kid-v1";
    private ClientSubscriptionEntity subscriptionEntity = ClientSubscriptionEntity.builder().id(subscriptionId).build();
    private EventPayload eventPayload = EventPayload.builder().eventType("PRISON_COURT_REGISTER_GENERATED").build();
    private Subscriber subscriber = Subscriber.builder().hmacKeyId(hmacKeyId).notificationEndpoint(callbackUrl).build();
    private EventNotificationPayload payload = EventNotificationPayload.builder().build();
    private EventNotificationPayloadWrapper payloadWrapper = EventNotificationPayloadWrapper.builder().build();

    @Test
    void submit_should_send_when_servicebus_disabled() {
        when(subscriptionRepository.findByEventType("PRISON_COURT_REGISTER_GENERATED")).thenReturn(List.of(subscriptionEntity));
        when(notificationMapper.mapToPayload(documentId, eventPayload)).thenReturn(payload);
        when(subscriberMapper.toSubscriber(subscriptionEntity)).thenReturn(subscriber);
        when(jsonMapper.toJson(payload)).thenReturn("{payload}");
        when(hmacManager.getSignature(hmacKeyId, "{payload}")).thenReturn("signature");
        when(notificationMapper.mapToWrapper(payload, hmacKeyId, "signature")).thenReturn(payloadWrapper);

        callbackDeliveryService.submitOutboundPcrEvents(eventPayload, documentId);

        verify(callbackService).sendToSubscriber(callbackUrl, payloadWrapper);
    }

    @Test
    void submit_should_skip_when_example_endpoint() {
        when(subscriptionRepository.findByEventType("PRISON_COURT_REGISTER_GENERATED")).thenReturn(List.of(subscriptionEntity));
        when(notificationMapper.mapToPayload(documentId, eventPayload)).thenReturn(payload);
        String exampleCallbackUrl = "https://example.com/demo";
        Subscriber exampleSubscriber = Subscriber.builder().hmacKeyId(hmacKeyId).notificationEndpoint(exampleCallbackUrl).build();
        when(subscriberMapper.toSubscriber(subscriptionEntity)).thenReturn(exampleSubscriber);
        when(jsonMapper.toJson(payload)).thenReturn("{payload}");
        when(hmacManager.getSignature(hmacKeyId, "{payload}")).thenReturn("signature");
        when(notificationMapper.mapToWrapper(payload, hmacKeyId, "signature")).thenReturn(payloadWrapper);

        callbackDeliveryService.submitOutboundPcrEvents(eventPayload, documentId);

        verify(callbackService, never()).sendToSubscriber(anyString(), any(EventNotificationPayloadWrapper.class));
    }
}