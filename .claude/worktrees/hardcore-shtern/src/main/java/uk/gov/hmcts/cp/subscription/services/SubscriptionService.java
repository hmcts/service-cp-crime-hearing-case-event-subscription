package uk.gov.hmcts.cp.subscription.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.hmac.managers.HmacManager;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.subscription.repositories.SubscriptionRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final ClockService clockService;
    private final SubscriptionRepository subscriptionRepository;
    private final EventTypeService eventTypeService;
    private final SubscriptionMapper mapper;
    private final HmacManager hmacManager;

    @Transactional
    public ClientSubscription createSubscription(final ClientSubscriptionRequest request, final UUID clientId) {
        validateEventsOrThrowError(request, clientId);
        subscriptionRepository.findFirstByClientId(clientId).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "subscription already exist with " + existing.getId());
        });
        final KeyPair keyPair = hmacManager.createAndStoreNewKey();
        final ClientSubscriptionEntity entity = mapper.mapCreateRequestToEntity(clientId, keyPair.getKeyId(), request, clockService.nowOffsetUTC());
        subscriptionRepository.save(entity);
        return mapper.mapEntityToResponse(entity, keyPair);
    }

    @Transactional
    public ClientSubscription updateSubscription(final UUID clientSubscriptionId, final ClientSubscriptionRequest request, final UUID clientId) {
        final ClientSubscriptionEntity existing = subscriptionRepository.findByIdAndClientId(clientSubscriptionId, clientId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found"));
        final ClientSubscriptionEntity entity = mapper.mapUpdateRequestToEntity(existing, request, clockService.nowOffsetUTC());
        return mapper.mapEntityToResponse(subscriptionRepository.save(entity), null);
    }

    @Transactional
    public ClientSubscription getSubscription(final UUID clientSubscriptionId, final UUID clientId) {
        final ClientSubscriptionEntity entity = subscriptionRepository.findByIdAndClientId(clientSubscriptionId, clientId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found"));
        return mapper.mapEntityToResponse(entity, null);
    }

    @Transactional
    public void deleteSubscription(final UUID clientSubscriptionId, final UUID clientId) {
        final ClientSubscriptionEntity entity = subscriptionRepository.findByIdAndClientId(clientSubscriptionId, clientId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found"));
        subscriptionRepository.delete(entity);
    }

    @Transactional
    public boolean hasAccess(final UUID clientSubscriptionId, final UUID clientId, final String eventType) {
        return subscriptionRepository.findByIdAndClientId(clientSubscriptionId, clientId)
                .map(entity -> entity.getEventTypes() != null && entity.getEventTypes().contains(eventType))
                .orElse(false);
    }

    public void validateEventsOrThrowError(final ClientSubscriptionRequest request, final UUID clientId) {
        request.getEventTypes().forEach(eventType -> {
            if (!eventTypeService.eventExists(eventType)) {
                log.error("Got invalid event type '{}' for client '{}' in subscription request.", eventType, clientId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event type: " + eventType);
            }
        });
    }
}
