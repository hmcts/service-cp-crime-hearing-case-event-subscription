package uk.gov.hmcts.cp.subscription.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.hmac.managers.HmacManager;
import uk.gov.hmcts.cp.openapi.model.EventNotificationPayload;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.mappers.NotificationMapper;
import uk.gov.hmcts.cp.subscription.mappers.SubscriberMapper;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;
import uk.gov.hmcts.cp.subscription.model.Subscriber;
import uk.gov.hmcts.cp.subscription.repositories.SubscriptionRepository;

import java.util.List;
import java.util.UUID;

import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_OUTBOUND_QUEUE;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackDeliveryService {

    private static final String EXAMPLE_ENDPOINT = "https://example.com";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriberMapper subscriberMapper;
    private final NotificationMapper notificationMapper;
    private final JsonMapper jsonMapper;
    private final ServiceBusConfigService serviceBusConfig;
    private final ServiceBusClientService clientService;
    private final CallbackService callbackService;
    private final HmacManager hmacManager;

    public void submitOutboundPcrEvents(final EventPayload eventPayload, final UUID documentId) {
        final String eventType = eventPayload.getEventType();
        final List<ClientSubscriptionEntity> entities = subscriptionRepository.findByEventType(eventType);
        final EventNotificationPayload eventNotificationPayload = notificationMapper.mapToPayload(documentId, eventPayload);
        log.info("sending {} outbound notifications", entities.size());
        for (final ClientSubscriptionEntity entity : entities) {
            final Subscriber subscriber = subscriberMapper.toSubscriber(entity);
            final String signature = hmacManager.getSignature(subscriber.getHmacKeyId(), jsonMapper.toJson(eventNotificationPayload));
            final EventNotificationPayloadWrapper payloadWrapper = notificationMapper.mapToWrapper(eventNotificationPayload, subscriber.getHmacKeyId(), signature);
            if (subscriber.getNotificationEndpoint().startsWith(EXAMPLE_ENDPOINT)) {
                log.info("Skipping notification for EXAMPLE callback endpoint:{}", subscriber.getNotificationEndpoint());
            } else if (serviceBusConfig.isEnabled()) {
                final String payload = jsonMapper.toJson(payloadWrapper);
                clientService.queueMessage(PCR_OUTBOUND_QUEUE, subscriber.getNotificationEndpoint(), payload, 0);
            } else {
                callbackService.sendToSubscriber(subscriber.getNotificationEndpoint(), payloadWrapper);
                log.info("Subscriber {} notified via callbackUrl {} for documentId {}", subscriber.getId(), subscriber.getNotificationEndpoint(), eventNotificationPayload.getDocumentId());
            }
        }
    }
}
