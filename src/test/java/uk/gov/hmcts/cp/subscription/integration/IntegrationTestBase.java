package uk.gov.hmcts.cp.subscription.integration;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.config.TestContainersInitialise;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;
import uk.gov.hmcts.cp.subscription.repositories.SubscriptionRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static uk.gov.hmcts.cp.openapi.model.EventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.openapi.model.EventType.PRISON_COURT_REGISTER_GENERATED;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@AutoConfigureMockMvc
@Slf4j
public abstract class IntegrationTestBase {

    @Resource
    protected MockMvc mockMvc;

    @Autowired
    protected SubscriptionRepository subscriptionRepository;

    NotificationEndpoint notificationEndpoint = NotificationEndpoint.builder()
            .webhookUrl("https://my-callback-url")
            .build();
    ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
            .notificationEndpoint(notificationEndpoint)
            .eventTypes(List.of(PRISON_COURT_REGISTER_GENERATED, CUSTODIAL_RESULT))
            .build();

    protected void clearAllTables() {
        log.info("Clearing all tables");
        subscriptionRepository.deleteAll();
    }

    protected ClientSubscriptionEntity insertSubscription(String notificationUri, List<EntityEventType> entityEventTypes) {
        ClientSubscriptionEntity subscription = ClientSubscriptionEntity.builder()
                .eventTypes(entityEventTypes)
                .notificationEndpoint(notificationUri)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        ClientSubscriptionEntity saved = subscriptionRepository.save(subscription);
        log.info("Inserted subscription:{}", saved.getId());
        return saved;
    }
}
