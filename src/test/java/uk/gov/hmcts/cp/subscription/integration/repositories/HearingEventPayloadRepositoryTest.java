package uk.gov.hmcts.cp.subscription.integration.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.entities.HearingEventPayloadEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class HearingEventPayloadRepositoryTest extends IntegrationTestBase {

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void existsByEventId_returns_true_for_persisted_entity() {
        UUID eventId = randomUUID();
        saveHearingEventPayload(eventId);

        assertThat(hearingEventPayloadRepository.existsByEventId(eventId)).isTrue();
    }

    @Test
    void existsByEventId_returns_false_for_unknown_id() {
        assertThat(hearingEventPayloadRepository.existsByEventId(randomUUID())).isFalse();
    }

    @Test
    void saved_entity_has_generated_hearingEventId() {
        UUID eventId = randomUUID();
        HearingEventPayloadEntity saved = saveHearingEventPayload(eventId);

        assertThat(saved.getHearingEventId()).isNotNull();
        assertThat(saved.getEventId()).isEqualTo(eventId);
    }

    private HearingEventPayloadEntity saveHearingEventPayload(UUID eventId) {
        Long eventTypeId = eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED")
                .orElseThrow().getId();
        return hearingEventPayloadRepository.save(HearingEventPayloadEntity.builder()
                .eventId(eventId)
                .eventTypeId(eventTypeId)
                .rawPayload(EventPayload.builder().eventType("PRISON_COURT_REGISTER_GENERATED").build())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }
}