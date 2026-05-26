package uk.gov.hmcts.cp.subscription.integration.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;
import uk.gov.hmcts.cp.subscription.config.IgnoreSSLCertificatesForWiremockTest;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Objects.nonNull;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;
import static uk.gov.hmcts.cp.subscription.integration.helpers.JwtHelper.bearerTokenWithAzp;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.getDocumentIdFromCallbackServeEvents;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.stubCallbackEndpoint;
import static uk.gov.hmcts.cp.subscription.integration.stubs.CallbackStub.stubCallbackEndpointReturnsServerError;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialBinary;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialContent;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialMetadata;
import static uk.gov.hmcts.cp.subscription.integration.stubs.MaterialStub.stubMaterialMetadataNoContent;
import static uk.gov.hmcts.cp.subscription.integration.stubs.SubscriptionStub.createSubscriptionPcr;
import static uk.gov.hmcts.cp.subscription.integration.stubs.SubscriptionStub.deleteSubscription;

@EnableWireMock({
        @ConfigureWireMock(name = "material-client", baseUrlProperties = "material-client.url", port = 0),
        @ConfigureWireMock(name = "callback-client", httpsBaseUrlProperties = "callback-client.url", httpsPort = 0)
})
@Import(IgnoreSSLCertificatesForWiremockTest.class)
@TestPropertySource(properties = {
        "subscription.oauth-enabled=true",
        "service-bus.enabled=false",
        "material-client.retry.intervalMilliSecs=100",
        "material-client.retry.timeoutMilliSecs=500"
})
class PcrSynchronousE2EIntegrationTest extends IntegrationTestBase {

    private UUID subscriptionId;
    private UUID otherSubscriptionId;
    private UUID lateSubscriptionId;
    private UUID callbackDocumentId;
    private static final String correlationId = String.valueOf(UUID.randomUUID());
    private static final UUID MATERIAL_ID = UUID.fromString("6c198796-08bb-4803-b456-fa0c29ca6021");
    private static final String DOCUMENT_URI = CLIENT_SUBSCRIPTIONS_URI + "/{clientSubscriptionId}/documents/{documentId}";
    private static final String PCR_EVENT_PAYLOAD_PATH = "stubs/requests/progression/pcr-request-prison-court-register.json";
    private static final String PCR_EVENT_TIMEOUT_PATH = "stubs/requests/progression/pcr-request-material-timeout.json";
    private static final String CALLBACK_URI_OTHER = "/callback/other";
    private static final String CALLBACK_URI_LATE = "/callback/late";

    private static final String CLIENT_ID_OTHER = "22222222-2222-3333-4444-555555555555";
    private static final String CLIENT_ID_LATE = "33333333-2222-3333-4444-555555555555";


    @InjectWireMock("callback-client")
    private WireMockServer callbackWireMock;

    @Value("${callback-client.url}")
    private String callbackBaseUrl;

    @MockitoSpyBean
    private MaterialClient materialClient;

    @BeforeEach
    void setUp() {
        WireMock.reset();
        if (nonNull(callbackWireMock)) {
            callbackWireMock.resetAll();
        }
        clearAllTables();
    }

    @Test
    void document_retrieval_success_should_return_pdf() throws Exception {
        given_i_am_a_subscriber_with_a_subscription();
        given_i_have_a_callback_endpoint();
        given_material_service_returns_document_success();

        when_a_pcr_event_is_posted();
        when_material_service_responds();

        then_the_subscriber_receives_a_callback();
        then_the_subscriber_can_retrieve_the_document();
    }

    @Test
    void material_not_ready_should_not_send_callback_and_return_504() throws Exception {
        given_i_am_a_subscriber_with_a_subscription();
        given_i_have_a_callback_endpoint();
        given_material_service_returns_document_not_found();

        when_a_pcr_event_is_posted_with_timeout();
        when_material_service_responds();

        then_the_material_api_was_polled();
        then_the_subscriber_does_not_receive_a_callback();
    }

    @Test
    void callback_client_not_responding_should_return_504() throws Exception {
        given_i_am_a_subscriber_with_a_subscription();
        given_callback_endpoint_returns_server_error();
        given_material_service_returns_document_success();

        when_a_pcr_event_is_posted_expect_callback_delivery_timeout();

        then_callback_was_attempted();
    }

    @Test
    void subscriber_lost_access_after_pcr_delivered_should_return_403() throws Exception {
        given_i_am_a_subscriber_with_a_subscription();
        given_i_have_a_callback_endpoint();
        given_material_service_returns_document_success();

        when_a_pcr_event_is_posted();
        when_material_service_responds();
        then_the_subscriber_receives_a_callback();

        when_subscriber_loses_access();

        then_subscriber_cannot_retrieve_document();
    }

    @Test
    void other_subscription_without_pcr_attempting_document_retrieval_should_return_403() throws Exception {
        given_i_am_a_subscriber_with_a_subscription();
        given_i_have_a_callback_endpoint();
        given_material_service_returns_document_success();
        given_another_subscription_with_custodial_only();

        when_a_pcr_event_is_posted();
        when_material_service_responds();
        then_the_subscriber_receives_a_callback();

        then_other_subscription_cannot_retrieve_document();
    }

    @Test
    void late_subscriber_with_pcr_should_retrieve_document_when_access_is_by_event_type() throws Exception {
        given_i_am_a_subscriber_with_a_subscription();
        given_i_have_a_callback_endpoint();
        given_material_service_returns_document_success();

        when_a_pcr_event_is_posted();
        when_material_service_responds();
        then_the_subscriber_receives_a_callback();
        then_the_subscriber_can_retrieve_the_document();

        given_late_subscriber_with_pcr();

        then_late_subscriber_can_retrieve_document();
    }

