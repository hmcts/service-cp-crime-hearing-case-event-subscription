package uk.gov.hmcts.cp.subscription.services;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;
import uk.gov.hmcts.cp.subscription.BaseIntegrationTest;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WebhookServiceIntegrationTest extends BaseIntegrationTest {

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
    private WebhookService webhookService;

    @Test
    void should_call_document_service_to_update_metadata() {
        wireMockServer.stubFor(post(urlPathMatching("/document-service/api/rest/document/.*"))
                .willReturn(aResponse().withStatus(200)));

        PcrEventPayload payload = PcrEventPayload.builder()
                .eventId(UUID.randomUUID())
                .build();

        webhookService.processPcrEvent(payload);

        wireMockServer.verify(postRequestedFor(
                urlPathMatching("/document-service/api/rest/document/.*"))
                .withHeader("Content-Type", containing("application/json")));
    }
}
