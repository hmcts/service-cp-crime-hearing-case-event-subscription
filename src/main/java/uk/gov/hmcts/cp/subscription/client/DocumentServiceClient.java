package uk.gov.hmcts.cp.subscription.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;

@FeignClient(
        name = "document-service",
        url = "${document-service.url}"
)
public interface DocumentServiceClient {
    @PostMapping(
            value = "//client-webhook-url",
            consumes = "application/json"
    )
    void updateDocumentMetadata(@RequestBody PcrEventPayload request);
}
