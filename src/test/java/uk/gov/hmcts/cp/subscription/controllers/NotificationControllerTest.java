package uk.gov.hmcts.cp.subscription.controllers;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;
import uk.gov.hmcts.cp.subscription.services.NotificationService;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;
import wiremock.com.fasterxml.jackson.databind.SerializationFeature;
import wiremock.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void shouldProcessPcrNotificationAndReturnCreated() throws Exception {
        UUID materialId = UUID.randomUUID();

        ClassPathResource resource = new ClassPathResource("requests/pcr-request.json");
        PcrEventPayload payload = objectMapper.readValue(resource.getFile(), PcrEventPayload.class);

        payload.setEventType("PRISON_COURT_REGISTER_GENERATED");

        mockMvc.perform(post("/notifications/pcr/{materialId}", materialId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(content().json(objectMapper.writeValueAsString(payload)));

        Mockito.verify(notificationService)
                .processPcrEvent(materialId);
    }

    @Test
    void shouldReturnBadRequestWhenPayloadInvalid() throws Exception {
        UUID materialId = UUID.randomUUID();

        PcrEventPayload invalidPayload = new PcrEventPayload(); // missing required fields

        mockMvc.perform(post("/notifications/pcr/{materialId}", materialId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(notificationService);
    }
}