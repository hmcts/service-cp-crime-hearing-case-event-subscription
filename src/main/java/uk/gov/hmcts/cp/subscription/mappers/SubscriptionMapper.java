package uk.gov.hmcts.cp.subscription.mappers;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.EventType;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.model.EntityEventType;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Mapper(componentModel = "spring",
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
        nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface SubscriptionMapper {

    @Mapping(target = "id", expression = "java(null)")
    @Mapping(source = "request.eventTypes", target = "eventTypes", qualifiedByName = "mapWithSortedEventTypes")
    @Mapping(source = "request.notificationEndpoint", target = "notificationEndpoint", qualifiedByName = "mapFromNotificationEndpoint")
    @Mapping(target = "createdAt", expression = "java(clockService.nowOffsetUTC())")
    @Mapping(target = "updatedAt", expression = "java(clockService.nowOffsetUTC())")
    ClientSubscriptionEntity mapCreateRequestToEntity(@Context ClockService clockService, ClientSubscriptionRequest request);

    @Mapping(source = "existing.id", target = "id")
    @Mapping(source = "request.eventTypes", target = "eventTypes", qualifiedByName = "mapWithSortedEventTypes")
    @Mapping(source = "request.notificationEndpoint", target = "notificationEndpoint", qualifiedByName = "mapFromNotificationEndpoint")
    @Mapping(source = "existing.createdAt", target = "createdAt")
    @Mapping(expression = "java(clockService.nowOffsetUTC())", target = "updatedAt")
    ClientSubscriptionEntity mapUpdateRequestToEntity(@Context ClockService clockService, ClientSubscriptionEntity existing, ClientSubscriptionRequest request);

    @Mapping(source = "id", target = "clientSubscriptionId")
    @Mapping(target = "createdAt", expression = "java(clockService.now())")
    @Mapping(target = "updatedAt", expression = "java(clockService.now())")
    ClientSubscription mapEntityToResponse(@Context ClockService clockService, ClientSubscriptionEntity entity);

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
