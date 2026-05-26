package uk.gov.hmcts.cp.subscription.mappers;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface ClientEntityMapper {

    @Mapping(source = "clientId", target = "id")
    @Mapping(target = "subscriptionId", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(source = "clientSubscription.notificationEndpoint.callbackUrl", target = "callbackUrl")
    @Mapping(target = "createdAt", expression = "java(clockService.nowOffsetUTC())")
    @Mapping(target = "updatedAt", expression = "java(clockService.nowOffsetUTC())")
    ClientEntity toEntity(@Context ClockService clockService, ClientSubscriptionRequest clientSubscription, UUID clientId);

    @Mapping(source = "existing.id", target = "id")
    @Mapping(source = "existing.subscriptionId", target = "subscriptionId")
    @Mapping(source = "request.notificationEndpoint", target = "callbackUrl", qualifiedByName = "mapFromNotificationEndpoint")
    @Mapping(source = "existing.createdAt", target = "createdAt")
    @Mapping(target = "updatedAt", expression = "java(clockService.nowOffsetUTC())")
    ClientEntity mapUpdateRequestToEntity(ClientEntity existing, @Context ClockService clockService, ClientSubscriptionRequest request);

    @Named("mapFromNotificationEndpoint")
    static String mapFromNotificationEndpoint(final NotificationEndpoint notificationEndpoint) {
        return notificationEndpoint.getCallbackUrl();
    }
}
