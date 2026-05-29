package uk.gov.hmcts.cp.subscription.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.repositories.ClientRepository;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionValidationService {

    private final ClientRepository clientRepository;
    private final EventTypeRepository eventTypeRepository;

    public void validateClientDoesNotExist(final UUID clientId) {
        final Optional<ClientEntity> existingClient = clientRepository.findByClientId(clientId);
        if (existingClient.isPresent()) {
            log.warn("duplicate subscription request clientId:{} subscriptionId:{}", clientId, existingClient.get().getSubscriptionId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "subscription already exist with " + existingClient.get().getSubscriptionId());
        }
    }

    public void validateClientSubscriptionExists(final UUID clientId, final UUID subscriptionId) {
        if (clientRepository.findByClientIdAndSubscriptionId(clientId, subscriptionId).isEmpty()) {
            throw new EntityNotFoundException("Client not found for the provided clientId and subscriptionId");
        }
    }

    public List<Long> validateAndFetchEventIds(final ClientSubscriptionRequest request) {
        final Set<String> requestedTypes = new HashSet<>(request.getEventTypes());

        final List<EventTypeEntity> foundEntities = eventTypeRepository.findByEventNameIn(requestedTypes);
        final Set<String> foundTypes = foundEntities.stream()
                .map(EventTypeEntity::getEventName)
                .collect(Collectors.toSet());

        final Set<String> invalidTypes = new HashSet<>(requestedTypes);
        invalidTypes.removeAll(foundTypes);

        if (!invalidTypes.isEmpty()) {
            throw new IllegalArgumentException("Invalid event type(s): "
                    + invalidTypes.stream().sorted().collect(Collectors.joining(", ")));
        }

        return foundEntities.stream()
                .map(EventTypeEntity::getId)
                .toList();
    }
}