package uk.gov.hmcts.cp.subscription.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.subscription.model.EntityEventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.subscription.model.EntityEventType.PRISON_COURT_REGISTER_GENERATED;

class SubscriptionRepositoryTest extends IntegrationTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Transactional
    @Test
    void subscription_should_save_and_read_ok() {
        ClientSubscriptionEntity saved = insertSubscription("https://example.com/notify", List.of(CUSTODIAL_RESULT, PRISON_COURT_REGISTER_GENERATED));
        ClientSubscriptionEntity found = subscriptionRepository.getReferenceById(saved.getId());
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getEventTypes()).isEqualTo(List.of(CUSTODIAL_RESULT, PRISON_COURT_REGISTER_GENERATED));
        assertThat(found.getNotificationEndpoint()).isEqualTo("https://example.com/notify");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Transactional
    @Test
    void subscription_should_delete_ok() {
        ClientSubscriptionEntity saved = insertSubscription("https://example.com/notify", List.of(CUSTODIAL_RESULT, PRISON_COURT_REGISTER_GENERATED));
        subscriptionRepository.deleteById(saved.getId());
        assertThat(subscriptionRepository.findAll()).hasSize(0);
    }
}