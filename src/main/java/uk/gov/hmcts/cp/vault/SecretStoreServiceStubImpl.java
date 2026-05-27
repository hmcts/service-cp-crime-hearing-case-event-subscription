package uk.gov.hmcts.cp.vault;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.InvalidKeyException;
import java.util.Optional;

import static uk.gov.hmcts.cp.vault.SecretStoreServiceAzureImpl.SECRET_PREFIX;

@Slf4j
@Service
@AllArgsConstructor
public class SecretStoreServiceStubImpl implements SecretStoreServiceInterface {

    public static String encodedSecret;

    @Override
    public Optional<String> getSecret(final String secretName) {
        log.warn("WARNING SecretService is stubbed. Do not use in real environments");
        return Optional.of(encodedSecret);
    }

    @Override
    public void setSecret(final String secretName, final String secretValue) {
        log.warn("WARNING SecretService is stubbed. Do not use in real environments");
        encodedSecret = secretValue;
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
