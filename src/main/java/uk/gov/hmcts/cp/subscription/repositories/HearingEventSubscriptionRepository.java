package uk.gov.hmcts.cp.subscription.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.subscription.entities.HearingEventSubscriptionEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HearingEventSubscriptionRepository extends JpaRepository<HearingEventSubscriptionEntity, UUID> {

    boolean existsBySubscriptionIdAndHearingEventId(UUID subscriptionId, UUID hearingEventId);

    Optional<HearingEventSubscriptionEntity> findByHearingEventIdAndSubscriptionId(UUID hearingEventId, UUID subscriptionId);
}
