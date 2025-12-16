package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.openapi.model.EventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.openapi.model.EventType.PCR;

class SubscriptionMapperTest {

    SubscriptionMapper mapper = new SubscriptionMapperImpl();

    UUID clientNotificationId = UUID.fromString("d730c6e1-66ba-4ef0-a3dd-0b9928faa76d");
    NotificationEndpoint notificationEndpoint = NotificationEndpoint.builder()
            .webhookUrl("https://example.com")
            .build();
    OffsetDateTime createdAt = OffsetDateTime.now().minusDays(2);
    OffsetDateTime updatedAt = OffsetDateTime.now().minusHours(2);

    @Test
    void request_should_map_to_entity_with_sorted_types() {
        ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
                .notificationEndpoint(notificationEndpoint)
                .eventTypes(List.of(PCR, CUSTODIAL_RESULT))
                .build();

        ClientSubscriptionEntity entity = mapper.mapRequestToEntity(request);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getNotificationEndpoint()).isEqualTo("https://example.com");
        assertThat(entity.getEventTypes().toString()).isEqualTo("[CUSTODIAL_RESULT, PCR]");
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void entity_should_map_to_response() {
        ClientSubscriptionEntity clientSubscriptionEntity = ClientSubscriptionEntity.builder()
                .id(clientNotificationId)
                .notificationEndpoint(notificationEndpoint.getWebhookUrl().toString())
                .eventTypes(List.of(EntityEventType.CUSTODIAL_RESULT, EntityEventType.PCR))
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        ClientSubscription subscription = mapper.mapEntityToResponse(clientSubscriptionEntity);

        assertThat(subscription.getClientSubscriptionId()).isEqualTo(clientNotificationId);
        assertThat(subscription.getNotificationEndpoint()).isEqualTo(notificationEndpoint);
        assertThat(subscription.getEventTypes().toString()).isEqualTo("[CUSTODIAL_RESULT, PCR]");
        assertThat(subscription.getCreatedAt()).isEqualTo(createdAt);
        assertThat(subscription.getUpdatedAt()).isEqualTo(updatedAt);
    }
}