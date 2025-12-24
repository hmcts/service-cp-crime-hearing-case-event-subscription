package uk.gov.hmcts.cp.subscription.services;

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
import uk.gov.hmcts.cp.subscription.client.DocumentServiceClient;

import java.util.UUID;


import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(
        classes = WebhookInvoiceServiceTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class WebhookInvoiceServiceTest {

    @Configuration
    @EnableFeignClients(clients = DocumentServiceClient.class)
    @Import({
            FeignAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            WebhookInvoiceService.class
    })
    static class TestConfig {
    }

    @RegisterExtension
    static WireMockExtension wireMockServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("document-service.url", wireMockServer::baseUrl);
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("spring.cloud.service-registry.auto-registration.enabled", () -> "false");
    }

    @Autowired
    private WebhookInvoiceService webhookInvoiceService;

    @Test
    void shouldCallDocumentServiceToUpdateMetadata() {
        wireMockServer.stubFor(post(urlEqualTo("/document-service/api/rest/document/metadata"))
                .willReturn(aResponse()
                        .withStatus(200)));

        PcrEventPayload payload = new PcrEventPayload();
        payload.setEventId(UUID.randomUUID());

        webhookInvoiceService.processPcrEvent(payload);

        wireMockServer.verify(postRequestedFor(
                urlEqualTo("/document-service/api/rest/document/metadata"))
                .withHeader("Content-Type", containing("application/json")));
    }
}