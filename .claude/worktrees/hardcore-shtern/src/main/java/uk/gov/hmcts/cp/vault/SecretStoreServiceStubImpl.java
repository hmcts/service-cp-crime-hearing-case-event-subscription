package uk.gov.hmcts.cp.vault;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.hmac.services.EncodingService;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Optional;

import static uk.gov.hmcts.cp.vault.SecretStoreServiceAzureImpl.SECRET_PREFIX;

@Slf4j
@Service
@AllArgsConstructor
public class SecretStoreServiceStubImpl implements SecretStoreServiceInterface {

    private EncodingService encodingService;

    public Optional<String> getSecret(final String secretName) {
        final String encoded = encodingService.encodeWithBase64(secretName.getBytes(StandardCharsets.UTF_8));
        return Optional.of(encoded);
    }

    public void setSecret(final String secretName, final String secretValue) {
        // Do nothing we stub the secret to be
    }

    @SneakyThrows
    @Override
    public String getFullSecretName(final String secretName) {
        if (secretName.matches("^[a-zA-Z0-9\\-]+$")) {
            return String.format("%s-%s", SECRET_PREFIX, secretName);
        }
        throw new InvalidKeyException("Secret name can only contain alphanumerics plus \"-\"");
    }
}
