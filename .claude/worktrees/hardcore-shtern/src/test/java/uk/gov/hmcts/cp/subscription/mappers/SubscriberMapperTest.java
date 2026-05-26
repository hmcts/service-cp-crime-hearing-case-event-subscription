package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.Subscriber;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriberMapperTest {
    SubscriberMapper subscriberMapper = new SubscriberMapper();

    @Test
    void entity_should_map_to_subscriber() {
        UUID id = UUID.randomUUID();
        List<String> eventTypes = List.of("PRISON_COURT_REGISTER_GENERATED");
        ClientSubscriptionEntity clientSubscription = ClientSubscriptionEntity.builder()
                .id(id)
                .eventTypes(eventTypes)
                .notificationEndpoint("http://localhost")
                .build();

        Subscriber result = subscriberMapper.toSubscriber(clientSubscription);

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getEventTypes()).isEqualTo(eventTypes);
        assertThat(result.getNotificationEndpoint()).isEqualTo("http://localhost");
        // clientId ??
    }
}