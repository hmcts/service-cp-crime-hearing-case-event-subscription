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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(
        classes = DocumentServiceClientTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class DocumentServiceClientTest {

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
    void shouldSendPostRequestToDocumentService() {
        wireMockServer.stubFor(post(urlEqualTo("/document-service/api/rest/document/metadata"))
                .willReturn(aResponse()
                        .withStatus(204)));

        PcrEventPayload payload = new PcrEventPayload();
        payload.setEventId(UUID.randomUUID());

        documentServiceClient.updateDocumentMetadata(payload);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/document-service/api/rest/document/metadata"))
                .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void shouldHandleSuccessfulResponse() {
        wireMockServer.stubFor(post(urlEqualTo("/document-service/api/rest/document/metadata"))
                .willReturn(aResponse()
                        .withStatus(200)));

        PcrEventPayload payload = new PcrEventPayload();
        payload.setEventId(UUID.randomUUID());

        documentServiceClient.updateDocumentMetadata(payload);

        wireMockServer.verify(1, postRequestedFor(
                urlEqualTo("/document-service/api/rest/document/metadata")));
    }

    @Test
    void shouldSendCorrectPayloadToDocumentService() {
        UUID eventId = UUID.randomUUID();

        wireMockServer.stubFor(post(urlEqualTo("/document-service/api/rest/document/metadata"))
                .willReturn(aResponse()
                        .withStatus(204)));

        PcrEventPayload payload = new PcrEventPayload();
        payload.setEventId(eventId);

        documentServiceClient.updateDocumentMetadata(payload);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/document-service/api/rest/document/metadata"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.eventId", equalTo(eventId.toString()))));
    }
}