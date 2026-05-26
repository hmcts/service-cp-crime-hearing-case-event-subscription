package uk.gov.hmcts.cp.subscription.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.openapi.model.HearingEventResponse;
import uk.gov.hmcts.cp.subscription.entities.HearingEventPayloadEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.mappers.HearingEventMapper;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;
import uk.gov.hmcts.cp.subscription.repositories.HearingEventPayloadRepository;
import uk.gov.hmcts.cp.subscription.repositories.HearingEventSubscriptionRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HearingEventService {

    private final HearingEventPayloadRepository hearingEventPayloadRepository;
    private final HearingEventSubscriptionRepository hearingEventSubscriptionRepository;
    private final EventTypeRepository eventTypeRepository;
    private final HearingEventMapper hearingEventPayloadMapper;
    private final JsonMapper jsonMapper;

    public HearingEventResponse getHearingEvent(final UUID subscriptionId, final UUID hearingEventId) {
        final HearingEventSubscriptionEntity subscription = hearingEventSubscriptionRepository
                .findByIdAndSubscriptionId(hearingEventId, subscriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "hearingEventId not found for this subscriber"));
        final HearingEventPayloadEntity payload = hearingEventPayloadRepository
                .findById(subscription.getHearingEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "hearing_event_payload missing for hearing_event_id: " + subscription.getHearingEventId()));
        log.info("getHearingEvent subscriptionId={} hearingEventId={}", subscriptionId, hearingEventId);
        return HearingEventResponse.builder()
                .hearingEventId(subscription.getId())
                .eventType(payload.getRawPayload().getEventType())
                .createdAt(payload.getCreatedAt().toInstant())
                .payload(jsonMapper.toMap(payload.getRawPayload()))
                .build();
    }

    @Transactional
    public UUID saveIfAbsent(final EventPayload eventPayload) {
        final UUID eventId = Objects.requireNonNull(eventPayload.getEventId(), "eventId must not be null");

        final Optional<HearingEventPayloadEntity> existing = hearingEventPayloadRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            log.info("hearing_event_payload already exists for eventId={}, skipping", eventId);
            return existing.get().getHearingEventId();
        }

        final Long eventTypeId = eventTypeRepository.findByEventName(eventPayload.getEventType())
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + eventPayload.getEventType()))
                .getId();
        final HearingEventPayloadEntity saved = hearingEventPayloadRepository.save(
                hearingEventPayloadMapper.toEntity(eventId, eventTypeId, eventPayload));
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
        hearingEventSubscriptionRepository.save(
                hearingEventPayloadMapper.toSubscriptionEntity(subscriptionId, hearingEventId));
        log.info("persisted hearing_event_subscription for subscriptionId={}, hearingEventId={}",
                subscriptionId, hearingEventId);
    }
}