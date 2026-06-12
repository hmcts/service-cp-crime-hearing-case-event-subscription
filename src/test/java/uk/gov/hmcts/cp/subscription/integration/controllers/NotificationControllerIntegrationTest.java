package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventPayloadEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;

@EnableWireMock({@ConfigureWireMock(name = "material-client", baseUrlProperties = "material-client.url", port = 0, filesUnderClasspath = "wiremock/material-client")})
@TestPropertySource(properties = {
        "material-service.url=the-real-dev-one",
        "material-client.cjscppuid=the-real-one",
        "hearing-event.json.enabled=true"
})
class NotificationControllerIntegrationTest extends IntegrationTestBase {

    private static final String NOTIFICATION_URI = "/notifications";
    private static final String CALLBACK_URL = "https://callback.example.com";
    private static final UUID MATERIAL_ID = UUID.fromString("04325082-5203-4eaa-9f62-e153d6308631");
    private static final String DOCUMENT_URI = "/client-subscriptions/{clientSubscriptionId}/documents/{documentId}";
    private static final String HEARING_EVENT_URI = "/client-subscriptions/{clientSubscriptionId}/hearing-events/{hearingEventId}";

    @MockitoBean
    private ServiceBusClientService serviceBusClientService;

    @BeforeEach
    void setUp() {
        reset(serviceBusClientService);
        clearAllTables();
    }

    @Test
    void prison_court_register_generated_should_queue_to_service_bus_and_return_accepted() throws Exception {
        String payload = loadPayload("stubs/requests/progression/pcr-request-prison-court-register.json");

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .content(payload))
                .andExpect(status().isAccepted());

        verify(serviceBusClientService).queueMessage(eq(NOTIFICATIONS_INBOUND_QUEUE), eq(null), anyString(), eq(0));
    }

    @Test
    void get_hearing_event_should_return_200_with_full_response_when_subscription_owns_event() throws Exception {
        UUID subscriptionId = insertSubscription(CALLBACK_URL, List.of("PRISON_COURT_REGISTER_GENERATED"));
        UUID hearingEventId = insertHearingEventPayload();
        insertHearingEventSubscription(subscriptionId, hearingEventId);

        mockMvc.perform(get(HEARING_EVENT_URI, subscriptionId, hearingEventId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hearingEventId").value(hearingEventId.toString()))
                .andExpect(jsonPath("$.eventType").value("PRISON_COURT_REGISTER_GENERATED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.payload.eventType").value("PRISON_COURT_REGISTER_GENERATED"))
                .andExpect(jsonPath("$.payload.materialId").doesNotExist())
                .andExpect(jsonPath("$.payload.eventId").doesNotExist());
    }

    @Test
    void get_hearing_event_should_return_403_when_client_does_not_own_subscription() throws Exception {
        UUID foreignSubscriptionId = UUID.randomUUID();

        mockMvc.perform(get(HEARING_EVENT_URI, foreignSubscriptionId, UUID.randomUUID())
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void get_hearing_event_should_return_404_when_hearing_event_not_found_for_subscription() throws Exception {
        UUID subscriptionId = insertSubscription(CALLBACK_URL, List.of("PRISON_COURT_REGISTER_GENERATED"));

        mockMvc.perform(get(HEARING_EVENT_URI, subscriptionId, UUID.randomUUID())
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void get_document_should_return_200_with_pdf_when_subscription_has_access() throws Exception {
        UUID subscriptionId = insertSubscription(
                CALLBACK_URL,
                List.of("PRISON_COURT_REGISTER_GENERATED"));
        DocumentMappingEntity document = insertDocument(MATERIAL_ID, "PRISON_COURT_REGISTER_GENERATED");

        mockMvc.perform(get(DOCUMENT_URI,
                        subscriptionId, document.getDocumentId())
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"PrisonCourtRegister_20260325161340.pdf\""))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    private UUID insertHearingEventPayload() {
        Long eventTypeId = eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED").orElseThrow().getId();
        HearingEventPayloadEntity entity = hearingEventPayloadRepository.save(HearingEventPayloadEntity.builder()
                .eventId(UUID.randomUUID())
                .eventTypeId(eventTypeId)
                .rawPayload(EventPayload.builder().eventType("PRISON_COURT_REGISTER_GENERATED").build())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        return entity.getHearingEventId();
    }

    private UUID insertHearingEventSubscription(final UUID subscriptionId, final UUID hearingEventId) {
        HearingEventSubscriptionEntity entity = hearingEventSubscriptionRepository.save(HearingEventSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .hearingEventId(hearingEventId)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        return entity.getHearingEventId();
    }
}
