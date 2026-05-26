package uk.gov.hmcts.cp.subscription.clients;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.subscription.model.MaterialMetadata;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpMethod.GET;

@Service
@Slf4j
public class MaterialClient {

    public static final String CONTENT_PATH = "/material-query-api/query/api/rest/material/material/{materialId}/content";
    public static final String METADATA_PATH = "/material-query-api/query/api/rest/material/material/{materialId}/metadata";
    public static final String CJSCPPUID_HEADER = "CJSCPPUID";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String cjscppuid;

    public MaterialClient(
            final RestTemplate restTemplate,
            @Value("${material-client.url}") final String baseUrl,
            @Value("${material-client.cjscppuid}") final String cjscppuid) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.cjscppuid = cjscppuid;
    }

    public MaterialMetadata getMetadata(final UUID materialId) {
        log.info("Getting metadata for materialId:{}", materialId);
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID_HEADER, cjscppuid);
        headers.set(ACCEPT, "application/vnd.material.query.material-metadata+json");
        final HttpEntity<Void> req = new HttpEntity<>(headers);
        final ResponseEntity<MaterialMetadata> response = restTemplate.exchange(
                baseUrl + METADATA_PATH, GET, req, MaterialMetadata.class, materialId);
        return response.getBody();
    }

    public String getContentUrl(final UUID materialId) {
        log.info("Getting content URL for materialId:{}", materialId);
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID_HEADER, cjscppuid);
        headers.set(ACCEPT, "application/vnd.material.query.material+json");
        final HttpEntity<Void> req = new HttpEntity<>(headers);
        final ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + CONTENT_PATH, GET, req, String.class, materialId);
        return response.getBody();
    }

}
