package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventPayloadEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.mappers.HearingEventMapper;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;
import uk.gov.hmcts.cp.subscription.repositories.HearingEventPayloadRepository;
import uk.gov.hmcts.cp.subscription.repositories.HearingEventSubscriptionRepository;

import java.util.Optional;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingEventServiceTest {

    @Mock
    private HearingEventPayloadRepository hearingEventPayloadRepository;
    @Mock
    private HearingEventSubscriptionRepository hearingEventSubscriptionRepository;
    @Mock
    private EventTypeRepository eventTypeRepository;
    @Mock
    private HearingEventMapper hearingEventPayloadMapper;

    @InjectMocks
    private HearingEventService hearingEventPayloadService;

    private final UUID eventId = randomUUID();
    private final EventPayload eventPayload = EventPayload.builder()
            .eventId(eventId)
            .eventType("PRISON_COURT_REGISTER_GENERATED")
            .build();
    private final EventTypeEntity eventTypeEntity = EventTypeEntity.builder()
            .id(1L)
            .eventName("PRISON_COURT_REGISTER_GENERATED")
            .build();

    @Test
    void saveIfAbsent_should_persist_when_not_exists() {
        HearingEventPayloadEntity entity = HearingEventPayloadEntity.builder()
                .eventId(eventId)
                .eventTypeId(1L)
                .rawPayload(eventPayload)
                .build();
        when(hearingEventPayloadRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED")).thenReturn(Optional.of(eventTypeEntity));
        when(hearingEventPayloadMapper.toEntity(eventId, 1L, eventPayload)).thenReturn(entity);
        when(hearingEventPayloadRepository.save(entity)).thenReturn(entity);

        hearingEventPayloadService.saveIfAbsent(eventPayload);

        verify(hearingEventPayloadMapper).toEntity(eventId, 1L, eventPayload);
        verify(hearingEventPayloadRepository).save(entity);
    }

    @Test
    void saveIfAbsent_should_skip_when_already_exists() {
        UUID hearingEventId = randomUUID();
        HearingEventPayloadEntity existing = HearingEventPayloadEntity.builder()
                .hearingEventId(hearingEventId)
                .eventId(eventId)
                .build();
        when(hearingEventPayloadRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));

        UUID result = hearingEventPayloadService.saveIfAbsent(eventPayload);

        assertThat(result).isEqualTo(hearingEventId);
        verify(hearingEventPayloadRepository, never()).save(any());
        verify(hearingEventPayloadMapper, never()).toEntity(any(), any(), any());
    }

    @Test
    void saveIfAbsent_should_throw_when_event_id_is_null() {
        EventPayload nullIdPayload = EventPayload.builder()
                .eventType("PRISON_COURT_REGISTER_GENERATED")
                .build();

        assertThatThrownBy(() -> hearingEventPayloadService.saveIfAbsent(nullIdPayload))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventId must not be null");
    }

    @Test
    void saveIfAbsent_should_throw_when_event_type_is_unknown() {
        when(hearingEventPayloadRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hearingEventPayloadService.saveIfAbsent(eventPayload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRISON_COURT_REGISTER_GENERATED");
    }

    @Test
    void saveSubscriptionIfAbsent_should_persist_when_not_exists() {
        UUID subscriptionId = randomUUID();
        UUID hearingEventId = randomUUID();
        HearingEventSubscriptionEntity entity = HearingEventSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .hearingEventId(hearingEventId)
                .build();
        when(hearingEventSubscriptionRepository.existsBySubscriptionIdAndHearingEventId(subscriptionId, hearingEventId)).thenReturn(false);
        when(hearingEventPayloadMapper.toSubscriptionEntity(subscriptionId, hearingEventId)).thenReturn(entity);

        hearingEventPayloadService.saveSubscriptionIfAbsent(subscriptionId, hearingEventId);

        verify(hearingEventPayloadMapper).toSubscriptionEntity(subscriptionId, hearingEventId);
        verify(hearingEventSubscriptionRepository).save(entity);
    }

    @Test
    void saveSubscriptionIfAbsent_should_skip_when_already_exists() {
        UUID subscriptionId = randomUUID();
        UUID hearingEventId = randomUUID();
        when(hearingEventSubscriptionRepository.existsBySubscriptionIdAndHearingEventId(subscriptionId, hearingEventId)).thenReturn(true);

        hearingEventPayloadService.saveSubscriptionIfAbsent(subscriptionId, hearingEventId);

        verify(hearingEventSubscriptionRepository, never()).save(any());
    }
}
