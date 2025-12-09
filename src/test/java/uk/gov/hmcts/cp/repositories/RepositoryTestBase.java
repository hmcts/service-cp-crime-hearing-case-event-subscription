package uk.gov.hmcts.cp.repositories;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.cp.config.TestContainersInitialise;
import uk.gov.hmcts.cp.entities.SubscriberEntity;
import uk.gov.hmcts.cp.entities.SubscriptionEntity;
import uk.gov.hmcts.cp.model.EventType;

import java.util.UUID;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@AutoConfigureMockMvc
@Slf4j
public abstract class RepositoryTestBase {

    @Autowired
    protected SubscriberRepository subscriberRepository;

    @Autowired
    protected SubscriptionRepository subscriptionRepository;

    protected void clearAllTables() {
        log.info("Clearing all tables");
        subscriptionRepository.deleteAll();
        subscriberRepository.deleteAll();
    }

    protected SubscriberEntity insertSubscriber(String name) {
        SubscriberEntity subscriber = SubscriberEntity.builder().name(name).build();
        SubscriberEntity saved = subscriberRepository.save(subscriber);
        log.info("Inserted subscriber:{}", saved.getId());
        return saved;
    }

    protected SubscriptionEntity insertSubscription(UUID subscriberId) {
        SubscriptionEntity subscription = SubscriptionEntity.builder()
                .subscriberId(subscriberId)
                .eventType(EventType.PCR)
                .notifyUrl("https://example.com/notify")
                .build();
        SubscriptionEntity saved = subscriptionRepository.save(subscription);
        log.info("Inserted subscription:{}", saved.getId());
        return saved;
    }
}
