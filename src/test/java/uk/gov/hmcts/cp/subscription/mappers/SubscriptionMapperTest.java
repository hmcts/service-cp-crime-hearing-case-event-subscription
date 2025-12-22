package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.openapi.model.EventType.CUSTODIAL_RESULT;
import static uk.gov.hmcts.cp.openapi.model.EventType.PCR;

@ExtendWith(MockitoExtension.class)
class SubscriptionMapperTest {

    private final static OffsetDateTime MOCKCREATED = OffsetDateTime.of(2025, 12, 1, 11, 30, 50, 582007048, ZoneOffset.UTC);
    private final static OffsetDateTime MOCKUPDATED = OffsetDateTime.of(2025, 12, 2, 12, 40, 55, 234567890, ZoneOffset.UTC);

    @Mock
    ClockService clockService;

    SubscriptionMapper mapper = new SubscriptionMapperImpl();

    UUID clientSubscriptionId = UUID.fromString("d730c6e1-66ba-4ef0-a3dd-0b9928faa76d");
    NotificationEndpoint notificationEndpoint = NotificationEndpoint.builder()
            .webhookUrl("https://example.com")
            .build();
    ClientSubscriptionEntity existing = ClientSubscriptionEntity.builder()
            .id(clientSubscriptionId)
            .notificationEndpoint(notificationEndpoint.getWebhookUrl().toString())
            .eventTypes(mutableLisOfEventTypes())
            .createdAt(MOCKCREATED)
            .updatedAt(MOCKCREATED)
            .build();

    @Test
    void create_request_should_map_to_entity_with_sorted_types() {
        when(clockService.now()).thenReturn(MOCKCREATED);
        ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
                .notificationEndpoint(notificationEndpoint)
                .eventTypes(List.of(PCR, CUSTODIAL_RESULT))
                .build();

        ClientSubscriptionEntity entity = mapper.mapCreateRequestToEntity(clockService, request);

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
        when(clockService.now()).thenReturn(MOCKUPDATED);
        ClientSubscriptionEntity entity = mapper.mapUpdateRequestToEntity(clockService, existing, request);

        assertThat(entity.getId()).isEqualTo(clientSubscriptionId);
        assertThat(entity.getNotificationEndpoint()).isEqualTo("https://updated.com");
        assertThat(entity.getEventTypes().toString()).isEqualTo("[CUSTODIAL_RESULT]");
        assertThat(entity.getCreatedAt()).isEqualTo(MOCKCREATED);
        assertThat(entity.getUpdatedAt()).isEqualTo(MOCKUPDATED);
    }

    @Test
    void entity_should_map_to_response() {
        ClientSubscription subscription = mapper.mapEntityToResponse(existing);

        assertThat(subscription.getClientSubscriptionId()).isEqualTo(clientSubscriptionId);
        assertThat(subscription.getNotificationEndpoint()).isEqualTo(notificationEndpoint);
        assertThat(subscription.getEventTypes().toString()).isEqualTo("[CUSTODIAL_RESULT, PCR]");
        assertThat(subscription.getCreatedAt()).isEqualTo(MOCKCREATED);
        assertThat(subscription.getUpdatedAt()).isEqualTo(MOCKCREATED);
    }

    private List<EntityEventType> mutableLisOfEventTypes() {
        List<EntityEventType> mutableList = new ArrayList<>();
        mutableList.add(EntityEventType.CUSTODIAL_RESULT);
        mutableList.add(EntityEventType.PCR);
        return mutableList;
    }
}
