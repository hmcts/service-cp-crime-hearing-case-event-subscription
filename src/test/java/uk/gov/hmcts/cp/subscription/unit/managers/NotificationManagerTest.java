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
import uk.gov.hmcts.cp.openapi.model.HearingEventResponse;
import uk.gov.hmcts.cp.subscription.managers.NotificationManager;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;
import uk.gov.hmcts.cp.subscription.services.CallbackDeliveryService;
import uk.gov.hmcts.cp.subscription.services.DocumentService;
import uk.gov.hmcts.cp.subscription.services.HearingEventService;
import uk.gov.hmcts.cp.subscription.services.NotificationService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @Mock
    HearingEventService hearingEventService;

    @InjectMocks
    NotificationManager notificationManager;

    UUID materialId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID subscriptionId = UUID.randomUUID();
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
    void processNotification_should_process_and_deliver_notification() {
        when(notificationService.processInboundEvent(payload)).thenReturn(documentId);

        notificationManager.processNotification(payload);

        verify(notificationService).processInboundEvent(eq(payload));
        verify(callbackDeliveryService).submitOutboundEvents(payload, documentId);
    }

    @Test
    void getDocumentContent_should_return_content_when_subscription_has_access() {
        when(documentService.getEventTypeForDocument(documentId)).thenReturn("PRISON_COURT_REGISTER_GENERATED");
        when(subscriptionService.hasAccess(subscriptionId, "PRISON_COURT_REGISTER_GENERATED")).thenReturn(true);
        when(documentService.getDocumentContent(documentId)).thenReturn(content);

        DocumentContent result = notificationManager.getDocumentContent(subscriptionId, documentId);

        assertThat(result).isEqualTo(content);
    }

    @Test
    void getHearingEvent_should_delegate_to_hearing_event_service() {
        UUID hearingEventId = UUID.randomUUID();
        HearingEventResponse expected = HearingEventResponse.builder()
                .hearingEventId(hearingEventId)
                .eventType("PRISON_COURT_REGISTER_GENERATED")
                .build();
        when(hearingEventService.getHearingEvent(subscriptionId, hearingEventId)).thenReturn(expected);

        HearingEventResponse result = notificationManager.getHearingEvent(subscriptionId, hearingEventId);

        assertThat(result).isEqualTo(expected);
        verify(hearingEventService).getHearingEvent(subscriptionId, hearingEventId);
    }

    @Test
    void getDocumentContent_should_throw_forbidden_when_subscription_has_no_access() {
        when(documentService.getEventTypeForDocument(documentId)).thenReturn("PRISON_COURT_REGISTER_GENERATED");

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> notificationManager.getDocumentContent(subscriptionId, documentId));

        assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thrown.getReason()).contains("Access denied");
        verify(documentService, never()).getDocumentContent(any());
    }

    @Test
    void validateClientOwnsSubscription_should_delegate_to_subscription_service() {
        notificationManager.validateClientOwnsSubscription(clientId, subscriptionId);

        verify(subscriptionService).assertClientOwnsSubscription(clientId, subscriptionId);
    }

    @Test
    void validateClientOwnsSubscription_should_propagate_forbidden_when_ownership_fails() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: subscription does not belong to this client"))
                .when(subscriptionService).assertClientOwnsSubscription(clientId, subscriptionId);

        assertThatThrownBy(() -> notificationManager.validateClientOwnsSubscription(clientId, subscriptionId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
