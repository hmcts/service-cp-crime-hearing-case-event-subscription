package uk.gov.hmcts.cp.subscription.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(
        classes = DocumentServiceClientIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class DocumentServiceClientIntegrationTest {

    @Configuration
    @EnableFeignClients(clients = DocumentServiceClient.class)
    @Import({
            FeignAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class
    })
    static class TestConfig {
    }

    @RegisterExtension
    static WireMockExtension wireMockServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private DocumentServiceClient documentServiceClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("document-service.url", wireMockServer::baseUrl);
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("spring.cloud.service-registry.auto-registration.enabled", () -> "false");
    }

    @Test
    void generateOpenApiModels_should_create_ErrorResponse() {
        wireMockServer.stubFor(post(urlEqualTo("//client-webhook-url"))
                .willReturn(aResponse()
                        .withStatus(204)));

        PcrEventPayload payload = new PcrEventPayload();
        payload.setEventId(UUID.randomUUID());

        documentServiceClient.updateDocumentMetadata(payload);

        wireMockServer.verify(postRequestedFor(urlEqualTo("//client-webhook-url"))
                .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void should_handle_successful_response() {
        wireMockServer.stubFor(post(urlEqualTo("//client-webhook-url"))
                .willReturn(aResponse()
                        .withStatus(200)));

        PcrEventPayload payload = new PcrEventPayload();
        payload.setEventId(UUID.randomUUID());

        documentServiceClient.updateDocumentMetadata(payload);

        wireMockServer.verify(1, postRequestedFor(
                urlEqualTo("//client-webhook-url")));
    }

    @Test
    void should_send_correct_payload_to_document_service() {
        UUID eventId = UUID.randomUUID();

        wireMockServer.stubFor(post(urlEqualTo("//client-webhook-url"))
                .willReturn(aResponse()
                        .withStatus(204)));

        PcrEventPayload payload = new PcrEventPayload();
        payload.setEventId(eventId);

        documentServiceClient.updateDocumentMetadata(payload);

        wireMockServer.verify(postRequestedFor(urlEqualTo("//client-webhook-url"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.eventId", equalTo(eventId.toString()))));
    }
}