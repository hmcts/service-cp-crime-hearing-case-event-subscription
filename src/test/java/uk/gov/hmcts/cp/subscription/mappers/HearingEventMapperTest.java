package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.entities.HearingEventPayloadEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingEventMapperTest {

    @Mock
    private ClockService clockService;

    @InjectMocks
    private HearingEventMapper hearingEventPayloadMapper;

    private final OffsetDateTime fixedNow = OffsetDateTime.of(2024, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void toEntity_should_map_all_fields() {
        UUID eventId = randomUUID();
        Long eventTypeId = 42L;
        EventPayload rawPayload = EventPayload.builder()
                .eventId(eventId)
                .eventType("PRISON_COURT_REGISTER_GENERATED")
                .build();
        when(clockService.nowOffsetUTC()).thenReturn(fixedNow);

        HearingEventPayloadEntity result = hearingEventPayloadMapper.toEntity(eventId, eventTypeId, rawPayload);

        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getEventTypeId()).isEqualTo(eventTypeId);
        assertThat(result.getRawPayload()).isEqualTo(rawPayload);
        assertThat(result.getCreatedAt()).isEqualTo(fixedNow);
        assertThat(result.getHearingEventId()).isNull();
    }

    @Test
    void toSubscriptionEntity_should_map_all_fields() {
        UUID subscriptionId = randomUUID();
        UUID hearingEventId = randomUUID();
        when(clockService.nowOffsetUTC()).thenReturn(fixedNow);

        HearingEventSubscriptionEntity result = hearingEventPayloadMapper.toSubscriptionEntity(subscriptionId, hearingEventId);

        assertThat(result.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(result.getHearingEventId()).isEqualTo(hearingEventId);
        assertThat(result.getCreatedAt()).isEqualTo(fixedNow);
        assertThat(result.getId()).isNull();
    }
}