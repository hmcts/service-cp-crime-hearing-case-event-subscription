package uk.gov.hmcts.cp.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import uk.gov.hmcts.cp.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.model.EntityEventType;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.EventType;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;

import java.net.URI;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubscriptionMapper {

    @Mapping(source = "request.eventTypes", target = "eventTypes", qualifiedByName = "sortedEventTypes")
    @Mapping(target = "createdAt", expression = "java(java.time.OffsetDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.OffsetDateTime.now())")
    ClientSubscriptionEntity mapRequestToEntity(ClientSubscriptionRequest request);

    @Mapping(source = "id", target = "clientSubscriptionId")
    ClientSubscription mapEntityToResponse(ClientSubscriptionEntity entity);

    @Named("sortedEventTypes")
    static List<EntityEventType> sortedEventTypes(final List<EventType> events) {
        final List<String> sorted = events.stream().map(e -> e.name()).sorted().collect(toList());
        return sorted.stream().map(e -> EntityEventType.valueOf(e)).toList();
    }

    static String mapFromNotificationEndpoint(final NotificationEndpoint notificationEndpoint) {
        return notificationEndpoint.getWebhookUrl().toString();
    }

    static NotificationEndpoint mapToNotificationEndpoint(final String endpointUrl) {
        return NotificationEndpoint.builder().webhookUrl(endpointUrl).build();
    }
}
