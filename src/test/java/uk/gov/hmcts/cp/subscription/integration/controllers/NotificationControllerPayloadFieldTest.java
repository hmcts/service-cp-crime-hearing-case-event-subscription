package uk.gov.hmcts.cp.subscription.integration.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "hearing-event.json.enabled=true")
class NotificationControllerPayloadFieldTest extends IntegrationTestBase {

    private static final String NOTIFICATION_URI = "/notifications";

    private static final String PRODUCTION_PCR_PAYLOAD_FILE =
            "stubs/requests/progression/pcr-payload-production-sample.json";

    // Full notification body produced by progression after the FileService.retrieveRawPayload() fix.
    private static final String PROGRESSION_FIX_NOTIFICATION_FILE =
            "stubs/requests/progression/pcr-notification-from-progression.json";

    private static final String HEARING_NOWS_FIX_NOTIFICATION_FILE =
            "stubs/requests/hearingnows/now-notification-from-hearing-nows.json";

    private static final String BASE_BODY = "{"
            + "\"eventType\":\"PRISON_COURT_REGISTER_GENERATED\","
            + "\"eventId\":\"a4554152-10fb-44fe-a015-226f8d547c91\","
            + "\"materialId\":\"886a3d9c-2543-4fdd-8b5c-1597e3d36ebb\","
            + "\"hearingId\":\"b2c3d4e5-f6a7-8901-bcde-f12345678901\","
            + "\"timestamp\":\"2026-05-29T10:23:29Z\","
            + "\"defendant\":{"
            + "\"masterDefendantId\":\"f08465c5-0000-0000-0000-000000000000\","
            + "\"name\":\"Leo Kuhn\","
            + "\"dateOfBirth\":\"2000-03-24\","
            + "\"custodyEstablishmentDetails\":{\"emailAddress\":\"lavenderhill@prison.gov.uk\"},"
            + "\"cases\":[{\"urn\":\"28DI8140839\"}]"
            + "}";

    private static final String BASE_BODY_NO_DOB = "{"
            + "\"eventType\":\"PRISON_COURT_REGISTER_GENERATED\","
            + "\"eventId\":\"a4554152-10fb-44fe-a015-226f8d547c91\","
            + "\"materialId\":\"886a3d9c-2543-4fdd-8b5c-1597e3d36ebb\","
            + "\"hearingId\":\"b2c3d4e5-f6a7-8901-bcde-f12345678901\","
            + "\"timestamp\":\"2026-05-29T10:23:29Z\","
            + "\"defendant\":{"
            + "\"masterDefendantId\":\"f08465c5-0000-0000-0000-000000000000\","
            + "\"name\":\"Leo Kuhn\","
            + "\"custodyEstablishmentDetails\":{\"emailAddress\":\"lavenderhill@prison.gov.uk\"},"
            + "\"cases\":[{\"urn\":\"28DI8140839\"}]"
            + "}";

    @MockitoBean
    private ServiceBusClientService serviceBusClientService;

    @BeforeEach
    void setUp() {
        reset(serviceBusClientService);
        clearAllTables();
    }

    @Test
    void notification_without_date_of_birth_should_return_202() throws Exception {
        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BASE_BODY_NO_DOB + "}"))
                .andDo(print())
                .andExpect(status().isAccepted());
    }

    @Test
    void notification_without_payload_field_should_return_202() throws Exception {
        String body = BASE_BODY + "}";

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isAccepted());
    }

    @Test
    void notification_with_production_pcr_payload_as_stored_in_file_service_should_return_202() throws Exception {
        String pcrPayloadJson = loadPayload(PRODUCTION_PCR_PAYLOAD_FILE);
        String body = BASE_BODY + ",\"payload\":" + pcrPayloadJson + "}";

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isAccepted());
    }

    @Test
    void notification_with_production_pcr_payload_after_wildfly_tostring_bug_should_return_400() throws Exception {
        String pcrPayloadJsonFromFileService = loadPayload(PRODUCTION_PCR_PAYLOAD_FILE);
        String pcrPayloadAfterWildflyToString = pcrPayloadJsonFromFileService.replace("\\n", "\n");
        String body = BASE_BODY + ",\"payload\":" + pcrPayloadAfterWildflyToString + "}";

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void notification_with_complete_payload_as_produced_by_progression_fix_should_return_202() throws Exception {
        String body = loadPayload(PROGRESSION_FIX_NOTIFICATION_FILE);

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isAccepted());
    }

    @Test
    void notification_with_now_payload_as_produced_by_hearing_nows_fix_should_return_202() throws Exception {
        String body = loadPayload(HEARING_NOWS_FIX_NOTIFICATION_FILE);

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isAccepted());
    }

    @Test
    void notification_with_jsonnode_introspection_payload_should_return_400() throws Exception {
        String introspectionPayload = "{"
                + "\"int\":false,\"long\":false,\"object\":true,"
                + "\"nodeType\":\"OBJECT\",\"textual\":false,\"containerNode\":true}";
        String body = BASE_BODY + ",\"payload\":" + introspectionPayload + "}";

        mockMvc.perform(post(NOTIFICATION_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
