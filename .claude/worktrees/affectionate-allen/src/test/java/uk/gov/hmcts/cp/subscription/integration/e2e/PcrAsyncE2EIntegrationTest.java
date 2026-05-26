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
import uk.gov.hmcts.cp.servicebus.integration.ServiceBusTestService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusAdminService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusProcessorService;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;
import uk.gov.hmcts.cp.subscription.config.IgnoreSSLCertificatesForWiremockTest;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Objects.nonNull;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_OUTBOUND_QUEUE;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.getDocumentIdFromCallbackServeEvents;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.stubCallbackEndpoint;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.stubCallbackEndpointReturnsServerError;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialBinary;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialContent;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialMetadata;
import static uk.gov.hmcts.cp.subscription.integration.stubs.SubscriptionStub.createSubscriptionPcr;

@EnableWireMock({
        @ConfigureWireMock(name = "material-client", baseUrlProperties = "material-client.url", port = 0),
        @ConfigureWireMock(name = "callback-client", httpsBaseUrlProperties = "callback-client.url", httpsPort = 0)
})
@Import(IgnoreSSLCertificatesForWiremockTest.class)
@TestPropertySource(properties = {
        "servicebus.enabled=true",
        "service-bus.retry.msecs=0,500,1000,5000",
        "material-client.retry.intervalMilliSecs=100",
        "material-client.retry.timeoutMilliSecs=500"
})
@Slf4j
class PcrAsyncE2EIntegrationTest extends IntegrationTestBase {

    @Autowired
    ServiceBusAdminService adminService;
    @Autowired
    ServiceBusProcessorService processorService;
    @Autowired
    ServiceBusTestService testService;

    private UUID subscriptionId;
    private UUID callbackDocumentId;

    private static final UUID materialId = UUID.fromString("6c198796-08bb-4803-b456-fa0c29ca6021");
    private static final String documentUri = CLIENT_SUBSCRIPTIONS_URI + "/{clientSubscriptionId}/documents/{documentId}";
    private static final String pcrEventPayloadPath = "stubs/requests/progression/pcr-request-prison-court-register.json";

    @InjectWireMock("callback-client")
    private WireMockServer callbackWireMock;

    @Value("${callback-client.url}")
    private String callbackBaseUrl;

    @MockitoSpyBean
    private MaterialClient materialClient;

    @BeforeEach
    void setUp() {
        assumeTrue(adminService.isServiceBusReady(), "ServiceBus is not running. Run gradlew composeUp / composeDown");
        processorService.stopMessageProcessor(PCR_INBOUND_QUEUE);
        testService.dropQueueIfExists(PCR_INBOUND_QUEUE);
        adminService.createQueue(PCR_INBOUND_QUEUE);
        processorService.startMessageProcessor(PCR_INBOUND_QUEUE);

        processorService.stopMessageProcessor(PCR_OUTBOUND_QUEUE);
        testService.dropQueueIfExists(PCR_OUTBOUND_QUEUE);
        adminService.createQueue(PCR_OUTBOUND_QUEUE);
        processorService.startMessageProcessor(PCR_OUTBOUND_QUEUE);

        WireMock.reset();
        if (nonNull(callbackWireMock)) {
            callbackWireMock.resetAll();
        }
        clearAllTables();
    }

    @AfterEach
    void afterEach() {
        processorService.stopMessageProcessor(PCR_INBOUND_QUEUE);
        processorService.stopMessageProcessor(PCR_OUTBOUND_QUEUE);
    }

    @Test
    void happy_path_should_return_pdf() throws Exception {
        given_i_am_a_subscriber_with_a_subscription();
        given_i_have_a_callback_endpoint();
        given_material_service_returns_document_success();

        when_a_pcr_event_is_posted();
        when_material_service_responds();

        then_the_subscriber_receives_a_callback();
        then_the_subscriber_can_retrieve_the_document();
    }

    @Test
    void callback_client_not_responding_should_try_3_times_in_4_seconds() throws Exception {
        given_i_am_a_subscriber_with_a_subscription();
        given_callback_endpoint_returns_server_error();
        given_material_service_returns_document_success();

        when_a_pcr_event_is_posted();
        when_material_service_responds();

        Thread.sleep(4000);

        then_callback_was_attempted_times(3);
    }

    private void given_i_am_a_subscriber_with_a_subscription() throws Exception {
        createSubscription();
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

    private void when_a_pcr_event_is_posted() throws Exception {
        postPcrEvent(pcrEventPayloadPath).andExpect(status().isAccepted());
    }

    private ResultActions postPcrEvent(String payloadPath) throws Exception {
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
        callbackDocumentId = getDocumentIdFromCallbackServeEvents(callbackWireMock, CALLBACK_URI);
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

    private void createSubscription() throws Exception {
        subscriptionId = createSubscriptionPcr(mockMvc, CLIENT_SUBSCRIPTIONS_URI, callbackBaseUrl, CALLBACK_URI);
    }
}
