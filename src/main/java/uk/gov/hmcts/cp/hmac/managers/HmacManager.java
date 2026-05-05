package uk.gov.hmcts.cp.hmac.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.hmac.services.EncodingService;
import uk.gov.hmcts.cp.hmac.services.HmacKeyService;
import uk.gov.hmcts.cp.hmac.services.HmacSigningService;
import uk.gov.hmcts.cp.vault.SecretStoreServiceInterface;

@Slf4j
@Service
@AllArgsConstructor
public class HmacManager {

    public static final String KEY_PREFIX = "kid-";

    private HmacKeyService hmacKeyService;
    private SecretStoreServiceInterface secretStoreService;
    private HmacSigningService hmacSigningService;
    private EncodingService encodingService;

    public KeyPair createAndStoreNewKey() {
        final KeyPair keyPair = hmacKeyService.generateKey();
        log.info("createAndStoreNewKey keyId:{}", keyPair.getKeyId());
        final String encodedSecret = encodingService.encodeWithBase64(keyPair.getSecret());
        secretStoreService.setSecret(keyPair.getKeyId(), encodedSecret);
        return keyPair;
    }

    public String rotateSecret(final String keyId) {
        log.info("rotateSecret keyId:{}", keyId);
        final byte[] newSecretBytes = hmacKeyService.generateSecretBytes();
        final String encodedSecret = encodingService.encodeWithBase64(newSecretBytes);
        secretStoreService.setSecret(keyId, encodedSecret);
        return encodedSecret;
    }

    public String calculateSignature(final String keyId, final String payload) {
        log.info("calculateSignature for keyId:{}", keyId);
        final String encodedSecret = secretStoreService.getSecret(keyId).
                orElseThrow(() -> new EntityNotFoundException("no existing secret for keyId:" + keyId));
        final byte[] secret = encodingService.decodeFromBase64(encodedSecret);
        return hmacSigningService.sign(secret, payload);
    }
}