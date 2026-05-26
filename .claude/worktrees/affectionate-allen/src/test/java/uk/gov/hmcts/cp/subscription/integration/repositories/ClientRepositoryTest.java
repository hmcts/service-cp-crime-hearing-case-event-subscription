package uk.gov.hmcts.cp.subscription.integration.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRepositoryTest extends IntegrationTestBase {

    final UUID clientId = UUID.randomUUID();
    final UUID subscriptionId = UUID.randomUUID();
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    final ClientEntity client = getClientEntity(clientId, subscriptionId, "https://test.com/webhook", now);

    @Transactional
    @Test
    void findById_should_return_client_entity_when_exists() {
        clientRepository.save(client);

        Optional<ClientEntity> found = clientRepository.findByIdAndSubscriptionId(clientId, subscriptionId);
        assertThat(found.isPresent()).isTrue();
        assertThat(found.get().getId()).isEqualTo(clientId);
        assertThat(found.get().getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(found.get().getCallbackUrl()).isEqualTo("https://test.com/webhook");
    }

    @Transactional
    @Test
    void findById_should_return_empty_when_client_not_found() {
        UUID nonExistentId = UUID.randomUUID();
        clientRepository.save(client);

        Optional<ClientEntity> found = clientRepository.findByIdAndSubscriptionId(nonExistentId, subscriptionId);
        assertThat(found).isEmpty();
    }

    private static ClientEntity getClientEntity(UUID clientId, UUID subscriptionId, String callbackUrl, OffsetDateTime originalTime) {
        return ClientEntity.builder()
                .id(clientId)
                .subscriptionId(subscriptionId)
                .callbackUrl(callbackUrl)
                .createdAt(originalTime)
                .updatedAt(originalTime)
                .build();
    }
}