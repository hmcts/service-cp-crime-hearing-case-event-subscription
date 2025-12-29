package uk.gov.hmcts.cp.subscription.services;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;
import uk.gov.hmcts.cp.subscription.client.DocumentServiceClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final DocumentServiceClient client;

    public void processPcrEvent(final PcrEventPayload payload) {
        log.info("Processing invoice webhook event: {}", payload);

        try {
            client.updateDocumentMetadata(payload);
            log.info("Successfully updated document metadata for invoice event");
        } catch (FeignException ex) {
            log.error("Failed to update document metadata for invoice event", ex);
            throw ex;
        }
    }
}
