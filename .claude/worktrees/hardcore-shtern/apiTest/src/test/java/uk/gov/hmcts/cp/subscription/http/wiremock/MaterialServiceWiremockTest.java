package uk.gov.hmcts.cp.subscription.http.wiremock;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import uk.gov.hmcts.cp.subscription.http.util.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/* This might seem a little odd ... testing the test framework but as well as being sure about what
   endpoints we expose, it also services to document our mocked endpoints
 */
@Slf4j
class MaterialServiceWiremockTest {
    private String wiremockRequestsUrl = "http://localhost:8090/__admin/requests";
    private String baseUrl = "http://localhost:8090";
    private String CORRELATION_ID_KEY = "X-Correlation-Id";
    private RestClient restClient = RestClient.create();
    private JsonMapper jsonMapper = new JsonMapper();

    UUID correlationId = UUID.randomUUID();
    UUID materialId = UUID.fromString("7998ebac-1bd0-4079-b4ca-1151aa16157c");

    @BeforeEach
    void beforeEach() {
        clearWireMockMessages();
    }

    @Test
    void material_metadata_should_return() {
        ResponseEntity<String> response = restClient
                .get()
                .uri(metaDataUrl(materialId))
                .header(CORRELATION_ID_KEY, correlationId.toString())
                .retrieve()
                .toEntity(String.class);
        log.info("material metadata response:{}", response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void material_metadata_should_timeout() {
        UUID timeoutMaterialId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        ResponseEntity<String> response = restClient
                .get()
                .uri(metaDataUrl(timeoutMaterialId))
                .header(CORRELATION_ID_KEY, correlationId.toString())
                .retrieve()
                .toEntity(String.class);
        log.info("material metadata timeout response:{}", response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void material_full_content_with_url_should_return() {
        ResponseEntity<String> response = restClient
                .get()
                .uri(contentUrl(materialId))
                .header(CORRELATION_ID_KEY, correlationId.toString())
                .retrieve()
                .toEntity(String.class);
        log.info("material content response:{}", response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode jsonNode = jsonMapper.toJsonNode(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String expectedContentUrl = "\"http://localhost:8090/material-query-api/query/api/rest/material/material/6c198796-08bb-4803-b456-fa0c29ca6021/binary\"";
        assertThat(jsonNode.get("contentUrl").toString()).isEqualTo(expectedContentUrl);
        // TODO add response transformer to correctly set the materialId as per the request
        assertWireMockMessage("/material-query-api/query/api/rest/material/material/7998ebac-1bd0-4079-b4ca-1151aa16157c/content");
    }

    private String metaDataUrl(UUID materialId) {
        String url = String.format("%s/material-query-api/query/api/rest/material/material/%s/metadata", baseUrl, materialId);
        log.info("metaDataUrl:{}", url);
        return url;
    }

    private String contentUrl(UUID materialId) {
        String url = String.format("%s/material-query-api/query/api/rest/material/material/%s/content", baseUrl, materialId);
        log.info("metaDataUrl:{}", url);
        return url;
    }

    private void clearWireMockMessages() {
        ResponseEntity<String> response = restClient
                .delete()
                .uri(wiremockRequestsUrl)
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void assertWireMockMessage(String expectedUrl) {
        ResponseEntity<String> response = restClient
                .get()
                .uri(wiremockRequestsUrl)
                .retrieve()
                .toEntity(String.class);
        log.info("Wiremock {}", response.getBody());

        int requestCount = jsonMapper.getIntAtPath(response.getBody(), "/meta/total");
        String lastUrl = jsonMapper.getStringAtPath(response.getBody(), String.format("/requests/%d/request/url", requestCount - 1));
        String lastCorrelationId = jsonMapper.getStringAtPath(response.getBody(), String.format("/requests/%d/request/headers/x-correlation-id", requestCount - 1));
        int lastStatus = jsonMapper.getIntAtPath(response.getBody(), String.format("/requests/%d/response/status", requestCount - 1));
        assertThat(lastUrl).isEqualTo(expectedUrl);
        assertThat(lastStatus).isEqualTo(200);
        assertThat(lastCorrelationId).isNotEmpty();
    }
}
