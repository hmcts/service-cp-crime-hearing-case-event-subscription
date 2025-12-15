package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.ClientSubscriptionEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.model.EntityEventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.model.EntityEventType.PCR;

class SubscriptionRepositoryTest extends RepositoryTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Transactional
    @Test
    void subscription_should_save_and_read_ok() {
        insertSubscription("https://example.com/notify", List.of(CUSTODIAL_RESULT, PCR));
        List<ClientSubscriptionEntity> subscriptions = subscriptionRepository.findAll();
        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).getId()).isNotNull();
        assertThat(subscriptions.get(0).getEventTypes()).isEqualTo(List.of(CUSTODIAL_RESULT, PCR));
        assertThat(subscriptions.get(0).getNotificationEndpoint()).isEqualTo("https://example.com/notify");
        assertThat(subscriptions.get(0).getCreatedAt()).isNotNull();
    }
}