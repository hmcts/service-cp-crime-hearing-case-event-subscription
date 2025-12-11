package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.entities.SubscriptionEntity;
import uk.gov.hmcts.cp.openapi.model.Result;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionMapperTest {

    SubscriptionMapper mapper = new SubscriptionMapperImpl();

    @Test
    void single_subscription_should_map_to_response() {
        SubscriptionEntity subscription = SubscriptionEntity.builder().notifyUrl("https://example").build();
        Result result = mapper.mapSubscriptionToResult(subscription);
        assertThat(result.getResultText()).isEqualTo("https://example");
    }

    @Test
    void list_of_subscriptions_should_map_to_response() {
        SubscriptionEntity subscription = SubscriptionEntity.builder().notifyUrl("https://example").build();
        List<Result> results = mapper.mapSubscriptionsToResults(List.of(subscription));
        assertThat(results).hasSize(1);
    }
}