package uk.gov.hmcts.cp.subscription.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorApiTest {

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();

    @Test
    void root_endpoint_should_be_ok() {
        final ResponseEntity<String> res = http.exchange(
                baseUrl + "/", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("DEPRECATED root endpoint");
    }

    @Test
    void health_endpoint_should_be_up() {
        final ResponseEntity<String> res = http.exchange(
                baseUrl + "/actuator/health", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }
}
