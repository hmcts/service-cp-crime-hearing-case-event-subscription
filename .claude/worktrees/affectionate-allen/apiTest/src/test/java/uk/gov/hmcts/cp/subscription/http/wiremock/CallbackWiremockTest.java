package uk.gov.hmcts.cp.subscription.http.wiremock;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/* This might seem a little odd ... testing the test framework but as well as being sure about what
   endpoints we expose, it also services to document our mocked endpoints
 */
@Slf4j
class CallbackWiremockTest {
    private static final String CORRELATION_ID_KEY = "X-Correlation-Id";

    private RestClient restClient = RestClient.create();
    private String wireMockBaseUrl = "http://localhost:8090";
    private String callbackUrl = "/callback/notify";

    UUID correlationId = UUID.randomUUID();

    @Test
    void callback() {
        ResponseEntity<String> response = restClient
                .post()
                .uri(wireMockBaseUrl + callbackUrl)
                .header(CORRELATION_ID_KEY, correlationId.toString())
                .retrieve()
                .toEntity(String.class);
        log.info("callback response:{}", response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
