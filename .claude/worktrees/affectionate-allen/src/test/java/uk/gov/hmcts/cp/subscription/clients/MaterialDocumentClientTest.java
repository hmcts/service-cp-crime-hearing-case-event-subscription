package uk.gov.hmcts.cp.subscription.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;

@ExtendWith(MockitoExtension.class)
class MaterialDocumentClientTest {

    private static final URI DOCUMENT_URI = URI.create("https://blob.core.windows.net/container/file.pdf?sig=abc");

    @Mock
    RestTemplate restTemplate;

    @InjectMocks
    MaterialDocumentClient materialDocumentClient;

    @Test
    void get_material_document_should_return_response_from_rest_template() {
        byte[] documentBytes = "pdf-content".getBytes();
        ResponseEntity<byte[]> expected = ResponseEntity.ok(documentBytes);
        when(restTemplate.exchange(eq(DOCUMENT_URI), eq(GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(expected);

        ResponseEntity<byte[]> result = materialDocumentClient.getMaterialDocument(DOCUMENT_URI);

        assertThat(result).isEqualTo(expected);
    }
}
