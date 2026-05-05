package uk.gov.hmcts.cp.hmac.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.vault.VaultServiceProperties;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class HmacKeyService {

    private static final int SECRET_BYTES_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final VaultServiceProperties vaultServiceProperties;

    public KeyPair generateKey() {
        final String keyId = "kid-v1-" + UUID.randomUUID();
        log.info("Generating new keyPair for keyId:{}", keyId);
        final byte[] secretBytes = new byte[SECRET_BYTES_LENGTH];
        secureRandom.nextBytes(secretBytes);
        return KeyPair.builder().keyId(keyId).secret(secretBytes).build();
    }

    public byte[] generateSecretBytes() {
        log.info("Generating secrets bytes.");
        final byte[] secretBytes = new byte[SECRET_BYTES_LENGTH];
        secureRandom.nextBytes(secretBytes);
        return secretBytes;
    }
}