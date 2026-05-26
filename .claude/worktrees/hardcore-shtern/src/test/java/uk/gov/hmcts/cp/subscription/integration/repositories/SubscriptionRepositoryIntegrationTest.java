package uk.gov.hmcts.cp.subscription.integration.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionRepositoryIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void beforeEach() {
        clearClientSubscriptionTable();
    }

    @Transactional
    @Test
    void subscription_should_save_and_read_ok() {
        ClientSubscriptionEntity saved = insertSubscription("https://example.com/notify", List.of("PRISON_COURT_REGISTER_GENERATED"));
        ClientSubscriptionEntity found = subscriptionRepository.getReferenceById(saved.getId());
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getEventTypes()).isEqualTo(List.of("PRISON_COURT_REGISTER_GENERATED"));
        assertThat(found.getNotificationEndpoint()).isEqualTo("https://example.com/notify");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Transactional
    @Test
    void subscription_should_delete_ok() {
        ClientSubscriptionEntity saved = insertSubscription("https://example.com/notify", List.of("PRISON_COURT_REGISTER_GENERATED"));
        subscriptionRepository.deleteById(saved.getId());
        assertThat(subscriptionRepository.findAll()).hasSize(0);
    }

    @Transactional
    @Test
    void findByEventType_should_return_subscriptions_for_event_type() {
        UUID client1 = UUID.fromString("11111111-2222-3333-4444-555555555551");
        UUID client2 = UUID.fromString("11111111-2222-3333-4444-555555555552");
        ClientSubscriptionEntity sub1 = insertSubscription(client1, List.of("PRISON_COURT_REGISTER_GENERATED"), "https://subscriber1.example/callback");
        ClientSubscriptionEntity sub2 = insertSubscription(client2, List.of("PRISON_COURT_REGISTER_GENERATED"), "https://subscriber2.example/callback");

        List<ClientSubscriptionEntity> forPcr = subscriptionRepository.findByEventType("PRISON_COURT_REGISTER_GENERATED");

        assertThat(forPcr)
                .extracting(ClientSubscriptionEntity::getId)
                .containsExactlyInAnyOrder(sub1.getId(), sub2.getId());
    }
}