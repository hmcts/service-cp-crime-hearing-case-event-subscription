package uk.gov.hmcts.cp.vault;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static uk.gov.hmcts.cp.vault.SecretStoreServiceAzureImpl.SECRET_PREFIX;

@Slf4j
@Service
@AllArgsConstructor
public class SecretStoreServiceDebug {

    private VaultServiceProperties vaultServiceProperties;

    @PostConstruct
    public void debugSecrets() {
        try {
            final DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
                    .managedIdentityClientId(vaultServiceProperties.getVaultClientId().toString())
                    .build();
            final SecretClient secretClient = new SecretClientBuilder()
                    .vaultUrl(vaultServiceProperties.getVaultUri())
                    .credential(credential)
                    .buildClient();
            log.info("debugSecrets found {} secrets", secretClient.listPropertiesOfSecrets().stream().toList().size());
            final List<SecretProperties> hcesSecrets = secretClient.listPropertiesOfSecrets().stream()
                    .filter(s -> s.getName().startsWith(SECRET_PREFIX))
                    .toList();
            log.info("debugSecrets found {} {} secrets", hcesSecrets.size(), SECRET_PREFIX);
            hcesSecrets.forEach(s -> log.info("debugSecrets found secret:{}", s.getName()));
        } catch (Exception e) {
            log.error("debugSecrets error {}", e.getMessage());
        }
    }
}
