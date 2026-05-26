package uk.gov.hmcts.cp.hmac.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.vault.VaultServiceProperties;

import java.security.SecureRandom;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Service
@AllArgsConstructor
public class HmacKeyService {

    private static final int SECRET_BYTES_LENGTH = 32;
    public static final String STUB_KEY_ID = "kid-" + "f4f5dc10-d6d8-4e94-8b02-459c4121aad0";
    public static final String STUB_SECRET_STRING = "Stub string used purely for development purposes. To be secured.";

    private final SecureRandom secureRandom = new SecureRandom();

    private final VaultServiceProperties vaultServiceProperties;

    public KeyPair generateKey() {
        if (vaultServiceProperties.isVaultEnabled()) {
            final String keyId = "kid-v1-" + UUID.randomUUID();
            log.info("Generating new keyPair for keyId:{}", keyId);
            final byte[] secretBytes = new byte[SECRET_BYTES_LENGTH];
            secureRandom.nextBytes(secretBytes);
            return KeyPair.builder().keyId(keyId).secret(secretBytes).build();
        } else {
            return KeyPair.builder().keyId(STUB_KEY_ID).secret(STUB_SECRET_STRING.getBytes(UTF_8)).build();
        }
    }
}