package uk.gov.hmcts.cp.vault;

import java.util.Optional;

public interface SecretStoreServiceInterface {

    String getFullSecretName(String secretName);

    Optional<String> getSecret(String secretName);

    void setSecret(String secretName, String secretValue);
}
