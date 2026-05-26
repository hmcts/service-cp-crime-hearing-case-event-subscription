package uk.gov.hmcts.cp.subscription.unit.managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.managers.NotificationManager;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;
import uk.gov.hmcts.cp.subscription.services.CallbackDeliveryService;
import uk.gov.hmcts.cp.subscription.services.DocumentService;
import uk.gov.hmcts.cp.subscription.services.NotificationService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationManagerTest {

    @Mock
    NotificationService notificationService;

    @Mock
    DocumentService documentService;

    @Mock
    SubscriptionService subscriptionService;

    @Mock
    CallbackDeliveryService callbackDeliveryService;

    @InjectMocks
    NotificationManager notificationManager;

    UUID materialId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    UUID subscriptionId = UUID.randomUUID();
    UUID clientId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    EventPayload payload = EventPayload.builder()
            .materialId(materialId)
            .eventType("PRISON_COURT_REGISTER_GENERATED")
            .build();

    DocumentContent content = DocumentContent.builder()
            .body("PDF".getBytes())
            .contentType(MediaType.APPLICATION_PDF)
            .fileName("doc.pdf")
            .build();

    @Test
    void processPcrNotification_should_process_deliver() {
        when(notificationService.processInboundEvent(payload)).thenReturn(documentId);

        notificationManager.processPcrNotification(payload);

        verify(notificationService).processInboundEvent(eq(payload));
        verify(callbackDeliveryService).submitOutboundPcrEvents(payload, documentId);
    }

    @Test
    void getPcrDocumentContent_should_return_content_when_subscription_has_access() {
        when(documentService.getEventTypeForDocument(documentId)).thenReturn("PRISON_COURT_REGISTER_GENERATED");
        when(subscriptionService.hasAccess(subscriptionId, clientId, "PRISON_COURT_REGISTER_GENERATED")).thenReturn(true);
        when(documentService.getDocumentContent(documentId)).thenReturn(content);

        DocumentContent result = notificationManager.getPcrDocumentContent(subscriptionId, clientId, documentId);

        assertThat(result).isEqualTo(content);
    }

    @Test
    void getPcrDocumentContent_should_throw_forbidden_when_subscription_has_no_access() {
        when(documentService.getEventTypeForDocument(documentId)).thenReturn("PRISON_COURT_REGISTER_GENERATED");

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> notificationManager.getPcrDocumentContent(subscriptionId, clientId, documentId));

        assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrown.getReason()).contains("Access denied");
        verify(documentService, never()).getDocumentContent(any());
    }
}
