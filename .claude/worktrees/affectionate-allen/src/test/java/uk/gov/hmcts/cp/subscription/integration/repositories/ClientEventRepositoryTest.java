package uk.gov.hmcts.cp.subscription.integration.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.subscription.entities.ClientEventEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClientEventRepositoryTest extends IntegrationTestBase {

    final UUID clientId1 = UUID.randomUUID();
    final UUID clientId2 = UUID.randomUUID();
    final UUID subscriptionId1 = UUID.randomUUID();
    final UUID subscriptionId2 = UUID.randomUUID();
    final ClientEntity client1 = getClientEntity(clientId1, "https://example.com/callback", subscriptionId1);
    final ClientEntity client2 = getClientEntity(clientId2, "https://example.com/callback", subscriptionId2);
    final ClientEventEntity event1 = getClientEventEntity(subscriptionId1, 1L);
    final ClientEventEntity event2 = getClientEventEntity(subscriptionId1, 2L);
    final ClientEventEntity event3 = getClientEventEntity(subscriptionId2, 3L);

    @Transactional
    @Test
    void findClientEventsWithEventTypes_should_return_events_ordered_by_event_name() {
        saveClientAndEventInfoInDb(client1, List.of(event1, event2));

        List<String> result = clientEventRepository.findEventNamesForClient(clientId1, subscriptionId1);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
        assertThat(result.getLast()).isEqualTo("WEE_Layout5");
    }

    private void saveClientAndEventInfoInDb(ClientEntity client, List<ClientEventEntity> events) {
        clientRepository.save(client);
        clientEventRepository.saveAll(events);
    }

    @Transactional
    @Test
    void countByClientSubscriptionAndEventName_should_return_count_if_event_exists() {
        saveClientAndEventInfoInDb(client1, List.of(event1));

        long result = clientEventRepository.countByClientSubscriptionAndEventName(subscriptionId1, "PRISON_COURT_REGISTER_GENERATED");
        assertThat(result).isEqualTo(1);
    }

    @Transactional
    @Test
    void deleteBySubscriptionId_should_delete_client_event_entity() {
        saveClientAndEventInfoInDb(client1, List.of(event1));
        saveClientAndEventInfoInDb(client2, List.of(event3));

        clientEventRepository.deleteBySubscriptionId(subscriptionId1);

        List<ClientEventEntity> result = clientEventRepository.findAll();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst().getSubscriptionId()).isEqualTo(subscriptionId2);
    }

    private static ClientEntity getClientEntity(UUID clientId, String callbackUrl, UUID subscriptionId) {
        return ClientEntity.builder()
                .id(clientId)
                .subscriptionId(subscriptionId)
                .callbackUrl(callbackUrl)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private static ClientEventEntity getClientEventEntity(UUID subscriptionId, long eventId) {
        return ClientEventEntity.builder()
                .subscriptionId(subscriptionId)
                .eventTypeId(eventId)
                .build();
    }
}