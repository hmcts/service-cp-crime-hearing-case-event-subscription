package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.openapi.model.HearingEventResponse;
import uk.gov.hmcts.cp.subscription.entities.HearingEventPayloadEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
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

    @Test
    void toResponse_should_map_all_fields() {
        UUID payloadFk = randomUUID();
        EventPayload rawPayload = EventPayload.builder().eventType("PRISON_COURT_REGISTER_GENERATED").build();
        Map<String, Object> payloadMap = Map.of("eventType", "PRISON_COURT_REGISTER_GENERATED");

        HearingEventSubscriptionEntity subscription = HearingEventSubscriptionEntity.builder()
                .hearingEventId(payloadFk)
                .build();
        HearingEventPayloadEntity payload = HearingEventPayloadEntity.builder()
                .hearingEventId(payloadFk)
                .rawPayload(rawPayload)
                .createdAt(fixedNow)
                .build();

        HearingEventResponse result = hearingEventPayloadMapper.toResponse(subscription, payload, payloadMap);

        assertThat(result.getHearingEventId()).isEqualTo(payloadFk);
        assertThat(result.getEventType()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
        assertThat(result.getCreatedAt()).isEqualTo(fixedNow.toInstant());
        assertThat(result.getPayload()).isEqualTo(payloadMap);
    }

    @Test
    void toResponse_should_strip_internal_fields_from_payload() {
        UUID payloadFk = randomUUID();
        UUID eventId = randomUUID();
        UUID materialId = randomUUID();
        EventPayload rawPayload = EventPayload.builder().eventType("PRISON_COURT_REGISTER_GENERATED").build();

        Map<String, Object> payloadMapWithInternalFields = new HashMap<>();
        payloadMapWithInternalFields.put("eventType", "PRISON_COURT_REGISTER_GENERATED");
        payloadMapWithInternalFields.put("eventId", eventId.toString());
        payloadMapWithInternalFields.put("materialId", materialId.toString());
        payloadMapWithInternalFields.put("hearingId", "some-hearing-id");

        HearingEventSubscriptionEntity subscription = HearingEventSubscriptionEntity.builder()
                .hearingEventId(payloadFk)
                .build();
        HearingEventPayloadEntity payload = HearingEventPayloadEntity.builder()
                .hearingEventId(payloadFk)
                .rawPayload(rawPayload)
                .createdAt(fixedNow)
                .build();

        HearingEventResponse result = hearingEventPayloadMapper.toResponse(subscription, payload, payloadMapWithInternalFields);

        assertThat(result.getPayload()).doesNotContainKey("materialId");
        assertThat(result.getPayload()).doesNotContainKey("eventId");
        assertThat(result.getPayload()).containsKey("eventType");
        assertThat(result.getPayload()).containsKey("hearingId");
    }
}