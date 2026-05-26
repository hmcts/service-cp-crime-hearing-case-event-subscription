package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientEntityMapperTest {

    @Mock
    ClockService clockService;

    ClientEntityMapper clientMapper = new ClientEntityMapperImpl();

    @Test
    void mapToClientEntity_should_map_client_subscription_to_entity() {
        UUID clientId = UUID.randomUUID();
        String callbackUrl = "https://example.com/callback";

        OffsetDateTime now = OffsetDateTime.now();
        when(clockService.nowOffsetUTC()).thenReturn(now);

        ClientSubscriptionRequest clientSubscription = ClientSubscriptionRequest.builder()
                .notificationEndpoint(NotificationEndpoint.builder()
                        .callbackUrl(callbackUrl)
                        .build())
                .build();

        ClientEntity result = clientMapper.toEntity(clockService, clientSubscription, clientId);

        assertThat(result.getId()).isEqualTo(clientId);
        assertThat(result.getSubscriptionId()).isNotNull(); // Subscription ID should be generated
        assertThat(result.getCallbackUrl()).isEqualTo(callbackUrl);
        assertThat(result.getCreatedAt()).isEqualTo(now);
        assertThat(result.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void mapUpdateRequestToClientEntity_should_map_existed_client_entity_to_updated_entity() {
        UUID clientId = UUID.randomUUID();
        UUID subsciptionId = UUID.randomUUID();

        OffsetDateTime now = OffsetDateTime.now();
        when(clockService.nowOffsetUTC()).thenReturn(now);

        ClientEntity existing = ClientEntity.builder()
                .callbackUrl("https://example.com/callback")
                .id(clientId)
                .subscriptionId(subsciptionId)
                .createdAt(now.minusMinutes(10))
                .build();

        ClientSubscriptionRequest clientSubscription = ClientSubscriptionRequest.builder()
                .notificationEndpoint(NotificationEndpoint.builder()
                        .callbackUrl("https://updated.com/callback")
                        .build())
                .build();

        ClientEntity result = clientMapper.mapUpdateRequestToEntity(existing, clockService, clientSubscription);

        assertThat(result.getId()).isEqualTo(clientId);
        assertThat(result.getSubscriptionId()).isEqualTo(subsciptionId);
        assertThat(result.getCallbackUrl()).isEqualTo("https://updated.com/callback");
        assertThat(result.getCreatedAt()).isEqualTo(existing.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(now);
    }
}
