package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.entities.SubscriberEntity;
import uk.gov.hmcts.cp.entities.SubscriptionEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.hmcts.cp.model.EventType.PCR;

class SubscriptionRepositoryTest extends RepositoryTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void subscription_should_save_and_read_ok() {
        SubscriberEntity subscriber = insertSubscriber("My subscriber");
        insertSubscription(subscriber.getId());
        List<SubscriptionEntity> subscriptions = subscriptionRepository.findAll();
        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).getId()).isNotNull();
        assertThat(subscriptions.get(0).getEventType()).isEqualTo(PCR);
        assertThat(subscriptions.get(0).getNotifyUrl()).isEqualTo("https://example.com/notify");
    }

    @Test
    void fk_constraint_should_prevent_orphaned_subscription() {
        assertThrows(Exception.class, () -> insertSubscription(99L));
    }

    @Test
    void query_by_subscriber_id_should_return_subscription_list() {
        SubscriberEntity subscriber = insertSubscriber("My subscriber");
        insertSubscription(subscriber.getId());
        List<SubscriptionEntity> subscriptions = subscriptionRepository.getBySubscriberId(subscriber.getId());
        assertThat(subscriptions).hasSize(1);
    }
}