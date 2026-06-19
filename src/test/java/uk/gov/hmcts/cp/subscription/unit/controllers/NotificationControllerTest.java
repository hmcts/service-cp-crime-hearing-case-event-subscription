package uk.gov.hmcts.cp.subscription.unit.controllers;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.cp.filters.ClientIdResolutionFilter;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.config.EnvironmentName;
import uk.gov.hmcts.cp.subscription.controllers.NotificationController;
import uk.gov.hmcts.cp.subscription.managers.NotificationManager;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;
import uk.gov.hmcts.cp.subscription.services.EventTypeService;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    JsonMapper jsonMapper;
    @Mock
    ServiceBusClientService clientService;
    @Mock
    EventTypeService eventTypeService;
    @Mock
    NotificationManager notificationManager;
    @Mock
    AppProperties appProperties;

    @InjectMocks
    NotificationController notificationController;

    UUID documentId = UUID.randomUUID();
    UUID subscriptionId = UUID.randomUUID();
    UUID resolvedClientUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
    EventPayload payload = EventPayload.builder().build();

    @BeforeEach
    void setMdcClientId() {
        MDC.put(ClientIdResolutionFilter.MDC_CLIENT_ID, resolvedClientUuid.toString());
    }

    @AfterEach
    void clearMdcClientId() {
        MDC.remove(ClientIdResolutionFilter.MDC_CLIENT_ID);
    }

    @SneakyThrows
    @Test
    void valid_notification_should_queue_to_service_bus_and_return_accepted() {
        when(jsonMapper.toJson(payload)).thenReturn("payload-json");
        when(eventTypeService.eventExists(payload.getEventType())).thenReturn(true);

        ResponseEntity<Void> response = notificationController.createNotification(payload, null);

        verify(clientService).queueMessage(NOTIFICATIONS_INBOUND_QUEUE, null, "payload-json", 0);
        assertThat(response.getStatusCode()).isEqualTo(ACCEPTED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void notification_disabled_in_dev_should_swallow_and_return_accepted() {
        when(appProperties.isHearingEventDisabledInDev()).thenReturn(true);
        when(appProperties.getEnvironmentName()).thenReturn(EnvironmentName.DEV);

        ResponseEntity<Void> response = notificationController.createNotification(payload, null);

        verifyNoInteractions(clientService, eventTypeService, jsonMapper);
        assertThat(response.getStatusCode()).isEqualTo(ACCEPTED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void notification_disabled_outside_dev_should_still_be_processed() {
        when(appProperties.isHearingEventDisabledInDev()).thenReturn(true);
        when(appProperties.getEnvironmentName()).thenReturn(EnvironmentName.PRD);
        when(jsonMapper.toJson(payload)).thenReturn("payload-json");
        when(eventTypeService.eventExists(payload.getEventType())).thenReturn(true);

        ResponseEntity<Void> response = notificationController.createNotification(payload, null);

        verify(clientService).queueMessage(NOTIFICATIONS_INBOUND_QUEUE, null, "payload-json", 0);
        assertThat(response.getStatusCode()).isEqualTo(ACCEPTED);
    }

    @Test
    void invalid_event_payload_is_silently_ignored() {
        when(eventTypeService.eventExists(payload.getEventType())).thenReturn(false);
        ResponseEntity<Void> response = notificationController.createNotification(payload, null);
        verifyNoInteractions(clientService);
        assertThat(response.getStatusCode()).isEqualTo(ACCEPTED);
    }

    @Test
    void jsonnode_introspection_payload_is_accepted_when_toggle_disabled() {
        EventPayload introspectionPayload = EventPayload.builder()
                .payload(Map.of("nodeType", "OBJECT", "containerNode", true, "object", true))
                .build();
        when(jsonMapper.toJson(introspectionPayload)).thenReturn("payload-json");
        when(eventTypeService.eventExists(introspectionPayload.getEventType())).thenReturn(true);

        ResponseEntity<Void> response = notificationController.createNotification(introspectionPayload, null);

        verify(clientService).queueMessage(NOTIFICATIONS_INBOUND_QUEUE, null, "payload-json", 0);
        assertThat(response.getStatusCode()).isEqualTo(ACCEPTED);
    }

    @Test
    void jsonnode_introspection_payload_is_rejected_when_toggle_enabled() {
        ReflectionTestUtils.setField(notificationController, "hearingEventJsonEnabled", true);
        EventPayload introspectionPayload = EventPayload.builder()
                .payload(Map.of("nodeType", "OBJECT", "containerNode", true, "object", true))
                .build();

        ResponseEntity<Void> response = notificationController.createNotification(introspectionPayload, null);

        verifyNoInteractions(clientService);
        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void get_notification_event_document_should_return_200_with_content_from_manager() throws Exception {
        byte[] pdfBody = "PDF content".getBytes();
        DocumentContent content = DocumentContent.builder()
                .body(pdfBody)
                .contentType(MediaType.APPLICATION_PDF)
                .fileName("PrisonCourtRegister.pdf")
                .build();
        when(notificationManager.getDocumentContent(subscriptionId, documentId)).thenReturn(content);

        ResponseEntity<Resource> response = notificationController.getDocument(subscriptionId, documentId, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInputStream().readAllBytes()).isEqualTo(pdfBody);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("PrisonCourtRegister.pdf");
        verify(notificationManager).getDocumentContent(subscriptionId, documentId);
    }
}
