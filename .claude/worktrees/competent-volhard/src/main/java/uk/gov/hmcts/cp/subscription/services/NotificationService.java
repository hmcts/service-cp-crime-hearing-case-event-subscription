package uk.gov.hmcts.cp.subscription.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.subscription.model.MaterialMetadata;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;

import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
public class NotificationService {

    private final ServiceBusConfigService serviceBusConfigService;
    private final MaterialService materialService;
    private final DocumentService documentService;

    public UUID processInboundEvent(final EventPayload eventPayload) {
        final MaterialMetadata materialMetadata = serviceBusConfigService.isEnabled()
                ? materialService.getMaterialMetadata(eventPayload.getMaterialId())
                : materialService.waitForMaterialMetadata(eventPayload.getMaterialId());
        return documentService.saveDocumentMapping(materialMetadata.getMaterialId(), eventPayload.getEventType());
    }
}
