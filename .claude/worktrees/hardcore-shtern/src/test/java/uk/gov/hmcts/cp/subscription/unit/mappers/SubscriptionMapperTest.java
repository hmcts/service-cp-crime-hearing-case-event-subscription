package uk.gov.hmcts.cp.subscription.unit.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.hmac.services.EncodingService;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.mappers.SubscriptionMapperImpl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SubscriptionMapperTest {

    @InjectMocks
    SubscriptionMapperImpl mapper;

    OffsetDateTime createdAt = OffsetDateTime.of(2025, 12, 1, 11, 30, 50, 582007048, ZoneOffset.UTC);
    OffsetDateTime updatedAt = OffsetDateTime.of(2025, 12, 2, 12, 40, 55, 234567890, ZoneOffset.UTC);
    UUID subscriptionId = UUID.fromString("d730c6e1-66ba-4ef0-a3dd-0b9928faa76d");
    UUID clientId = UUID.fromString("161901db-f7dc-4126-a574-86ea801298b5");
    NotificationEndpoint notificationEndpoint = NotificationEndpoint.builder()
            .callbackUrl("https://example.com")
            .build();
    UUID testClientUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
    ClientSubscriptionEntity existing = ClientSubscriptionEntity.builder()
            .id(subscriptionId)
            .clientId(testClientUuid)
            .notificationEndpoint(notificationEndpoint.getCallbackUrl().toString())
            .eventTypes(mutableLisOfEventTypes())
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    KeyPair hmac = KeyPair.builder().keyId("keyId").secret("secret".getBytes()).build();

    @Test
    void create_request_should_map_to_entity() {
        ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
                .notificationEndpoint(notificationEndpoint)
                .eventTypes(List.of("PRISON_COURT_REGISTER_GENERATED"))
                .build();

        ClientSubscriptionEntity entity = mapper.mapCreateRequestToEntity(clientId, "kid-v1", request, createdAt);

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getClientId()).isEqualTo(clientId);
        assertThat(entity.getNotificationEndpoint()).isEqualTo("https://example.com");
        assertThat(entity.getEventTypes().toString()).isEqualTo("[PRISON_COURT_REGISTER_GENERATED]");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(createdAt);
        assertThat(entity.getHmacKeyId()).isEqualTo("kid-v1");
    }

    @Test
    void update_request_should_map_to_entity() {
        NotificationEndpoint updatedEndpoint = NotificationEndpoint.builder()
                .callbackUrl("https://updated.com")
                .build();
        ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
                .notificationEndpoint(updatedEndpoint)
                .eventTypes(List.of("PRISON_COURT_REGISTER_GENERATED"))
                .build();
        ClientSubscriptionEntity entity = mapper.mapUpdateRequestToEntity(existing, request, createdAt);

        assertThat(entity.getId()).isEqualTo(subscriptionId);
        assertThat(entity.getClientId()).isEqualTo(testClientUuid);
        assertThat(entity.getNotificationEndpoint()).isEqualTo("https://updated.com");
        assertThat(entity.getEventTypes().toString()).isEqualTo("[PRISON_COURT_REGISTER_GENERATED]");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(createdAt);
    }

    @Test
    void entity_should_map_to_response() {
        ClientSubscription subscription = mapper.mapEntityToResponse(existing, hmac);

        assertThat(subscription.getClientSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(subscription.getNotificationEndpoint()).isEqualTo(notificationEndpoint);
        assertThat(subscription.getEventTypes().toString()).isEqualTo("[PRISON_COURT_REGISTER_GENERATED]");
        assertThat(subscription.getCreatedAt()).isEqualTo(createdAt.toInstant());
        assertThat(subscription.getUpdatedAt()).isEqualTo(updatedAt.toInstant());
        assertThat(subscription.getHmac().getKeyId()).isEqualTo(hmac.getKeyId());
        String expectedSecretString = new EncodingService().encodeWithBase64(hmac.getSecret());
        assertThat(subscription.getHmac().getSecret()).isEqualTo(expectedSecretString);
    }

    private List<String> mutableLisOfEventTypes() {
        List<String> mutableList = new ArrayList<>();
        mutableList.add("PRISON_COURT_REGISTER_GENERATED");
        return mutableList;
    }
}
