package uk.gov.hmcts.cp.vault;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.cp.hmac.services.EncodingService;

@Configuration
@AllArgsConstructor
public class VaultServiceConfiguration {
    private EncodingService encodingService;
    private VaultServiceProperties vaultServiceProperties;

    @Bean
    public SecretStoreServiceInterface secretStoreService() {
        if (vaultServiceProperties.isVaultEnabled()) {
            return new SecretStoreServiceAzureImpl(vaultServiceProperties);
        } else {
            return new SecretStoreServiceStubImpl(encodingService);
        }
    }
}
