package uk.gov.hmcts.cp.subscription.clients;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;

@ExtendWith(MockitoExtension.class)
class MaterialClientTest {

    @Mock
    RestTemplate restTemplate;

    MaterialClient materialClient;

    @BeforeEach
    void setUp() {
        materialClient = new MaterialClient(restTemplate, "http://material-service", "11111111-2222-3333-4444-666666666666");
    }

    @Test
    void get_content_url_should_return_blob_url() {
        UUID materialId = UUID.randomUUID();
        String blobUrl = "https://blob.core.windows.net/container/file.pdf?sig=abc";
        when(restTemplate.exchange(anyString(), eq(GET), any(HttpEntity.class), eq(String.class), eq(materialId)))
                .thenReturn(ResponseEntity.ok(blobUrl));

        String result = materialClient.getContentUrl(materialId);

        assertThat(result).isEqualTo(blobUrl);
    }
}
