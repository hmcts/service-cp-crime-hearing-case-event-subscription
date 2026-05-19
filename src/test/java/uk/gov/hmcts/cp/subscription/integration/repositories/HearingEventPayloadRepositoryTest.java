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
    void existsByHearingEventId_returns_true_for_persisted_entity() {
        UUID hearingEventId = randomUUID();
        saveHearingEventPayload(hearingEventId);

        assertThat(hearingEventPayloadRepository.existsByHearingEventId(hearingEventId)).isTrue();
    }

    @Test
    void existsByHearingEventId_returns_false_for_unknown_id() {
        assertThat(hearingEventPayloadRepository.existsByHearingEventId(randomUUID())).isFalse();
    }

    private void saveHearingEventPayload(UUID hearingEventId) {
        Long eventTypeId = eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED")
                .orElseThrow().getId();
        hearingEventPayloadRepository.save(HearingEventPayloadEntity.builder()
                .hearingEventId(hearingEventId)
                .eventTypeId(eventTypeId)
                .rawPayload(EventPayload.builder().eventType("PRISON_COURT_REGISTER_GENERATED").build())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }
}
