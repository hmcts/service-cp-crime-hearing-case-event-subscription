package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.openapi.model.EventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.openapi.model.EventType.PCR;

class SubscriptionMapperTest {

    SubscriptionMapper mapper = new SubscriptionMapperImpl();

    UUID clientSubscriptionId = UUID.fromString("d730c6e1-66ba-4ef0-a3dd-0b9928faa76d");
    NotificationEndpoint notificationEndpoint = NotificationEndpoint.builder()
            .webhookUrl("https://example.com")
            .build();
    OffsetDateTime createdAt = OffsetDateTime.now().minusDays(2);
    OffsetDateTime updatedAt = OffsetDateTime.now().minusHours(2);
    ClientSubscriptionEntity existing = ClientSubscriptionEntity.builder()
            .id(clientSubscriptionId)
            .notificationEndpoint(notificationEndpoint.getWebhookUrl().toString())
            .eventTypes(mutableLisOfEventTypes())
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    @Test
    void create_request_should_map_to_entity_with_sorted_types() {
        ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
                .notificationEndpoint(notificationEndpoint)
                .eventTypes(List.of(PCR, CUSTODIAL_RESULT))
                .build();

        ClientSubscriptionEntity entity = mapper.mapCreateRequestToEntity(request);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getNotificationEndpoint()).isEqualTo("https://example.com");
        assertThat(entity.getEventTypes().toString()).isEqualTo("[CUSTODIAL_RESULT, PCR]");
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void update_request_should_map_to_entity_with_sorted_types() {
        NotificationEndpoint updatedEndpoint = NotificationEndpoint.builder()
                .webhookUrl("https://updated.com")
                .build();
        ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
                .notificationEndpoint(updatedEndpoint)
                .eventTypes(List.of(CUSTODIAL_RESULT))
                .build();

        ClientSubscriptionEntity entity = mapper.mapUpdateRequestToEntity(existing, request);

        assertThat(entity.getId()).isEqualTo(clientSubscriptionId);
        assertThat(entity.getNotificationEndpoint()).isEqualTo("https://updated.com");
        assertThat(entity.getEventTypes().toString()).isEqualTo("[CUSTODIAL_RESULT]");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void entity_should_map_to_response() {
        ClientSubscription subscription = mapper.mapEntityToResponse(existing);

        assertThat(subscription.getClientSubscriptionId()).isEqualTo(clientSubscriptionId);
        assertThat(subscription.getNotificationEndpoint()).isEqualTo(notificationEndpoint);
        assertThat(subscription.getEventTypes().toString()).isEqualTo("[CUSTODIAL_RESULT, PCR]");
        assertThat(subscription.getCreatedAt()).isEqualTo(createdAt);
        assertThat(subscription.getUpdatedAt()).isEqualTo(updatedAt);
    }

    private List<EntityEventType> mutableLisOfEventTypes() {
        List<EntityEventType> mutableList = new ArrayList<>();
        mutableList.add(EntityEventType.CUSTODIAL_RESULT);
        mutableList.add(EntityEventType.PCR);
        return mutableList;
    }
}
