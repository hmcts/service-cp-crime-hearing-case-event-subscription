package uk.gov.hmcts.cp.subscription.integration.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.entities.HearingEventPayloadEntity;
import uk.gov.hmcts.cp.subscription.entities.HearingEventSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HearingEventSubscriptionRepositoryTest extends IntegrationTestBase {

    private UUID hearingEventId;
    private UUID subscriptionId;

    @BeforeEach
    void beforeEach() {
        clearAllTables();
        hearingEventId = randomUUID();
        subscriptionId = insertSubscription("https://callback", java.util.List.of("PRISON_COURT_REGISTER_GENERATED"));
        saveHearingEventPayload(hearingEventId);
    }

    @Test
    void existsBySubscriptionIdAndHearingEventId_returns_true_for_existing_pair() {
        saveSubscription(randomUUID(), subscriptionId, hearingEventId);

        assertThat(hearingEventSubscriptionRepository
                .existsBySubscriptionIdAndHearingEventId(subscriptionId, hearingEventId)).isTrue();
    }

    @Test
    void existsBySubscriptionIdAndHearingEventId_returns_false_for_unknown_pair() {
        assertThat(hearingEventSubscriptionRepository
                .existsBySubscriptionIdAndHearingEventId(randomUUID(), randomUUID())).isFalse();
    }

    @Test
    void findByIdAndSubscriptionId_returns_entity_on_match() {
        UUID id = randomUUID();
        saveSubscription(id, subscriptionId, hearingEventId);

        Optional<HearingEventSubscriptionEntity> result =
                hearingEventSubscriptionRepository.findByIdAndSubscriptionId(id, subscriptionId);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(result.get().getHearingEventId()).isEqualTo(hearingEventId);
    }

    @Test
    void findByIdAndSubscriptionId_returns_empty_on_subscriptionId_mismatch() {
        UUID id = randomUUID();
        saveSubscription(id, subscriptionId, hearingEventId);

        Optional<HearingEventSubscriptionEntity> result =
                hearingEventSubscriptionRepository.findByIdAndSubscriptionId(id, randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void duplicate_insert_should_violate_unique_constraint() {
        saveSubscription(randomUUID(), subscriptionId, hearingEventId);

        assertThatThrownBy(() -> saveSubscription(randomUUID(), subscriptionId, hearingEventId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void saveHearingEventPayload(UUID id) {
        Long eventTypeId = eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED")
                .orElseThrow().getId();
        hearingEventPayloadRepository.save(HearingEventPayloadEntity.builder()
                .hearingEventId(id)
                .eventTypeId(eventTypeId)
                .rawPayload(EventPayload.builder().eventType("PRISON_COURT_REGISTER_GENERATED").build())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private void saveSubscription(UUID id, UUID subId, UUID hearingEvtId) {
        hearingEventSubscriptionRepository.saveAndFlush(HearingEventSubscriptionEntity.builder()
                .id(id)
                .subscriptionId(subId)
                .hearingEventId(hearingEvtId)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }
}
