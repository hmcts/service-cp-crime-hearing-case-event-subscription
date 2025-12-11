package uk.gov.hmcts.cp.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.entities.SubscriberEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriberRepositoryTest extends RepositoryTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void subscriber_should_save_and_read_ok() {
        insertSubscriber("My subscriber");
        List<SubscriberEntity> subscribers = subscriberRepository.findAll();
        assertThat(subscribers).hasSize(1);
        assertThat(subscribers.get(0).getId()).isNotNull();
        assertThat(subscribers.get(0).getName()).isEqualTo("My subscriber");
    }
}