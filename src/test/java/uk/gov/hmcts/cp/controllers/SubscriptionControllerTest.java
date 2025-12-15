package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.services.SubscriptionService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    SubscriptionService subscriptionService;

    @InjectMocks
    SubscriptionController subscriptionController;

    ClientSubscriptionRequest request = ClientSubscriptionRequest.builder().build();

    @Test
    void create_controller_should_call_service() {
        UUID subscriptionId = UUID.randomUUID();
        ClientSubscription response = ClientSubscription.builder().clientSubscriptionId(subscriptionId).build();
        when(subscriptionService.saveSubscription(any())).thenReturn(response);
        subscriptionController.createClientSubscription(request);
        verify(subscriptionService).saveSubscription(request);
    }

    @Test
    void get_controller_should_call_service() {
        UUID subscriptionId = UUID.randomUUID();
        ClientSubscription response = ClientSubscription.builder().clientSubscriptionId(subscriptionId).build();
        when(subscriptionService.getSubscription(subscriptionId)).thenReturn(response);
        ResponseEntity<ClientSubscription> result = subscriptionController.getClientSubscription(subscriptionId);
        assertThat(result.getBody()).isEqualTo(response);
    }
}