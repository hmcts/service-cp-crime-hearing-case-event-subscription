package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.repositories.SubscriptionRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper mapper;

    public ClientSubscription saveSubscription(final ClientSubscriptionRequest request) {
        final ClientSubscriptionEntity entity = mapper.mapRequestToEntity(request);
        return mapper.mapEntityToResponse(subscriptionRepository.save(entity));
    }

    public ClientSubscription getSubscription(final UUID clientSubscriptionId) {
        final ClientSubscriptionEntity entity = subscriptionRepository.getReferenceById(clientSubscriptionId);
        return mapper.mapEntityToResponse(entity);
    }

    public void deleteSubscription(final UUID clientSubscriptionId) {
        subscriptionRepository.deleteById(clientSubscriptionId);
    }
}
