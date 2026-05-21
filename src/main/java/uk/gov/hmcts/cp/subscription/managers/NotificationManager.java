package uk.gov.hmcts.cp.subscription.managers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;
import uk.gov.hmcts.cp.subscription.services.CallbackDeliveryService;
import uk.gov.hmcts.cp.subscription.services.DocumentService;
import uk.gov.hmcts.cp.subscription.services.NotificationService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;

import java.util.UUID;

/**
 * Orchestrates notification and document retrieval flows.
 * Encapsulates coordination between notification, subscription, document and callback services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationManager {

    private final NotificationService notificationService;
    private final DocumentService documentService;
    private final SubscriptionService subscriptionService;
    private final CallbackDeliveryService callbackDeliveryService;

    public void processNotification(final EventPayload eventPayload) {
        log.info("processNotification eventId:{} materialId:{}", eventPayload.getEventId(), eventPayload.getMaterialId());
        final UUID documentId = notificationService.processInboundEvent(eventPayload);
        callbackDeliveryService.submitOutboundEvents(eventPayload, documentId);
        log.info("processNotification complete eventId:{} documentId:{}", eventPayload.getEventId(), documentId);
    }

    public DocumentContent getDocumentContent(final UUID clientSubscriptionId, final UUID documentId) {
        log.info("getDocumentContent clientSubscriptionId:{} documentId:{}", clientSubscriptionId, documentId);
        final String eventType = documentService.getEventTypeForDocument(documentId);
        if (!subscriptionService.hasAccess(clientSubscriptionId, eventType)) {
            log.error("getDocumentContent access denied clientSubscriptionId:{} eventType:{}", clientSubscriptionId, eventType);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: subscription does not have access to this document");
        }
        return documentService.getDocumentContent(documentId);
    }
}
