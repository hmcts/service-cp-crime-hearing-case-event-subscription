package uk.gov.hmcts.cp.hmac.services;

import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class EncodingService {

    public String encodeWithBase64(final byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public byte[] decodeFromBase64(final String encodedString) {
        return Base64.getDecoder().decode(encodedString);
    }
}