package uk.gov.hmcts.cp.subscription.unit.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.hmac.services.EncodingService;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.mappers.ClientSubscriptionMapperImpl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ClientSubscriptionMapperTest {

    @InjectMocks
    ClientSubscriptionMapperImpl mapper;

    OffsetDateTime createdAt = OffsetDateTime.of(2025, 12, 1, 11, 30, 50, 582007048, ZoneOffset.UTC);
    OffsetDateTime updatedAt = OffsetDateTime.of(2025, 12, 2, 12, 40, 55, 234567890, ZoneOffset.UTC);
    UUID subscriptionId = UUID.fromString("d730c6e1-66ba-4ef0-a3dd-0b9928faa76d");
    UUID clientId = UUID.fromString("161901db-f7dc-4126-a574-86ea801298b5");
    NotificationEndpoint notificationEndpoint = NotificationEndpoint.builder()
            .callbackUrl("https://example.com")
            .build();
    ClientEntity clientEntity = ClientEntity.builder()
            .callbackUrl("https://example.com")
            .id(clientId)
            .subscriptionId(subscriptionId)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    KeyPair hmac = KeyPair.builder().keyId("keyId").secret("secret".getBytes()).build();

    @Test
    void map_to_dto_should_map_to_client_subscription() {

        ClientSubscription clientSubscription = mapper.toDto(clientEntity, List.of("PRISON_COURT_REGISTER_GENERATED"), hmac);

        assertThat(clientSubscription.getClientSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(clientSubscription.getEventTypes()).isEqualTo(List.of("PRISON_COURT_REGISTER_GENERATED"));
        assertThat(clientSubscription.getNotificationEndpoint().getCallbackUrl()).isEqualTo("https://example.com");
        assertThat(clientSubscription.getCreatedAt()).isEqualTo(createdAt.toInstant());
        assertThat(clientSubscription.getUpdatedAt()).isEqualTo(updatedAt.toInstant());
        assertThat(clientSubscription.getHmac().getKeyId()).isEqualTo(hmac.getKeyId());
        String expectedSecretString = new EncodingService().encodeWithBase64(hmac.getSecret());
        assertThat(clientSubscription.getHmac().getSecret()).isEqualTo(expectedSecretString);
    }
}
