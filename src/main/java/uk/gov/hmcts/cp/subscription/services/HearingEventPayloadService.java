package uk.gov.hmcts.cp.subscription.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.entities.HearingEventPayloadEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;
import uk.gov.hmcts.cp.subscription.repositories.HearingEventPayloadRepository;
import uk.gov.hmcts.cp.subscription.repositories.HearingEventSubscriptionRepository;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HearingEventPayloadService {

    private final HearingEventPayloadRepository hearingEventPayloadRepository;
    private final HearingEventSubscriptionRepository hearingEventSubscriptionRepository;
    private final EventTypeRepository eventTypeRepository;
    private final ClockService clockService;

    @Transactional
    public UUID saveIfAbsent(final EventPayload eventPayload) {
        final UUID eventId = Objects.requireNonNull(eventPayload.getEventId(), "eventId must not be null");
        if (hearingEventPayloadRepository.existsByEventId(eventId)) {
            log.info("hearing_event_payload already exists for eventId={}, skipping", eventId);
            return null;
        }
        final Long eventTypeId = eventTypeRepository.findByEventName(eventPayload.getEventType())
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + eventPayload.getEventType()))
                .getId();
        final HearingEventPayloadEntity saved = hearingEventPayloadRepository.save(HearingEventPayloadEntity.builder()
                .eventId(eventId)
                .eventTypeId(eventTypeId)
                .rawPayload(eventPayload)
                .createdAt(clockService.nowOffsetUTC())
                .build());
        log.info("persisted hearing_event_payload for eventId={}", eventId);
        return saved.getHearingEventId();
    }

    @Transactional
    public void saveSubscriptionIfAbsent(final UUID subscriptionId, final UUID hearingEventId) {
        if (hearingEventSubscriptionRepository.existsBySubscriptionIdAndHearingEventId(subscriptionId, hearingEventId)) {
            log.info("hearing_event_subscription already exists for subscriptionId={}, hearingEventId={}, skipping",
                    subscriptionId, hearingEventId);
            return;
        }
        hearingEventSubscriptionRepository.save(HearingEventSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .hearingEventId(hearingEventId)
                .createdAt(clockService.nowOffsetUTC())
                .build());
        log.info("persisted hearing_event_subscription for subscriptionId={}, hearingEventId={}",
                subscriptionId, hearingEventId);
    }
}