    private void given_i_am_a_subscriber_with_a_subscription() throws Exception {
        createSubscription();
    }

    private void given_i_have_a_callback_endpoint() throws IOException {
        stubCallbackEndpoint(callbackWireMock, CALLBACK_URI);
    }

    private void given_material_service_returns_document_success() throws IOException {
        stubMaterialMetadata(MATERIAL_ID);
        stubMaterialContent(MATERIAL_ID);
        stubMaterialBinary(MATERIAL_ID);
    }

    private void given_material_service_returns_document_not_found() {
        stubMaterialMetadataNoContent(MATERIAL_ID_TIMEOUT);
    }

    private void given_callback_endpoint_returns_server_error() {
        stubCallbackEndpointReturnsServerError(callbackWireMock, CALLBACK_URI);
    }

    private void when_a_pcr_event_is_posted_expect_callback_delivery_timeout() throws Exception {
        postPcrEvent(PCR_EVENT_PAYLOAD_PATH)
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("gateway_timeout"))
                .andExpect(jsonPath("$.message").value("Callback is not ready"));
    }

    private void then_callback_was_attempted() {
        callbackWireMock.verify(moreThanOrExactly(1), postRequestedFor(urlPathEqualTo(CALLBACK_URI)));
    }

    private void when_a_pcr_event_is_posted() throws Exception {
        postPcrEvent(PCR_EVENT_PAYLOAD_PATH).andExpect(status().isAccepted());
    }

    private void when_a_pcr_event_is_posted_with_timeout() throws Exception {
        postPcrEvent(PCR_EVENT_TIMEOUT_PATH)
                .andDo(print())
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("gateway_timeout"))
                .andExpect(jsonPath("$.message").value("Material metadata not ready"));
    }

    private ResultActions postPcrEvent(String payloadPath) throws Exception {
        return mockMvc.perform(post(NOTIFICATIONS_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .header(CORRELATION_ID_KEY, correlationId)
                        .content(loadPayload(payloadPath)));
    }

    private void when_material_service_responds() {
        await()
                .pollInterval(Duration.ofMillis(50))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verify(materialClient, atLeastOnce()).getMetadata(any(UUID.class)));
    }

    private void then_the_material_api_was_polled() {
        verify(materialClient, atLeastOnce()).getMetadata(eq(MATERIAL_ID_TIMEOUT));
    }

    private void then_the_subscriber_receives_a_callback() {
        callbackWireMock.verify(1, postRequestedFor(urlPathEqualTo(CALLBACK_URI)));
        callbackDocumentId = getDocumentIdFromCallbackServeEvents(callbackWireMock, CALLBACK_URI);
    }

    private void then_the_subscriber_does_not_receive_a_callback() {
        callbackWireMock.verify(0, postRequestedFor(urlPathEqualTo(CALLBACK_URI)));
    }

    private void then_the_subscriber_can_retrieve_the_document() throws Exception {
        getDocumentAndExpectPdf(subscriptionId, callbackDocumentId);
    }

    private void when_subscriber_loses_access() throws Exception {
        deleteSubscription(mockMvc, CLIENT_SUBSCRIPTIONS_URI, subscriptionId)
                .andExpect(status().isNoContent());
    }

    private void then_subscriber_cannot_retrieve_document() throws Exception {
        mockMvc.perform(get(DOCUMENT_URI, subscriptionId, callbackDocumentId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("Access denied: subscription does not have access to this document"));
    }

    private void getDocumentAndExpectPdf(UUID subId, UUID docId) throws Exception {
        mockMvc.perform(get(DOCUMENT_URI, subId, docId)
                        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
                        .header(CORRELATION_ID_KEY, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/pdf")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("PrisonCourtRegister")))
                .andExpect(header().string(CORRELATION_ID_KEY, correlationId));
    }

    private void getDocumentAndExpectPdf(UUID subId, UUID docId, String clientId) throws Exception {
        mockMvc.perform(get(DOCUMENT_URI, subId, docId)
                        .header("Authorization", bearerTokenWithAzp(clientId))
                        .header(CORRELATION_ID_KEY, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/pdf")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("PrisonCourtRegister")))
                .andExpect(header().string(CORRELATION_ID_KEY, correlationId));
    }

    private void createSubscription() throws Exception {
        subscriptionId = createSubscriptionPcr(mockMvc, CLIENT_SUBSCRIPTIONS_URI, callbackBaseUrl, CALLBACK_URI);
    }

    private void given_another_subscription_with_custodial_only() {
        // Directly insert a subscription with no event types to simulate a client
        // that has no access to PCR documents, bypassing API validation.
        otherSubscriptionId = insertSubscription(
                UUID.fromString(CLIENT_ID_OTHER), List.of(), callbackBaseUrl + CALLBACK_URI_OTHER).getId();
    }

    private void given_late_subscriber_with_pcr() throws Exception {
        lateSubscriptionId = createSubscriptionPcr(mockMvc, CLIENT_SUBSCRIPTIONS_URI, callbackBaseUrl, CALLBACK_URI_LATE, CLIENT_ID_LATE);
    }

    private void then_late_subscriber_can_retrieve_document() throws Exception {
        getDocumentAndExpectPdf(lateSubscriptionId, callbackDocumentId, CLIENT_ID_LATE);
    }

    private void then_other_subscription_cannot_retrieve_document() throws Exception {
        mockMvc.perform(get(DOCUMENT_URI, otherSubscriptionId, callbackDocumentId)
                        .header("Authorization", bearerTokenWithAzp(CLIENT_ID_OTHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("Access denied: subscription does not have access to this document"));
    }
}
