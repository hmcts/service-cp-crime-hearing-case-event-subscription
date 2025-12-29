package uk.gov.hmcts.cp.subscription.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;

import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class DocumentServiceClientTest {

    @InjectMocks
    private DocumentServiceClient documentServiceClient =
            spy(DocumentServiceClient.class);

    @Test
    void updateDocumentMetadata_should_callFeignClient() {
        PcrEventPayload payload = PcrEventPayload.builder()
                .eventId(UUID.randomUUID())
                .build();

        documentServiceClient.updateDocumentMetadata(payload);

        verify(documentServiceClient, times(1))
                .updateDocumentMetadata(payload);
    }

    @Test
    void updateDocumentMetadata_should_handle_multiple_calls() {
        PcrEventPayload payload1 = PcrEventPayload.builder()
                .eventId(UUID.randomUUID())
                .build();

        PcrEventPayload payload2 = PcrEventPayload.builder()
                .eventId(UUID.randomUUID())
                .build();

        documentServiceClient.updateDocumentMetadata(payload1);
        documentServiceClient.updateDocumentMetadata(payload2);

        verify(documentServiceClient, times(1))
                .updateDocumentMetadata(payload1);
        verify(documentServiceClient, times(1))
                .updateDocumentMetadata(payload2);
    }
}
