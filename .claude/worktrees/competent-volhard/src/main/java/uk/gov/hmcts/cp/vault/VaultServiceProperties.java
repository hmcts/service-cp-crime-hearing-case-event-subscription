package uk.gov.hmcts.cp.vault;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@Builder
@Getter
public class VaultServiceProperties {

    private boolean vaultEnabled;
    private final String vaultUri;
    private final UUID vaultClientId;

    public VaultServiceProperties(
            @Value("${vault.enabled}") final boolean vaultEnabled,
            @Value("${vault.uri}") final String vaultUri,
            @Value("${vault.client-id:}") final UUID vaultClientId) {
        log.info("Vault initialised with vaultEnabled:{}", vaultEnabled);
        log.info("Vault initialised with vaultUri:{}", vaultUri);
        log.info("Vault initialised with vaultClientId:{}", vaultClientId);
        this.vaultEnabled = vaultEnabled;
        this.vaultUri = vaultUri;
        this.vaultClientId = vaultClientId;
    }
}

