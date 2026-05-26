package uk.gov.hmcts.cp.hmac.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Slf4j
@Service
public class HmacSigningService {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    public String sign(final byte[] secret, final String message) {
        try {
            final Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA_256));
            final byte[] hmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    public void validateSignature(final byte[] secret, final String message, final String signature) throws InvalidKeyException {
        final String expectedSignature = sign(secret, message);
        if (!expectedSignature.equals(signature)) {
            log.error("Invalid signature does not match expected");
            throw new InvalidKeyException("");
        }
    }
}