package uk.gov.hmcts.cp.subscription.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorApiTest {

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestClient restClient = RestClient.create();

    @Test
    void health_endpoint_should_be_up() {
        final var res = restClient.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .toEntity(String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }
}
