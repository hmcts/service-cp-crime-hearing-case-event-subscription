package uk.gov.hmcts.cp.subscription.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;

import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<ClientSubscriptionEntity, UUID> {
}
