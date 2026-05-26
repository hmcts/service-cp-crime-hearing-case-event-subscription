package uk.gov.hmcts.cp.subscription.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.cp.openapi.model.EventNotificationPayload;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventNotificationPayloadWrapper {
    public static final String KEY_ID_HEADER = "X-Key-Id";
    public static final String SIGNATURE_HEADER = "X-Signature";

    private String keyId;
    private String signature;
    private EventNotificationPayload payload;
}
