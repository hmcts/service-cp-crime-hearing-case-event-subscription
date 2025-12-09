package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.entities.SubscriptionEntity;
import uk.gov.hmcts.cp.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.openapi.model.Result;
import uk.gov.hmcts.cp.repositories.SubscriptionRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper mapper;

    public Result getSubscriptionById(final UUID subscriptionId) {
        SubscriptionEntity entity = subscriptionRepository.getReferenceById(subscriptionId);
        return mapper.mapSubscriptionToResult(entity);
    }

    public List<Result> getSubscriptionsBySubscriber(final UUID subscriberId) {
        List<SubscriptionEntity> subscriptions = subscriptionRepository.getBySubscriberId(subscriberId);
        return mapper.mapSubscriptionsToResults(subscriptions);
    }
}
