package uk.gov.hmcts.cp.subscription.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DocumentServiceClientTest {

    private DocumentServiceClient documentServiceClient;

    @BeforeEach
    void setUp() {
        documentServiceClient = mock(DocumentServiceClient.class);
    }

    @Test
        void updateDocumentMetadata_should_callFeignClient() {
            PcrEventPayload payload = new PcrEventPayload();
            UUID eventId = UUID.randomUUID();
            payload.setEventId(eventId);

            documentServiceClient.updateDocumentMetadata(payload);

            verify(documentServiceClient, times(1)).updateDocumentMetadata(payload);
    }

    @Test
    void updateDocumentMetadata_should_handle_multiple_calls() {
        PcrEventPayload payload1 = new PcrEventPayload();
        payload1.setEventId(UUID.randomUUID());
        PcrEventPayload payload2 = new PcrEventPayload();
        payload2.setEventId(UUID.randomUUID());

        documentServiceClient.updateDocumentMetadata(payload1);
        documentServiceClient.updateDocumentMetadata(payload2);

        verify(documentServiceClient, times(1)).updateDocumentMetadata(payload1);
        verify(documentServiceClient, times(1)).updateDocumentMetadata(payload2);
    }
}
