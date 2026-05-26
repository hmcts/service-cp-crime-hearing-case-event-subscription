package uk.gov.hmcts.cp.servicebus.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.clients.CallbackClient;
import uk.gov.hmcts.cp.subscription.managers.NotificationManager;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_OUTBOUND_QUEUE;

@Service
@AllArgsConstructor
@Slf4j
public class ServiceBusHandlers {

    private final CallbackClient callbackClient;
    private final NotificationManager notificationManager;
    private final JsonMapper jsonMapper;

    public void handleMessage(final String queueName, final String target, final String message) {
        switch (queueName) {
            case PCR_INBOUND_QUEUE -> {
                final EventPayload eventPayload = jsonMapper.fromJson(message, EventPayload.class);
                log.info("handleMessageType {} eventId:{}", queueName, eventPayload.getEventId());
                notificationManager.processPcrNotification(eventPayload);
            }
            case PCR_OUTBOUND_QUEUE -> {
                final EventNotificationPayloadWrapper wrapper = jsonMapper.fromJson(message, EventNotificationPayloadWrapper.class);
                log.info("handleMessageType {}", queueName);
                callbackClient.sendNotification(target, wrapper);
            }
            default -> throw new RuntimeException("Invalid queue name " + queueName);
        }
        log.info("handleMessageType completed OK");
    }
}
