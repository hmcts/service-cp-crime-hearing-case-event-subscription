package uk.gov.hmcts.cp.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.hmcts.cp.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;

import java.net.URI;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubscriptionMapper {

    ClientSubscriptionEntity mapRequestToEntity(ClientSubscriptionRequest request);

    @Mapping(source = "id", target = "clientSubscriptionId")
    ClientSubscription mapEntityToResponse(ClientSubscriptionEntity subscription);

    static String mapFromNotificationEndpoint(final NotificationEndpoint notificationEndpoint) {
        return notificationEndpoint.getWebhookUrl().toString();
    }

    static NotificationEndpoint mapToNotificationEndpoint(final String endpointUrl) {
        final URI uri = URI.create(endpointUrl);
        return NotificationEndpoint.builder().webhookUrl(uri).build();
    }
}
