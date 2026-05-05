package uk.gov.hmcts.cp.subscription.unit.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.hmac.managers.HmacManager;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.HmacCredentials;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.openapi.model.RotateSecretRequest;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientEventEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientHmacEntity;
import uk.gov.hmcts.cp.subscription.mappers.ClientEntityMapper;
import uk.gov.hmcts.cp.subscription.mappers.ClientEventEntityMapper;
import uk.gov.hmcts.cp.subscription.mappers.ClientHmacMapper;
import uk.gov.hmcts.cp.subscription.mappers.ClientSubscriptionMapper;
import uk.gov.hmcts.cp.subscription.repositories.ClientEventRepository;
import uk.gov.hmcts.cp.subscription.repositories.ClientHmacRepository;
import uk.gov.hmcts.cp.subscription.repositories.ClientRepository;
import uk.gov.hmcts.cp.subscription.services.ClockService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionValidationService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    ClockService clockService;
    @Mock
    HmacManager hmacManager;
    @Mock
    ClientRepository clientRepository;
    @Mock
    ClientHmacMapper clientHmacMapper;
    @Mock
    ClientHmacRepository clientHmacRepository;
    @Mock
    ClientEventRepository clientEventRepository;
    @Mock
    ClientEntityMapper clientEntityMapper;
    @Mock
    ClientEventEntityMapper clientEventEntityMapper;
    @Mock
    ClientSubscriptionMapper clientSubscriptionMapper;
    @Mock
    SubscriptionValidationService subscriptionValidationService;

    @InjectMocks
    SubscriptionService subscriptionService;

    ClientSubscriptionRequest createRequest = ClientSubscriptionRequest.builder()
            .notificationEndpoint(NotificationEndpoint.builder().callbackUrl("https://example.com/callback").build())
            .eventTypes(List.of("PRISON_COURT_REGISTER_GENERATED"))
            .build();
    ClientSubscriptionRequest updateRequest = ClientSubscriptionRequest.builder()
            .eventTypes(List.of("PRISON_COURT_REGISTER_GENERATED"))
            .build();
    UUID subscriptionId = UUID.fromString("2ca16eb5-3998-4bb7-adce-4bb9b3b7223c");
    UUID clientId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    ClientEntity clientEntity = ClientEntity.builder()
            .subscriptionId(subscriptionId)
            .clientId(clientId)
            .build();
    ClientHmacEntity clientHmacEntity = ClientHmacEntity.builder().build();
    ClientEntity updatedClientEntity = ClientEntity.builder()
            .subscriptionId(subscriptionId)
            .clientId(clientId)
            .callbackUrl("https://example.com/updated-callback")
            .build();
    ClientEventEntity clientEventEntity = ClientEventEntity.builder()
            .subscriptionId(subscriptionId)
            .id(1L)
            .build();
    ClientSubscription response = ClientSubscription.builder().build();
    KeyPair hmacKeyPair = KeyPair.builder().keyId("kid-1").secret("secret-1".getBytes()).build();

    @Test
    void create_request_should_save_new_entity() {
        when(subscriptionValidationService.validateAndFetchEventIds(createRequest)).thenReturn(List.of(1L));
        when(clientEntityMapper.toEntity(clockService, createRequest, clientId)).thenReturn(clientEntity);
        when(clientHmacMapper.toEntity(subscriptionId, hmacKeyPair.getKeyId())).thenReturn(clientHmacEntity);
        when(clientEventEntityMapper.toEntity(subscriptionId, 1L)).thenReturn(clientEventEntity);
        when(hmacManager.createAndStoreNewKey()).thenReturn(hmacKeyPair);
        when(clientSubscriptionMapper.toDto(clientEntity, List.of("PRISON_COURT_REGISTER_GENERATED"), hmacKeyPair)).thenReturn(response);
        when(clientRepository.save(clientEntity)).thenReturn(clientEntity);

        ClientSubscription result = subscriptionService.createClientSubscription(createRequest, clientId);

        assertThat(result).isEqualTo(response);
        verify(subscriptionValidationService).validateAndFetchEventIds(createRequest);
        verify(clientRepository).save(clientEntity);
        verify(clientHmacRepository).save(clientHmacEntity);
        verify(clientEventRepository).saveAll(List.of(clientEventEntity));
    }

    @Test
    void create_request_should_throw_when_event_types_are_invalid() {
        doThrow(new IllegalArgumentException("Invalid event type(s): BAD"))
                .when(subscriptionValidationService).validateAndFetchEventIds(createRequest);

        assertThatThrownBy(() -> subscriptionService.createClientSubscription(createRequest, clientId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid event type(s): BAD");
    }

    @Test
    void update_request_should_update_existing_entity() {
        when(subscriptionValidationService.validateAndFetchEventIds(updateRequest)).thenReturn(List.of(1L));
        when(clientEntityMapper.mapUpdateRequestToEntity(clientEntity, clockService, updateRequest)).thenReturn(updatedClientEntity);
        when(clientEventEntityMapper.toEntity(subscriptionId, 1L)).thenReturn(clientEventEntity);
        when(clientRepository.findByClientIdAndSubscriptionId(clientId, subscriptionId)).thenReturn(Optional.of(clientEntity));
        when(clientSubscriptionMapper.toDto(updatedClientEntity, List.of("PRISON_COURT_REGISTER_GENERATED"), null)).thenReturn(response);
        when(clientRepository.save(updatedClientEntity)).thenReturn(updatedClientEntity);

        ClientSubscription result = subscriptionService.updateClientSubscription(clientId, subscriptionId, updateRequest);

        assertThat(result).isEqualTo(response);
        verify(clientRepository).findByClientIdAndSubscriptionId(clientId, subscriptionId);
        verify(subscriptionValidationService).validateAndFetchEventIds(updateRequest);
        verify(clientRepository).save(updatedClientEntity);
        verify(clientEventRepository).saveAll(List.of(clientEventEntity));
    }

    @Test
    void get_should_return_subscription_when_owned_by_client() {
        when(clientEventRepository.findEventNamesForSubscription(subscriptionId)).thenReturn(List.of("PRISON_COURT_REGISTER_GENERATED"));
        when(clientRepository.findByClientIdAndSubscriptionId(clientId, subscriptionId)).thenReturn(Optional.of(clientEntity));
        when(clientSubscriptionMapper.toDto(clientEntity, List.of("PRISON_COURT_REGISTER_GENERATED"), null)).thenReturn(response);

        ClientSubscription result = subscriptionService.getClientSubscription(clientId, subscriptionId);

        assertThat(result).isEqualTo(response);
        verify(clientRepository).findByClientIdAndSubscriptionId(clientId, subscriptionId);
    }

    @Test
    void delete_should_delete_entity_when_owned_by_client() {
        when(clientRepository.findByClientIdAndSubscriptionId(clientId, subscriptionId)).thenReturn(Optional.of(clientEntity));
        subscriptionService.deleteClientSubscription(clientId, subscriptionId);

        verify(clientRepository).findByClientIdAndSubscriptionId(clientId, subscriptionId);
        InOrder inOrder = inOrder(clientEventRepository, clientRepository);
        inOrder.verify(clientEventRepository).deleteBySubscriptionId(subscriptionId);
        inOrder.verify(clientRepository).delete(clientEntity);
    }

    @Test
    void hasAccess_should_return_true_when_matching_event_exists() {
        UUID subscriptionId = UUID.randomUUID();

        when(clientEventRepository.countByClientSubscriptionAndEventName(subscriptionId, "PRISON_COURT_REGISTER_GENERATED")).thenReturn(1L);

        boolean result = subscriptionService.hasAccess(subscriptionId, "PRISON_COURT_REGISTER_GENERATED");
        assertThat(result).isTrue();
    }

    @Test
    void rotate_should_return_hmac_credentials_with_same_key_id_and_new_secret() {
        final String existingKeyId = "kid-v1-existing";
        final String newEncodedSecret = "bmV3U2VjcmV0";
        final ClientHmacEntity hmacEntity = ClientHmacEntity.builder().keyId(existingKeyId).build();
        final RotateSecretRequest request = RotateSecretRequest.builder().keyId(existingKeyId).build();
        final OffsetDateTime now = OffsetDateTime.now();
        final ClientEntity clientWithUpdatedAt = clientEntity.toBuilder().updatedAt(now).build();

        when(clientRepository.findByClientIdAndSubscriptionId(clientId, subscriptionId)).thenReturn(Optional.of(clientEntity));
        when(clientHmacRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.of(hmacEntity));
        when(hmacManager.rotateSecret(existingKeyId)).thenReturn(newEncodedSecret);
        when(clockService.nowOffsetUTC()).thenReturn(now);

        HmacCredentials result = subscriptionService.rotateSubscriptionSecret(clientId, subscriptionId, request);

        assertThat(result.getKeyId()).isEqualTo(existingKeyId);
        assertThat(result.getSecret()).isEqualTo(newEncodedSecret);
        verify(hmacManager).rotateSecret(existingKeyId);
        verify(clientRepository).save(clientWithUpdatedAt);
    }

    @Test
    void rotate_should_throw_not_found_when_key_id_does_not_match() {
        final ClientHmacEntity hmacEntity = ClientHmacEntity.builder().keyId("kid-v1-real").build();
        final RotateSecretRequest request = RotateSecretRequest.builder().keyId("kid-v1-wrong").build();

        when(clientRepository.findByClientIdAndSubscriptionId(clientId, subscriptionId)).thenReturn(Optional.of(clientEntity));
        when(clientHmacRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.of(hmacEntity));

        assertThatThrownBy(() -> subscriptionService.rotateSubscriptionSecret(clientId, subscriptionId, request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void hasAccess_should_return_false_when_no_matching_event() {
        UUID subscriptionId = UUID.randomUUID();

        when(clientEventRepository.countByClientSubscriptionAndEventName(subscriptionId, "PRISON_COURT_REGISTER_GENERATED")).thenReturn(0L);

        boolean result = subscriptionService.hasAccess(subscriptionId, "PRISON_COURT_REGISTER_GENERATED");
        assertThat(result).isFalse();
    }
}