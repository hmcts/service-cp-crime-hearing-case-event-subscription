package uk.gov.hmcts.cp.subscription.unit.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.hmac.managers.HmacManager;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.hmac.services.HmacKeyService;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.subscription.repositories.SubscriptionRepository;
import uk.gov.hmcts.cp.subscription.services.ClockService;
import uk.gov.hmcts.cp.subscription.services.EventTypeService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    ClockService clockService;
    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    SubscriptionMapper mapper;
    @Mock
    HmacKeyService hmacKeyService;
    @Mock
    HmacManager hmacManager;
    @Mock
    EventTypeService eventTypeService;

    @InjectMocks
    SubscriptionService subscriptionService;

    ClientSubscriptionRequest createRequest = ClientSubscriptionRequest.builder()
            .notificationEndpoint(NotificationEndpoint.builder().callbackUrl("https://example.com/callback").build())
            .eventTypes(List.of("PRISON_COURT_REGISTER_GENERATED"))
            .build();
    ClientSubscriptionRequest updateRequest = ClientSubscriptionRequest.builder().build();
    UUID subscriptionId = UUID.fromString("2ca16eb5-3998-4bb7-adce-4bb9b3b7223c");
    UUID clientId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    OffsetDateTime now = OffsetDateTime.now();
    ClientSubscriptionEntity requestEntity = ClientSubscriptionEntity.builder()
            .id(subscriptionId)
            .clientId(clientId)
            .build();
    ClientSubscriptionEntity savedEntity = ClientSubscriptionEntity.builder()
            .id(subscriptionId)
            .clientId(clientId)
            .build();
    ClientSubscriptionEntity updatedEntity = ClientSubscriptionEntity.builder()
            .id(subscriptionId)
            .clientId(clientId)
            .build();
    ClientSubscription response = ClientSubscription.builder().build();
    KeyPair hmacKeyPair = KeyPair.builder().keyId("kid-v1").secret("secret-1".getBytes()).build();


    @Test
    void create_request_should_save_new_entity() {
        when(subscriptionRepository.findFirstByClientId(clientId)).thenReturn(Optional.empty());
        when(clockService.nowOffsetUTC()).thenReturn(now);
        when(mapper.mapCreateRequestToEntity(clientId, "kid-v1", createRequest, now)).thenReturn(requestEntity);
        when(hmacManager.createAndStoreNewKey()).thenReturn(hmacKeyPair);
        when(mapper.mapEntityToResponse(requestEntity, hmacKeyPair)).thenReturn(response);
        when(eventTypeService.eventExists("PRISON_COURT_REGISTER_GENERATED")).thenReturn(true);

        ClientSubscription result = subscriptionService.createSubscription(createRequest, clientId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void create_request_should_throw_conflict_when_subscription_already_exists() {
        when(subscriptionRepository.findFirstByClientId(clientId)).thenReturn(Optional.of(savedEntity));
        when(eventTypeService.eventExists("PRISON_COURT_REGISTER_GENERATED")).thenReturn(true);

        assertThatThrownBy(() -> subscriptionService.createSubscription(createRequest, clientId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("subscription already exist with " + subscriptionId);
                });

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void create_request_should_throw_bad_when_event_does_not_exists() {
        ClientSubscriptionRequest createRequest = ClientSubscriptionRequest.builder()
                .notificationEndpoint(NotificationEndpoint.builder().callbackUrl("https://example.com/callback").build())
                .eventTypes(List.of("BAD"))
                .build();
        when(eventTypeService.eventExists("BAD")).thenReturn(false);

        assertThatThrownBy(() -> subscriptionService.createSubscription(createRequest, clientId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("Invalid event type: BAD");
                });

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void update_request_should_update_existing_entity() {
        when(subscriptionRepository.findByIdAndClientId(subscriptionId, clientId)).thenReturn(Optional.of(savedEntity));
        when(clockService.nowOffsetUTC()).thenReturn(now);
        when(mapper.mapUpdateRequestToEntity(savedEntity, updateRequest, now)).thenReturn(requestEntity);
        when(subscriptionRepository.save(requestEntity)).thenReturn(savedEntity);
        when(mapper.mapEntityToResponse(savedEntity, null)).thenReturn(response);

        ClientSubscription result = subscriptionService.updateSubscription(subscriptionId, updateRequest, clientId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void get_should_return_subscription_when_owned_by_client() {
        when(subscriptionRepository.findByIdAndClientId(subscriptionId, clientId)).thenReturn(Optional.of(savedEntity));
        when(mapper.mapEntityToResponse(savedEntity, null)).thenReturn(response);

        ClientSubscription result = subscriptionService.getSubscription(subscriptionId, clientId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void delete_should_delete_entity_when_owned_by_client() {
        when(subscriptionRepository.findByIdAndClientId(subscriptionId, clientId)).thenReturn(Optional.of(savedEntity));

        subscriptionService.deleteSubscription(subscriptionId, clientId);

        verify(subscriptionRepository).findByIdAndClientId(subscriptionId, clientId);
        verify(subscriptionRepository).delete(savedEntity);
    }
}