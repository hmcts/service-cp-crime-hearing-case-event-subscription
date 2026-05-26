package uk.gov.hmcts.cp.subscription.unit.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.model.MaterialMetadata;
import uk.gov.hmcts.cp.subscription.services.DocumentService;
import uk.gov.hmcts.cp.subscription.services.MaterialService;
import uk.gov.hmcts.cp.subscription.services.NotificationService;

import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    ServiceBusConfigService configService;
    @Mock
    AppProperties appProperties;
    @Mock
    private DocumentService documentService;
    @Mock
    private MaterialService materialService;

    @InjectMocks
    private NotificationService notificationService;


    @Test
    void sync_notification_should_save_document() {
        UUID materialId = randomUUID();
        EventPayload payload = EventPayload.builder()
                .materialId(materialId)
                .eventType("PRISON_COURT_REGISTER_GENERATED")
                .build();
        MaterialMetadata materialMetadata = new MaterialMetadata();
        materialMetadata.setMaterialId(materialId);
        when(materialService.waitForMaterialMetadata(materialId)).thenReturn(materialMetadata);

        notificationService.processInboundEvent(payload);

        verify(materialService).waitForMaterialMetadata(materialId);
        verify(documentService).saveDocumentMapping(eq(materialId), eq("PRISON_COURT_REGISTER_GENERATED"));
    }

    @Test
    void async_notification_should_save_document() {
        when(configService.isEnabled()).thenReturn(true);
        UUID materialId = randomUUID();
        EventPayload payload = EventPayload.builder()
                .materialId(materialId)
                .eventType("PRISON_COURT_REGISTER_GENERATED")
                .build();
        MaterialMetadata materialMetadata = new MaterialMetadata();
        materialMetadata.setMaterialId(materialId);
        when(materialService.getMaterialMetadata(materialId)).thenReturn(materialMetadata);

        notificationService.processInboundEvent(payload);

        verify(materialService).getMaterialMetadata(materialId);
        verify(documentService).saveDocumentMapping(eq(materialId), eq("PRISON_COURT_REGISTER_GENERATED"));
    }
}
