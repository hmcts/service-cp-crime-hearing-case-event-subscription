package uk.gov.hmcts.cp.subscription.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.hmac.managers.HmacManager;
import uk.gov.hmcts.cp.openapi.model.EventNotificationPayload;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientHmacEntity;
import uk.gov.hmcts.cp.subscription.mappers.NotificationMapper;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;
import uk.gov.hmcts.cp.subscription.repositories.ClientEventRepository;
import uk.gov.hmcts.cp.subscription.repositories.ClientHmacRepository;

import java.util.List;
import java.util.UUID;

import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;

@Service
@Slf4j
public class CallbackDeliveryService {

    public static final String EXAMPLE_ENDPOINT = "https://example.com";
    private static final EntityNotFoundException HMAC_NOT_FOUND = new EntityNotFoundException("Hmac not found for client subscriptionId");

    private final ClientEventRepository clientEventRepository;
    private final ClientHmacRepository clientHmacRepository;
    private final NotificationMapper notificationMapper;
    private final JsonMapper jsonMapper;
    private final ServiceBusClientService clientService;
    private final HmacManager hmacManager;
    private final HearingEventService hearingEventPayloadService;
    private final boolean hearingEventJsonEnabled;

    public CallbackDeliveryService(
            final ClientEventRepository clientEventRepository,
            final ClientHmacRepository clientHmacRepository,
            final NotificationMapper notificationMapper,
            final JsonMapper jsonMapper,
            final ServiceBusClientService clientService,
            final HmacManager hmacManager,
            final HearingEventService hearingEventPayloadService,
            @Value("${hearing-event.json.enabled:false}") final boolean hearingEventJsonEnabled) {
        this.clientEventRepository = clientEventRepository;
        this.clientHmacRepository = clientHmacRepository;
        this.notificationMapper = notificationMapper;
        this.jsonMapper = jsonMapper;
        this.clientService = clientService;
        this.hmacManager = hmacManager;
        this.hearingEventPayloadService = hearingEventPayloadService;
        this.hearingEventJsonEnabled = hearingEventJsonEnabled;
    }

    public void submitOutboundEvents(final EventPayload eventPayload, final UUID documentId) {
        final String eventType = eventPayload.getEventType();
        final List<ClientEntity> clients = clientEventRepository.findClientsByEventType(eventType);
        final EventNotificationPayload eventNotificationPayload = notificationMapper.mapToPayload(documentId, eventPayload);
        log.info("sending {} outbound notifications", clients.size());

        final UUID hearingEventId = hearingEventJsonEnabled ? hearingEventPayloadService.saveIfAbsent(eventPayload) : null;

        for (final ClientEntity client : clients) {
            if (hearingEventJsonEnabled && hearingEventId != null) {
                hearingEventPayloadService.saveSubscriptionIfAbsent(client.getSubscriptionId(), hearingEventId);
            }

            final ClientHmacEntity clientHmac = clientHmacRepository.findBySubscriptionId(client.getSubscriptionId())
                    .orElseThrow(() -> HMAC_NOT_FOUND);
            final String signature = hmacManager.calculateSignature(clientHmac.getKeyId(), jsonMapper.toJson(eventNotificationPayload));
            final EventNotificationPayloadWrapper payloadWrapper = notificationMapper.mapToWrapper(eventNotificationPayload, clientHmac.getKeyId(), signature);
            if (client.getCallbackUrl().startsWith(EXAMPLE_ENDPOINT)) {
                log.info("Skipping notification for EXAMPLE callback endpoint:{}", client.getCallbackUrl());
            } else {
                final String payload = jsonMapper.toJson(payloadWrapper);
                clientService.queueMessage(NOTIFICATIONS_OUTBOUND_QUEUE, client.getCallbackUrl(), payload, 0);
            }
        }
    }
}
