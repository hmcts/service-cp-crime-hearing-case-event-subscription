package uk.gov.hmcts.cp.subscription.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.Subscriber;

@Component
public class SubscriberMapper {

    public Subscriber toSubscriber(final ClientSubscriptionEntity entity) {
        return new Subscriber(
                entity.getId(),
                entity.getEventTypes(),
                entity.getNotificationEndpoint(),
                entity.getClientId(),
                entity.getHmacKeyId()
        );
    }
}
