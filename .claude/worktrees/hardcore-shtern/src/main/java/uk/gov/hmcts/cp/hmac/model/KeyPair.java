package uk.gov.hmcts.cp.hmac.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KeyPair {
    private String keyId;
    private byte[] secret;
}
