package uk.gov.hmcts.cp.subscription.mappers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.entities.HearingEventPayloadEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HearingEventPayloadMapper {

    private final ClockService clockService;

    public HearingEventPayloadEntity toEntity(final UUID eventId, final Long eventTypeId, final EventPayload rawPayload) {
        return HearingEventPayloadEntity.builder()
                .eventId(eventId)
                .eventTypeId(eventTypeId)
                .rawPayload(rawPayload)
                .createdAt(clockService.nowOffsetUTC())
                .build();
    }

    public HearingEventSubscriptionEntity toSubscriptionEntity(final UUID subscriptionId, final UUID hearingEventId) {
        return HearingEventSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .hearingEventId(hearingEventId)
                .createdAt(clockService.nowOffsetUTC())
                .build();
    }
}