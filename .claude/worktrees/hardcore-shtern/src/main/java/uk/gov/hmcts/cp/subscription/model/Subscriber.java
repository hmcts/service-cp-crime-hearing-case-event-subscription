package uk.gov.hmcts.cp.subscription.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class Subscriber {
    private final UUID id;
    private final List<String> eventTypes;
    private final String notificationEndpoint;
    private final UUID clientId;
    private final String hmacKeyId;
}
