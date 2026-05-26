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
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientEventEntity;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.mappers.ClientEntityMapper;
import uk.gov.hmcts.cp.subscription.mappers.ClientEventEntityMapper;
import uk.gov.hmcts.cp.subscription.mappers.ClientSubscriptionMapper;
import uk.gov.hmcts.cp.subscription.repositories.ClientEventRepository;
import uk.gov.hmcts.cp.subscription.repositories.ClientRepository;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceV2 {

    private final ClientRepository clientRepository;
    private final ClientEventRepository clientEventRepository;
    private final EventTypeRepository eventTypeRepository;

    private final ClientEntityMapper clientEntityMapper;
    private final ClientEventEntityMapper clientEventEntityMapper;
    private final ClientSubscriptionMapper clientSubscriptionMapper;

    private final ClockService clockService;
    private final HmacManager hmacManager;

    @Transactional
    public ClientSubscription createClientSubscription(final ClientSubscriptionRequest request, final UUID clientId) {
        validateClientDoesNotExists(clientId);
        final List<Long> eventIds = validateAndFetchEvents(request);

        final ClientEntity client = saveClientForCreateRequest(request, clientId);
        saveClientEvents(client.getSubscriptionId(), eventIds);

        //TODO save the client hmacKeyId in the new table
        final KeyPair keyPair = hmacManager.createAndStoreNewKey();
        return clientSubscriptionMapper.toDto(client, request.getEventTypes(), keyPair);
    }

    @Transactional
    public ClientSubscription updateClientSubscription(final ClientSubscriptionRequest request,
                                                       final UUID clientId,
                                                       final UUID subscriptionId) {
        final ClientEntity client = validateAndFetchClient(clientId, subscriptionId);
        final List<Long> eventIds = validateAndFetchEvents(request);

        final ClientEntity updatedClient = saveClientForUpdateRequest(request, client);
        updateClientEvents(subscriptionId, eventIds);

        return clientSubscriptionMapper.toDto(updatedClient, request.getEventTypes(), null);
    }

    public ClientSubscription getClientSubscription(final UUID clientId, final UUID subscriptionId) {
        final ClientEntity client = validateAndFetchClient(clientId, subscriptionId);
        final List<String> eventNames = clientEventRepository.findEventNamesForClient(clientId, subscriptionId);
        return clientSubscriptionMapper.toDto(client, eventNames, null);
    }

    @Transactional
    public void deleteClientSubscription(final UUID clientId, final UUID subscriptionId) {
        final ClientEntity client = validateAndFetchClient(clientId, subscriptionId);
        clientEventRepository.deleteBySubscriptionId(subscriptionId);
        clientRepository.delete(client);
    }

    public boolean hasAccess(final UUID subscriptionId,
                             final String eventType) {
        return clientEventRepository.countByClientSubscriptionAndEventName(subscriptionId, eventType) > 0;
    }

    private void validateClientDoesNotExists(final UUID clientId) {
        if (clientRepository.existsById(clientId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "subscription already exist for client " + clientId.toString());
        }
    }

    private ClientEntity validateAndFetchClient(final UUID clientId, final UUID subscriptionId) {
        return clientRepository.findByIdAndSubscriptionId(clientId, subscriptionId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Client not found for the provided clientId and subscriptionId"));
    }

    private List<Long> validateAndFetchEvents(final ClientSubscriptionRequest request) {
        return request.getEventTypes().stream()
                .map(name -> eventTypeRepository.findByEventName(name)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid event type: " + name)))
                .map(EventTypeEntity::getId)
                .toList();
    }

    private void saveClientEvents(final UUID subscriptionId, final List<Long> eventIds) {
        final List<ClientEventEntity> eventEntities = eventIds.stream()
                .map(id -> clientEventEntityMapper.toEntity(subscriptionId, id))
                .toList();
        clientEventRepository.saveAll(eventEntities);
    }

    private ClientEntity saveClientForCreateRequest(final ClientSubscriptionRequest request, final UUID clientId) {
        final ClientEntity client = clientEntityMapper.toEntity(clockService, request, clientId);
        return clientRepository.save(client);
    }

    private ClientEntity saveClientForUpdateRequest(final ClientSubscriptionRequest request, final ClientEntity client) {
        final ClientEntity updatedClient = clientEntityMapper.mapUpdateRequestToEntity(client, clockService, request);
        return clientRepository.save(updatedClient);
    }

    private void updateClientEvents(final UUID subscriptionId, final List<Long> eventIds) {
        clientEventRepository.deleteBySubscriptionId(subscriptionId);
        saveClientEvents(subscriptionId, eventIds);
    }
}
