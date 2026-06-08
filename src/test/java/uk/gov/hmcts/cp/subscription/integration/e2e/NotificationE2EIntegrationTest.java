package uk.gov.hmcts.cp.subscription.integration.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import uk.gov.hmcts.cp.filters.UUIDService;
import uk.gov.hmcts.cp.hmac.services.EncodingService;
import uk.gov.hmcts.cp.hmac.services.HmacSigningService;
import uk.gov.hmcts.cp.servicebus.integration.ServiceBusTestService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusAdminService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusProcessorService;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;
import uk.gov.hmcts.cp.subscription.config.IgnoreSSLCertificatesForWiremockTest;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;
import uk.gov.hmcts.cp.subscription.integration.stubs.SubscriptionStub;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.getBodyFromCallbackServeEvents;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.getHeaderFromCallbackServeEvents;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.stubCallbackEndpoint;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.stubCallbackEndpointReturnsServerError;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialBinary;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialContent;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialMetadata;
import static uk.gov.hmcts.cp.subscription.integration.stubs.SubscriptionStub.createSubscription;
import static uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper.KEY_ID_HEADER;
import static uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper.SIGNATURE_HEADER;

@EnableWireMock({
        @ConfigureWireMock(name = "material-client", baseUrlProperties = "material-client.url", port = 0),
        @ConfigureWireMock(name = "callback-client", httpsBaseUrlProperties = "callback-client.url", httpsPort = 0)
})
@Import(IgnoreSSLCertificatesForWiremockTest.class)
@TestPropertySource(properties = {
        "service-bus.max-tries=3",
        "service-bus.retry-durations=0s,500ms,1s"
})
@Slf4j
class NotificationE2EIntegrationTest extends IntegrationTestBase {

    @Autowired
    ServiceBusAdminService adminService;
    @Autowired
    ServiceBusProcessorService processorService;
    @Autowired
    ServiceBusTestService testService;
    @Autowired
    HmacSigningService hmacSigningService;
    @Autowired
    EncodingService encodingService;
    @Autowired
    JsonMapper jsonMapper;

    private UUID subscriptionId;
    private String hmacKeyId;
    private String hmacSecret;
    private UUID callbackDocumentId;
    private UUID callbackHearingId;
    private String callbackBody;
    private String callbackSignature;
    private String callbackKeyId;

    private static final UUID materialId = UUID.fromString("6c198796-08bb-4803-b456-fa0c29ca6021");
    private static final String documentUri = CLIENT_SUBSCRIPTIONS_URI + "/{clientSubscriptionId}/documents/{documentId}";
    private static final String eventPayloadPath = "stubs/requests/progression/pcr-request-prison-court-register.json";

    @InjectWireMock("callback-client")
    private WireMockServer callbackWireMock;

    @Value("${callback-client.url}")
    private String callbackBaseUrl;

    @MockitoSpyBean
    private UUIDService uuidService;
    @MockitoSpyBean
    private MaterialClient materialClient;

    @BeforeEach
    void setUp() {
        assertThat(adminService.isServiceBusReady()).isTrue();
        processorService.stopMessageProcessor(NOTIFICATIONS_INBOUND_QUEUE);
        testService.dropQueueIfExists(NOTIFICATIONS_INBOUND_QUEUE);
        adminService.createQueue(NOTIFICATIONS_INBOUND_QUEUE);
        processorService.startMessageProcessor(NOTIFICATIONS_INBOUND_QUEUE);

        processorService.stopMessageProcessor(NOTIFICATIONS_OUTBOUND_QUEUE);
        testService.dropQueueIfExists(NOTIFICATIONS_OUTBOUND_QUEUE);
        adminService.createQueue(NOTIFICATIONS_OUTBOUND_QUEUE);
        processorService.startMessageProcessor(NOTIFICATIONS_OUTBOUND_QUEUE);

        WireMock.reset();
        if (nonNull(callbackWireMock)) {
            callbackWireMock.resetAll();
        }
        clearAllTables();
    }

    @AfterEach
    void afterEach() {
        processorService.stopMessageProcessor(NOTIFICATIONS_INBOUND_QUEUE);
        processorService.stopMessageProcessor(NOTIFICATIONS_OUTBOUND_QUEUE);
    }

