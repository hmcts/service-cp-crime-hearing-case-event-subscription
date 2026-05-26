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
import uk.gov.hmcts.cp.filters.ClientIdResolutionFilter;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.controllers.NotificationController;
import uk.gov.hmcts.cp.subscription.managers.NotificationManager;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_INBOUND_QUEUE;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    ServiceBusConfigService configService;
    @Mock
    JsonMapper jsonMapper;
    @Mock
    ServiceBusClientService clientService;
    @Mock
    NotificationManager notificationManager;

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
    void valid_pcr_payload_should_process_notification() {
        ResponseEntity<Void> response = notificationController.createNotification(payload, null);
        verify(notificationManager).processPcrNotification(eq(payload));
        assertThat(response.getStatusCode()).isEqualTo(ACCEPTED);
    }

    @Test
    void valid_pcr_payload_should_send_to_servicebus() {
        when(configService.isEnabled()).thenReturn(true);
        when(jsonMapper.toJson(payload)).thenReturn("payload-json");
        ResponseEntity<Void> response = notificationController.createNotification(payload, null);
        verify(clientService).queueMessage(PCR_INBOUND_QUEUE, null, "payload-json", 0);
        assertThat(response.getStatusCode()).isEqualTo(ACCEPTED);
    }

    @Test
    void get_pcr_document_should_return_200_with_content_from_manager() throws Exception {
        byte[] pdfBody = "PDF content".getBytes();
        DocumentContent content = DocumentContent.builder()
                .body(pdfBody)
                .contentType(MediaType.APPLICATION_PDF)
                .fileName("PrisonCourtRegister.pdf")
                .build();
        when(notificationManager.getPcrDocumentContent(eq(subscriptionId), eq(resolvedClientUuid), eq(documentId))).thenReturn(content);

        ResponseEntity<Resource> response = notificationController.getDocument(subscriptionId, documentId, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInputStream().readAllBytes()).isEqualTo(pdfBody);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("PrisonCourtRegister.pdf");
        verify(notificationManager).getPcrDocumentContent(subscriptionId, resolvedClientUuid, documentId);
    }
}
