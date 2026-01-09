package uk.gov.hmcts.cp.subscription.controllers;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;
import uk.gov.hmcts.cp.subscription.services.NotificationService;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;
import wiremock.com.fasterxml.jackson.databind.SerializationFeature;
import wiremock.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

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
    void should_process_pcr_notification() throws IOException {
        ClassPathResource resource = new ClassPathResource("requests/pcr-request.json");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PcrEventPayload payload = objectMapper.readValue(resource.getFile(), PcrEventPayload.class);

        when(notificationService.processPcrEvent(any())).thenReturn(any());
        var response = notificationController.notificationPCR(payload);
        verify(notificationService, times(1)).processPcrEvent(any());
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNull();
    }
}