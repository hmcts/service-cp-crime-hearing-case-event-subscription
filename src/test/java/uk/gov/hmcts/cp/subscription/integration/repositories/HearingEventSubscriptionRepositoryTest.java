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
        subscriptionId = insertSubscription("https://callback", java.util.List.of("PRISON_COURT_REGISTER_GENERATED"));
        hearingEventId = saveHearingEventPayload();
    }

    @Test
    void existsBySubscriptionIdAndHearingEventId_returns_true_for_existing_pair() {
        saveSubscription(subscriptionId, hearingEventId);

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
        HearingEventSubscriptionEntity saved = saveSubscription(subscriptionId, hearingEventId);

        Optional<HearingEventSubscriptionEntity> result =
                hearingEventSubscriptionRepository.findByIdAndSubscriptionId(saved.getId(), subscriptionId);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
        assertThat(result.get().getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(result.get().getHearingEventId()).isEqualTo(hearingEventId);
    }

    @Test
    void findByIdAndSubscriptionId_returns_empty_on_subscriptionId_mismatch() {
        HearingEventSubscriptionEntity saved = saveSubscription(subscriptionId, hearingEventId);

        Optional<HearingEventSubscriptionEntity> result =
                hearingEventSubscriptionRepository.findByIdAndSubscriptionId(saved.getId(), randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void duplicate_insert_should_violate_unique_constraint() {
        saveSubscription(subscriptionId, hearingEventId);

        assertThatThrownBy(() -> saveSubscription(subscriptionId, hearingEventId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID saveHearingEventPayload() {
        Long eventTypeId = eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED")
                .orElseThrow().getId();
        HearingEventPayloadEntity saved = hearingEventPayloadRepository.save(HearingEventPayloadEntity.builder()
                .eventId(UUID.randomUUID())
                .eventTypeId(eventTypeId)
                .rawPayload(EventPayload.builder().eventType("PRISON_COURT_REGISTER_GENERATED").build())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        return saved.getHearingEventId();
    }

    private HearingEventSubscriptionEntity saveSubscription(UUID subId, UUID hearingEvtId) {
        return hearingEventSubscriptionRepository.saveAndFlush(HearingEventSubscriptionEntity.builder()
                .subscriptionId(subId)
                .hearingEventId(hearingEvtId)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }
}
