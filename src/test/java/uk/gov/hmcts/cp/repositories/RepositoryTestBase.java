package uk.gov.hmcts.cp.repositories;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.cp.config.TestContainersInitialise;
import uk.gov.hmcts.cp.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.model.EntityEventType;

import java.time.OffsetDateTime;
import java.util.List;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@AutoConfigureMockMvc
@Slf4j
public abstract class RepositoryTestBase {

    @Autowired
    protected SubscriptionRepository subscriptionRepository;

    protected void clearAllTables() {
        log.info("Clearing all tables");
        subscriptionRepository.deleteAll();
    }

    protected ClientSubscriptionEntity insertSubscription(String notificationUri, List<EntityEventType> entityEventTypes) {
        ClientSubscriptionEntity subscription = ClientSubscriptionEntity.builder()
                .eventTypes(entityEventTypes)
                .notificationEndpoint(notificationUri)
                .createdAt(OffsetDateTime.now())
                .build();
        ClientSubscriptionEntity saved = subscriptionRepository.save(subscription);
        log.info("Inserted subscription:{}", saved.getId());
        return saved;
    }
}
