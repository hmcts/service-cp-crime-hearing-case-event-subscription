package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.mappers.SubscriptionMapperImpl;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.repositories.SubscriptionRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    SubscriptionMapper mapper = new SubscriptionMapperImpl();

    @InjectMocks
    SubscriptionService subscriptionService;

    ClientSubscriptionRequest request = ClientSubscriptionRequest.builder().build();
    ClientSubscriptionEntity requestEntity = ClientSubscriptionEntity.builder().build();
    ClientSubscriptionEntity savedEntity = ClientSubscriptionEntity.builder().build();
    ClientSubscription response = ClientSubscription.builder().build();
    UUID subscriptionId = UUID.fromString("2ca16eb5-3998-4bb7-adce-4bb9b3b7223c");

    @Test
    void request_should_save_entity() {
        when(mapper.mapRequestToEntity(request)).thenReturn(requestEntity);
        when(subscriptionRepository.save(requestEntity)).thenReturn(savedEntity);
        when(mapper.mapEntityToResponse(savedEntity)).thenReturn(response);

        ClientSubscription result = subscriptionService.saveSubscription(request);

        verify(mapper).mapRequestToEntity(request);
        verify(subscriptionRepository).save(requestEntity);
        assertThat(result).isEqualTo(response);
    }

    @Test
    void request_should_get_entity() {
        when(subscriptionRepository.getReferenceById(subscriptionId)).thenReturn(savedEntity);
        when(mapper.mapEntityToResponse(savedEntity)).thenReturn(response);

        ClientSubscription result = subscriptionService.getSubscription(subscriptionId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void delete_should_delete_entity() {
        subscriptionService.deleteSubscription(subscriptionId);
        verify(subscriptionRepository).deleteById(subscriptionId);
    }
}