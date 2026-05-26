package uk.gov.hmcts.cp.subscription.integration.repositories;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientHmacEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClientHmacRepositoryTest extends IntegrationTestBase {

    @Test
    void client_hmac_table_should_write_and_read() {
        UUID clientId = UUID.randomUUID();
        String kid = "kid-" + UUID.randomUUID();
        ClientEntity client = insertClient(clientId);
        ClientHmacEntity clientHmac = ClientHmacEntity.builder()
                .subscriptionId(client.getSubscriptionId())
                .keyId(kid)
                .build();
        ClientHmacEntity saved = clientHmacRepository.save(clientHmac);

        ClientHmacEntity retrieved = clientHmacRepository.findById(saved.getId()).get();
        assertThat(retrieved.getSubscriptionId()).isEqualTo(client.getSubscriptionId());
    }
}
