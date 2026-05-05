package uk.gov.hmcts.cp.subscription.unit.controllers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.HmacCredentials;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.openapi.model.RotateSecretRequest;
import uk.gov.hmcts.cp.subscription.controllers.SubscriptionController;
import uk.gov.hmcts.cp.filters.ClientIdResolutionFilter;
import uk.gov.hmcts.cp.subscription.services.EventTypeService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionValidationService;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    SubscriptionService subscriptionService;

    @Mock
    SubscriptionValidationService subscriptionValidationService;

    @Mock
    EventTypeService eventTypeService;

    @InjectMocks
    SubscriptionController subscriptionController;

    private static final UUID TEST_CLIENT_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setMdcClientId() {
        MDC.put(ClientIdResolutionFilter.MDC_CLIENT_ID, TEST_CLIENT_UUID.toString());
    }

    @AfterEach
    void clearMdcClientId() {
        MDC.remove(ClientIdResolutionFilter.MDC_CLIENT_ID);
    }

    ClientSubscriptionRequest createRequest = ClientSubscriptionRequest.builder()
            .notificationEndpoint(NotificationEndpoint.builder().callbackUrl("https://example.com/callback").build())
            .eventTypes(List.of("PRISON_COURT_REGISTER_GENERATED"))
            .build();
    ClientSubscriptionRequest updateRequest = ClientSubscriptionRequest.builder()
            .notificationEndpoint(NotificationEndpoint.builder().callbackUrl("https://example.com/callback").build())
            .eventTypes(List.of("PRISON_COURT_REGISTER_GENERATED"))
            .build();
    UUID subscriptionId = UUID.randomUUID();

    @Test
    void create_controller_should_call_service() {
        ClientSubscription response = ClientSubscription.builder().clientSubscriptionId(subscriptionId).build();
        when(subscriptionService.createClientSubscription(createRequest, TEST_CLIENT_UUID)).thenReturn(response);

        var result = subscriptionController.createClientSubscription(createRequest, null);

        verify(subscriptionValidationService).validateClientDoesNotExist(TEST_CLIENT_UUID);
        verify(subscriptionService).createClientSubscription(createRequest, TEST_CLIENT_UUID);
        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void create_controller_should_throw_conflict_when_client_already_exists() {
        ResponseStatusException conflict = new ResponseStatusException(HttpStatus.CONFLICT,
                "subscription already exist with " + subscriptionId);
        doThrow(conflict)
                .when(subscriptionValidationService).validateClientDoesNotExist(TEST_CLIENT_UUID);

        assertThatThrownBy(() -> subscriptionController.createClientSubscription(createRequest, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void create_controller_should_throw_when_event_types_are_invalid() {
        doThrow(new IllegalArgumentException("Invalid event type(s): BAD"))
                .when(subscriptionService).createClientSubscription(createRequest, TEST_CLIENT_UUID);

        assertThatThrownBy(() -> subscriptionController.createClientSubscription(createRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid event type(s): BAD");
    }

    @Test
    void update_controller_should_call_service() {
        ClientSubscription response = ClientSubscription.builder().clientSubscriptionId(subscriptionId).build();
        when(subscriptionService.updateClientSubscription(TEST_CLIENT_UUID, subscriptionId, updateRequest)).thenReturn(response);

        var result = subscriptionController.updateClientSubscription(subscriptionId, updateRequest, null);

        verify(subscriptionValidationService).validateClientSubscriptionExists(TEST_CLIENT_UUID, subscriptionId);
        verify(subscriptionService).updateClientSubscription(TEST_CLIENT_UUID, subscriptionId, updateRequest);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void get_controller_should_call_service() {
        ClientSubscription response = ClientSubscription.builder().clientSubscriptionId(subscriptionId).build();
        when(subscriptionService.getClientSubscription(TEST_CLIENT_UUID, subscriptionId)).thenReturn(response);

        var result = subscriptionController.getClientSubscription(subscriptionId, null);

        verify(subscriptionValidationService).validateClientSubscriptionExists(TEST_CLIENT_UUID, subscriptionId);
        verify(subscriptionService).getClientSubscription(TEST_CLIENT_UUID, subscriptionId);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void delete_controller_should_call_service() {
        var result = subscriptionController.deleteClientSubscription(subscriptionId, null);

        verify(subscriptionValidationService).validateClientSubscriptionExists(TEST_CLIENT_UUID, subscriptionId);
        verify(subscriptionService).deleteClientSubscription(TEST_CLIENT_UUID, subscriptionId);
        assertThat(result.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void rotate_controller_should_return_200_with_hmac_credentials() {
        final RotateSecretRequest request = RotateSecretRequest.builder().keyId("kid-v1-existing").build();
        final HmacCredentials credentials = HmacCredentials.builder().keyId("kid-v1-existing").secret("bmV3U2VjcmV0").build();
        when(subscriptionService.rotateSubscriptionSecret(TEST_CLIENT_UUID, subscriptionId, request)).thenReturn(credentials);

        var result = subscriptionController.rotateClientSubscriptionSecret(subscriptionId, request, null);

        verify(subscriptionValidationService).validateClientSubscriptionExists(TEST_CLIENT_UUID, subscriptionId);
        verify(subscriptionService).rotateSubscriptionSecret(TEST_CLIENT_UUID, subscriptionId, request);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(credentials);
    }

    @Test
    void get_event_types_controller_should_call_event_type_service() {
        var result = subscriptionController.getEventTypes();
        verify(eventTypeService).getAllEventTypes();
        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }
}