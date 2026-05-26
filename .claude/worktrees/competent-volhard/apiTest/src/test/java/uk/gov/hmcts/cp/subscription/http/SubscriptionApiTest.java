package uk.gov.hmcts.cp.subscription.http;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class SubscriptionApiTest extends BaseTest {

    @BeforeEach
    void beforeEach() {
        try {
            restClient.get()
                    .uri(subscriptionsBaseUrl + "/actuator/health")
                    .retrieve();
        } catch (Exception e) {
            log.error("Service not running on 8082 - run docker compose to bring up service");
        }
    }

    @Test
    void create_get_and_delete_subscription_should_work_ok() {
        UUID clientId = UUID.randomUUID();
        UUID subscriptionId = createOrGetSubscription(clientId);
        UUID subscriptionIdAgain = createOrGetSubscription(clientId);
        assertThat(subscriptionId).isEqualTo(subscriptionIdAgain);
        deleteSubscription(clientId, subscriptionId);
    }
}