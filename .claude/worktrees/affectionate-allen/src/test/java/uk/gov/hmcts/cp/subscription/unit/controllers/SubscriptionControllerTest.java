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
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.controllers.SubscriptionController;
import uk.gov.hmcts.cp.filters.ClientIdResolutionFilter;
import uk.gov.hmcts.cp.subscription.services.EventTypeService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    SubscriptionService subscriptionService;

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
    ClientSubscriptionRequest updateRequest = ClientSubscriptionRequest.builder().build();
    UUID subscriptionId = UUID.randomUUID();

    @Test
    void create_controller_should_call_service() {
        ClientSubscription response = ClientSubscription.builder().clientSubscriptionId(subscriptionId).build();
        when(subscriptionService.createSubscription(createRequest, TEST_CLIENT_UUID)).thenReturn(response);
        var result = subscriptionController.createClientSubscription(createRequest, null);
        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void update_controller_should_call_service() {
        ClientSubscription response = ClientSubscription.builder().clientSubscriptionId(subscriptionId).build();
        when(subscriptionService.updateSubscription(subscriptionId, updateRequest, TEST_CLIENT_UUID)).thenReturn(response);
        var result = subscriptionController.updateClientSubscription(subscriptionId, updateRequest, null);
        verify(subscriptionService).updateSubscription(subscriptionId, updateRequest, TEST_CLIENT_UUID);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void get_controller_should_call_service() {
        ClientSubscription response = ClientSubscription.builder().clientSubscriptionId(subscriptionId).build();
        when(subscriptionService.getSubscription(subscriptionId, TEST_CLIENT_UUID)).thenReturn(response);
        var result = subscriptionController.getClientSubscription(subscriptionId, null);
        verify(subscriptionService).getSubscription(subscriptionId, TEST_CLIENT_UUID);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void delete_controller_should_call_service() {
        var result = subscriptionController.deleteClientSubscription(subscriptionId, null);
        verify(subscriptionService).deleteSubscription(subscriptionId, TEST_CLIENT_UUID);
        assertThat(result.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void get_event_types_controller_should_call_event_type_service() {
        var result = subscriptionController.getEventTypes();
        verify(eventTypeService).getAllEventTypes();
        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }
}