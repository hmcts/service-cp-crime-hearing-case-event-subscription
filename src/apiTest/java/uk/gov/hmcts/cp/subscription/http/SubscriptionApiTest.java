package uk.gov.hmcts.cp.subscription.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is a bit verbose and not using sensible plugins to log, parse response etc
 * This is because these tests run inside the app context. But we dont want to bloat the app with configs or plugins
 * that are needed for api-test
 * We need to decide how to run api-tests against built docker file.
 * Probably best as a separate app with its own gradle build
 */
class SubscriptionApiTest {
    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();

    @Test
    void round_trip_subscription_should_work_ok() throws InterruptedException {
        final String postUrl = java.lang.String.format("%s/client-subscriptions", baseUrl);
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final String body = "{\"eventTypes\":[\"PCR\",\"CUSTODIAL_RESULT\"],\n" +
                "\"notificationEndpoint\":{\"webhookUrl\":\"https://my-callback-url\"}}";
        final ResponseEntity<String> postResult = http.exchange(
                postUrl,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        System.out.println("postResult:" + postResult);
        assertThat(postResult.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postResult.getBody()).contains("clientSubscriptionId");
        final String subscriptionId = postResult.getBody()
                .replaceAll(".*clientSubscriptionId\":\"", "")
                .replaceAll("\".*$", "");

        System.out.println("subscriptionId:" + subscriptionId);
        final String getUrl = String.format("%s/client-subscriptions/%s", baseUrl, subscriptionId);
        final ResponseEntity<String> getResult = http.exchange(
                getUrl,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );
        System.out.println("getres:" + getResult.getBody());
        assertThat(getResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResult.getBody()).contains("clientSubscriptionId\":\"" + subscriptionId);
    }
}