    @Test
    void happy_path_should_return_pdf() throws Exception {
        given_i_create_a_new_subscription();
        given_i_have_a_callback_endpoint();
        given_material_service_returns_document_success();

        when_a_notification_event_is_posted();
        when_material_service_responds();

        then_the_subscriber_receives_a_callback();
        and_the_callback_signature_is_correct();
        then_the_subscriber_can_retrieve_the_document();
    }

    @Test
    void callback_client_error_should_try_3_times_in_4_seconds() throws Exception {
        given_i_create_a_new_subscription();
        given_callback_endpoint_returns_server_error();
        given_material_service_returns_document_success();

        when_a_notification_event_is_posted();
        when_material_service_responds();

        Thread.sleep(4000);

        then_callback_was_attempted_times(3);
    }

    private void given_i_create_a_new_subscription() throws Exception {
        String responseBody = SubscriptionStub.createSubscription(mockMvc, CLIENT_SUBSCRIPTIONS_URI, callbackBaseUrl, CALLBACK_URI);
        subscriptionId = jsonMapper.getUUIDAtPath(responseBody, "/clientSubscriptionId");
        subscriptionId = jsonMapper.getUUIDAtPath(responseBody, "/clientSubscriptionId");
        hmacKeyId = jsonMapper.getStringAtPath(responseBody, "/hmac/keyId");
        hmacSecret = jsonMapper.getStringAtPath(responseBody, "/hmac/secret");
        log.info("WireMockDebug received keyId:{} secret:{}", hmacKeyId, hmacSecret);
    }

    private void given_i_have_a_callback_endpoint() throws IOException {
        stubCallbackEndpoint(callbackWireMock, CALLBACK_URI);
    }

    private void given_material_service_returns_document_success() throws IOException {
        stubMaterialMetadata(materialId);
        stubMaterialContent(materialId);
        stubMaterialBinary(materialId);
    }

    private void given_callback_endpoint_returns_server_error() {
        stubCallbackEndpointReturnsServerError(callbackWireMock, CALLBACK_URI);
    }

    private void then_callback_was_attempted_times(int numberOfTries) {
        callbackWireMock.verify(exactly(numberOfTries), postRequestedFor(urlPathEqualTo(CALLBACK_URI)));
    }

    private void when_a_notification_event_is_posted() throws Exception {
        postNotificationEvent(eventPayloadPath).andExpect(status().isAccepted());
    }

    private ResultActions postNotificationEvent(String payloadPath) throws Exception {
        return mockMvc.perform(post(NOTIFICATIONS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .content(loadPayload(payloadPath)));
    }

    private void when_material_service_responds() {
        await()
                .pollInterval(Duration.ofMillis(50))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verify(materialClient, atLeastOnce()).getMetadata(any(UUID.class)));
    }

    @SneakyThrows
    private void then_the_subscriber_receives_a_callback() {
        log.info("sleeping for 2 second in Test ... it would of course be better to wait for something signalling callback is done");
        Thread.sleep(2000);
        callbackWireMock.verify(1, postRequestedFor(urlPathEqualTo(CALLBACK_URI)));
        callbackKeyId = getHeaderFromCallbackServeEvents(callbackWireMock, CALLBACK_URI, KEY_ID_HEADER);
        callbackSignature = getHeaderFromCallbackServeEvents(callbackWireMock, CALLBACK_URI, SIGNATURE_HEADER);
        callbackBody = getBodyFromCallbackServeEvents(callbackWireMock, CALLBACK_URI);
        callbackDocumentId = jsonMapper.getUUIDAtPath(callbackBody, "/documentId");
        callbackHearingId = jsonMapper.getUUIDAtPath(callbackBody, "/hearingId");

        log.info("WireMockDebug got callbackKeyId:{} callbackSignature:{} from callback header", callbackKeyId, callbackSignature);
    }


    private void and_the_callback_signature_is_correct() {
        assertThat(callbackKeyId).isEqualTo(hmacKeyId);
        byte[] hmacSecretBytes = encodingService.decodeFromBase64(hmacSecret);
        String calculatedSignature = hmacSigningService.sign(hmacSecretBytes, callbackBody);
        assertThat(callbackSignature).isEqualTo(calculatedSignature);
    }

    private void then_the_subscriber_can_retrieve_the_document() throws Exception {
        getDocumentAndExpectPdf(subscriptionId, callbackDocumentId);
    }

    private void getDocumentAndExpectPdf(UUID subId, UUID docId) throws Exception {
        mockMvc.perform(get(documentUri, subId, docId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/pdf")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("PrisonCourtRegister")));
    }

}
