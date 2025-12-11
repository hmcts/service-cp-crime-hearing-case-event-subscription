package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.entities.SubscriptionEntity;
import uk.gov.hmcts.cp.mappers.SubscriptionMapper;
import uk.gov.hmcts.cp.mappers.SubscriptionMapperImpl;
import uk.gov.hmcts.cp.openapi.model.Result;
import uk.gov.hmcts.cp.repositories.SubscriptionRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    SubscriptionRepository subscriptionRepository;
    @Spy
    SubscriptionMapper mapper = new SubscriptionMapperImpl();

    @InjectMocks
    SubscriptionService subscriptionService;

    SubscriptionEntity subscriptionEntity = SubscriptionEntity.builder().notifyUrl("url").build();

    UUID subscriberId = UUID.fromString("2413d0fd-8a75-4fb6-828d-1a1fb7ae7290");
    UUID subscriptionId = UUID.fromString("ee2e6e17-7c37-45ec-a1d4-4023500fb90d");

    @Test
    void get_single_subscription_should_return_single() {
        when(subscriptionRepository.getReferenceById(subscriptionId)).thenReturn(subscriptionEntity);
        Result response = subscriptionService.getSubscriptionById(subscriptionId);
        assertThat(response.getResultText()).isEqualTo("url");
    }

    @Test
    void get_subscription_by_subscriber_id_should_return() {
        when(subscriptionRepository.getBySubscriberId(subscriberId)).thenReturn(List.of(subscriptionEntity));
        List<Result> response = subscriptionService.getSubscriptionsBySubscriber(subscriberId);
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getResultText()).isEqualTo("url");
    }
}