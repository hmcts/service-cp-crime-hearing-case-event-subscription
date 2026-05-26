package uk.gov.hmcts.cp.subscription.http;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.subscription.http.util.JsonMapper;
import uk.gov.hmcts.cp.subscription.http.util.JwtHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class BaseTest {
    public static final String AUTHORIZATION = "Authorization";
    public static final String CORRELATION_ID_KEY = "X-Correlation-Id";

    protected JsonMapper jsonMapper = new JsonMapper();
    protected RestClient restClient = RestClient.create();
    protected String subscriptionsBaseUrl = "http://localhost:8082";

    @SneakyThrows
    UUID createOrGetSubscription(UUID clientId) {
        // To maker this idempotent we catch the 409 use the subscriptionId in the response.
        // i.e. From 409 Conflict: "{"error":"invalid_request","message":"subscription already exist with 215767e1-3da3-470e-b8aa-f5da1d79a064"}
        try {
            return createSubscription(clientId, "https://mycallback");
        } catch (HttpClientErrorException e) {
            log.info("Subscription already exists ... trying to parse subscriptionId from:{}", e.getMessage());
            JsonNode jsonNode = new JsonMapper().toJsonNode(e.getResponseBodyAsString());
            String message = String.valueOf(jsonNode.get("message"));
            String subscriptionIdString = message.replaceAll("subscription already exist with ", "").replaceAll("\"", "");
            UUID subscriptionId = UUID.fromString(subscriptionIdString);
            return getSubscription(clientId, subscriptionId);
        }
    }

    protected UUID createSubscription(UUID clientId, String callbackUrl) {
        String bearerToken = JwtHelper.bearerTokenWithAzp(clientId);
        ResponseEntity<String> response = restClient.post()
                .uri(subscriptionsBaseUrl + "/client-subscriptions")
                .header(AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(subscriptionRequestBody(callbackUrl))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String hmacKeyId = jsonMapper.getStringAtPath(response.getBody(), "/hmac/keyId");
        assertThat(hmacKeyId).matches("kid-[a-zA-Z0-9\\-]*");
        String hmacSecret = jsonMapper.getStringAtPath(response.getBody(), "/hmac/secret");
        assertThat(hmacSecret).isEqualTo("U3R1YiBzdHJpbmcgdXNlZCBwdXJlbHkgZm9yIGRldmVsb3BtZW50IHB1cnBvc2VzLiBUbyBiZSBzZWN1cmVkLg==");
        return jsonMapper.getUUIDAtPath(response.getBody(), "/clientSubscriptionId");
    }

    private UUID getSubscription(UUID clientId, UUID subscriptionId) {
        String bearerToken = JwtHelper.bearerTokenWithAzp(clientId);
        final String getUrl = String.format("%s/client-subscriptions/%s", subscriptionsBaseUrl, subscriptionId);
        ResponseEntity<String> response = RestClient.builder().baseUrl(getUrl)
                .defaultHeader(AUTHORIZATION, bearerToken)
                .build()
                .get()
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return jsonMapper.getUUIDAtPath(response.getBody(), "/clientSubscriptionId");
    }

    protected void deleteSubscription(UUID clientId, UUID subscriptionId) {
        String bearerToken = JwtHelper.bearerTokenWithAzp(clientId);
        ResponseEntity<String> response = restClient.delete()
                .uri(subscriptionsBaseUrl + "/client-subscriptions/" + subscriptionId)
                .header(AUTHORIZATION, bearerToken)
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @SneakyThrows
    private String subscriptionRequestBody(String callbackUrl) {
        String filesBase = "./src/test/resources/files/";
        String json = Files.readString(Path.of(filesBase + "subscription-request.json"));
        return json.replace("https://my-callback-url", callbackUrl);
    }
}
