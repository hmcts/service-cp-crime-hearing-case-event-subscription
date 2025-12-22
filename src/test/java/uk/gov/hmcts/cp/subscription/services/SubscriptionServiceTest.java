package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.subscription.mappers.SubscriptionMapperImpl;
import uk.gov.hmcts.cp.subscription.repositories.SubscriptionRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock ClockService clockService;
    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    SubscriptionMapper mapper = new SubscriptionMapperImpl();

    @InjectMocks
    SubscriptionService subscriptionService;

    ClientSubscriptionRequest request = ClientSubscriptionRequest.builder().build();
    ClientSubscriptionEntity requestEntity = ClientSubscriptionEntity.builder().build();
    ClientSubscriptionEntity savedEntity = ClientSubscriptionEntity.builder().build();
    ClientSubscriptionEntity updatedEntity = ClientSubscriptionEntity.builder().build();
    ClientSubscription response = ClientSubscription.builder().build();
    UUID subscriptionId = UUID.fromString("2ca16eb5-3998-4bb7-adce-4bb9b3b7223c");

    @Test
    void save_request_should_save_new_entity() {
        when(mapper.mapCreateRequestToEntity(clockService, request)).thenReturn(requestEntity);
        when(subscriptionRepository.save(requestEntity)).thenReturn(savedEntity);
        when(mapper.mapEntityToResponse(savedEntity)).thenReturn(response);

        ClientSubscription result = subscriptionService.saveSubscription(request);

        verify(mapper).mapCreateRequestToEntity(clockService, request);
        verify(subscriptionRepository).save(requestEntity);
        assertThat(result).isEqualTo(response);
    }

    @Test
    void update_request_should_update_existing_entity() {
        when(subscriptionRepository.getReferenceById(subscriptionId)).thenReturn(savedEntity);
        when(mapper.mapUpdateRequestToEntity(clockService, savedEntity, request)).thenReturn(requestEntity);
        when(subscriptionRepository.save(requestEntity)).thenReturn(updatedEntity);
        when(mapper.mapEntityToResponse(updatedEntity)).thenReturn(response);

        ClientSubscription result = subscriptionService.updateSubscription(subscriptionId, request);

        verify(subscriptionRepository).save(requestEntity);
        assertThat(result).isEqualTo(response);
    }

    @Test
    void delete_should_delete_entity() {
        subscriptionService.deleteSubscription(subscriptionId);
        verify(subscriptionRepository).deleteById(subscriptionId);
    }
}