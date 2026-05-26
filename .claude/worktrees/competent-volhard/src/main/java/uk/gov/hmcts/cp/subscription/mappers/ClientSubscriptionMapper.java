package uk.gov.hmcts.cp.subscription.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.hmac.services.EncodingService;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Mapper(componentModel = "spring",
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT,
        nullValueMapMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface ClientSubscriptionMapper {

    @Mapping(source = "entity.subscriptionId", target = "clientSubscriptionId")
    @Mapping(source = "entity.callbackUrl", target = "notificationEndpoint", qualifiedByName = "mapToNotificationEndpoint")
    @Mapping(target = "eventTypes", source = "eventTypes", qualifiedByName = "mapWithSortedEventTypes")
    @Mapping(target = "createdAt", expression = "java(entity.getCreatedAt().toInstant())")
    @Mapping(target = "updatedAt", expression = "java(entity.getUpdatedAt().toInstant())")
    @Mapping(source = "hmac.keyId", target = "hmac.keyId")
    @Mapping(source = "hmac.secret", target = "hmac.secret", qualifiedByName = "encodeToBase64")
    ClientSubscription toDto(ClientEntity entity, List<String> eventTypes, KeyPair hmac);

    @Named("encodeToBase64")
    static String encodeToBase64(final byte[] bytes) {
        return new EncodingService().encodeWithBase64(bytes);
    }

    @Named("mapWithSortedEventTypes")
    static List<String> sortedEventTypes(final List<String> events) {
        return events.stream().sorted().collect(toList());
    }

    @Named("mapToNotificationEndpoint")
    static NotificationEndpoint mapToNotificationEndpoint(final String endpointUrl) {
        return NotificationEndpoint.builder().callbackUrl(endpointUrl).build();
    }
}
