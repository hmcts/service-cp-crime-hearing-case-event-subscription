package uk.gov.hmcts.cp.subscription.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.EventType;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "id", expression = "java(null)")
    @Mapping(source = "request.eventTypes", target = "eventTypes", qualifiedByName = "mapWithSortedEventTypes")
    @Mapping(source = "request.notificationEndpoint", target = "notificationEndpoint", qualifiedByName = "mapFromNotificationEndpoint")
    @Mapping(target = "createdAt", expression = "java(java.time.OffsetDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.OffsetDateTime.now())")
    ClientSubscriptionEntity mapCreateRequestToEntity(ClientSubscriptionRequest request);

    @Mapping(source = "existing.id", target = "id")
    @Mapping(source = "request.eventTypes", target = "eventTypes", qualifiedByName = "mapWithSortedEventTypes")
    @Mapping(source = "request.notificationEndpoint", target = "notificationEndpoint", qualifiedByName = "mapFromNotificationEndpoint")
    @Mapping(source = "existing.createdAt", target = "createdAt")
    @Mapping(expression = "java(java.time.OffsetDateTime.now())", target = "updatedAt")
    ClientSubscriptionEntity mapUpdateRequestToEntity(ClientSubscriptionEntity existing, ClientSubscriptionRequest request);

    @Mapping(source = "id", target = "clientSubscriptionId")
    ClientSubscription mapEntityToResponse(ClientSubscriptionEntity entity);

    @Named("mapWithSortedEventTypes")
    static List<EntityEventType> sortedEventTypes(final List<EventType> events) {
        final List<String> sorted = events.stream().map(e -> e.name()).sorted().collect(toList());
        return sorted.stream().map(e -> EntityEventType.valueOf(e)).toList();
    }

    @Named("mapFromNotificationEndpoint")
    static String mapFromNotificationEndpoint(final NotificationEndpoint notificationEndpoint) {
        return notificationEndpoint.getWebhookUrl().toString();
    }

    static NotificationEndpoint mapToNotificationEndpoint(final String endpointUrl) {
        return NotificationEndpoint.builder().webhookUrl(endpointUrl).build();
    }
}
