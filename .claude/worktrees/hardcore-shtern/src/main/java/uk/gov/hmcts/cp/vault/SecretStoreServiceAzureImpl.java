package uk.gov.hmcts.cp.vault;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.InvalidKeyException;
import java.util.Optional;

@Slf4j
@Service
public class SecretStoreServiceAzureImpl implements SecretStoreServiceInterface {

    public static final String SECRET_PREFIX = "hces";
    public static final String SECRET_SUFFIX = "hmac";

    private SecretClient secretClient;

    public SecretStoreServiceAzureImpl(final VaultServiceProperties vaultServiceProperties) {
        if (vaultServiceProperties.isVaultEnabled()) {
            final DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
                    .managedIdentityClientId(vaultServiceProperties.getVaultClientId().toString())
                    .build();
            this.secretClient = new SecretClientBuilder()
                    .vaultUrl(vaultServiceProperties.getVaultUri())
                    .credential(credential)
                    .buildClient();
        }
    }

    @Override
    public Optional<String> getSecret(final String secretName) {
        try {
            final KeyVaultSecret secret = secretClient.getSecret(getFullSecretName(secretName));
            return Optional.of(secret.getValue());
        } catch (ResourceNotFoundException e) {
            log.error("Secret not found");
            return Optional.empty();
        }
    }

    @Override
    public void setSecret(final String secretName, final String secretValue) {
        secretClient.setSecret(new KeyVaultSecret(getFullSecretName(secretName), secretValue));
    }

    @SneakyThrows
    @Override
    public String getFullSecretName(final String secretName) {
        if (secretName.matches("^[a-zA-Z0-9\\-]+$")) {
            return String.format("%s-%s-%s", SECRET_PREFIX, secretName, SECRET_SUFFIX);
        }
        throw new InvalidKeyException("Secret name can only contain alphanumerics plus \"-\"");
    }
}
