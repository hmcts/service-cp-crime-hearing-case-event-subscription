package uk.gov.hmcts.cp.subscription.integration.stubs;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.springframework.core.io.ClassPathResource;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Objects.nonNull;

/**
 * WireMock stub for the callback endpoint (event notification delivery).
 * Use with the callback-client WireMock server (e.g. from @EnableWireMock).
 */
public final class CallbackStub {

    static JsonMapper jsonMapper = new JsonMapper();
    private static final String CALLBACK_RESPONSE_PATH = "wiremock/callback-client/files/callback-accepted.json";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CONTENT_TYPE = "Content-Type";

    public static void stubCallbackEndpoint(WireMockServer server, String callbackUri) throws IOException {
        String body = new ClassPathResource(CALLBACK_RESPONSE_PATH).getContentAsString(StandardCharsets.UTF_8);
        server.stubFor(post(urlPathEqualTo(callbackUri))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(body)));
    }

    public static void stubCallbackEndpointReturnsServerError(WireMockServer server, String callbackUri) {
        server.stubFor(post(urlPathEqualTo(callbackUri))
                .willReturn(aResponse().withStatus(500)));
    }

    public static UUID getDocumentIdFromCallbackServeEvents(WireMockServer server, String callbackUri) {
        return server.getAllServeEvents().stream()
                .map(ServeEvent::getRequest)
                .filter(r -> nonNull(r.getUrl()) && r.getUrl().contains(callbackUri))
                .map(r -> parseDocumentIdFromBody(r.getBodyAsString()))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Callback request body did not contain documentId"));
    }

    private static UUID parseDocumentIdFromBody(String body) {
        return jsonMapper.getUUIDAtPath(body, "/documentId");
    }
}
