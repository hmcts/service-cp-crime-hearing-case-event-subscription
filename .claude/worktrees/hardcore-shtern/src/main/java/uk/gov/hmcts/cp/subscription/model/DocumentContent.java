package uk.gov.hmcts.cp.subscription.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.http.MediaType;

@Value
@Builder
public class DocumentContent {
    byte[] body;
    MediaType contentType;
    String fileName;
}
