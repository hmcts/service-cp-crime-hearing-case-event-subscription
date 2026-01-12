package uk.gov.hmcts.cp.subscription.controllers;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;
import uk.gov.hmcts.cp.subscription.services.NotificationService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    NotificationService notificationService;

    @InjectMocks
    NotificationController notificationController;

    @Test
    void should_process_pcr_notification() {
        PcrEventPayload payload = PcrEventPayload.builder().materialId(UUID.randomUUID()).build();
        when(notificationService.processPcrEvent(any())).thenReturn(any());
        var response = notificationController.createNotificationPCR(payload);
        verify(notificationService, times(1)).processPcrEvent(any());
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNull();
    }
}