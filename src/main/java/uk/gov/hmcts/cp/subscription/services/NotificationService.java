package uk.gov.hmcts.cp.subscription.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.subscription.model.MaterialMetadata;
import uk.gov.hmcts.cp.openapi.model.EventPayload;

import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
public class NotificationService {

    private final MaterialService materialService;
    private final DocumentService documentService;

    public UUID processInboundEvent(final EventPayload eventPayload) {
        log.info("processInboundEvent eventId:{} materialId:{} eventType:{}",
                eventPayload.getEventId(), eventPayload.getMaterialId(), eventPayload.getEventType());

        if (eventPayload.getDefendant() != null && eventPayload.getDefendant().getDateOfBirth() == null) {
            log.warn("Inbound notification missing dateOfBirth for defendant. eventId:{} eventType:{}",
                    eventPayload.getEventId(), eventPayload.getEventType());
        }

        final MaterialMetadata materialMetadata = materialService.getMaterialMetadata(eventPayload.getMaterialId());
        final UUID documentId = documentService.saveDocumentMapping(materialMetadata.getMaterialId(), eventPayload.getEventType());
        log.info("processInboundEvent complete eventId:{} documentId:{}", eventPayload.getEventId(), documentId);
        return documentId;
    }
}
