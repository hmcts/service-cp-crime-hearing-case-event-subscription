package uk.gov.hmcts.cp.subscription.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class RootApiTest {

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestClient restClient = RestClient.create();

    @Test
    void root_endpoint_should_be_ok() throws InterruptedException {
        final var res = restClient.get()
                .uri(baseUrl + "/")
                .retrieve()
                .toEntity(String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("DEPRECATED root endpoint");
    }
}
