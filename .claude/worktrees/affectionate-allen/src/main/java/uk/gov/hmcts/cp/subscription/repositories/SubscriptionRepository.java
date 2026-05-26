package uk.gov.hmcts.cp.subscription.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<ClientSubscriptionEntity, UUID> {

    Optional<ClientSubscriptionEntity> findByIdAndClientId(UUID id, UUID clientId);

    Optional<ClientSubscriptionEntity> findFirstByClientId(UUID clientId);

    @Query(value = "SELECT * FROM client_subscription WHERE :eventType = ANY(event_types)", nativeQuery = true)
    List<ClientSubscriptionEntity> findByEventType(@Param("eventType") String eventType);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM client_subscription WHERE id = :subscriptionId AND client_id = :clientId AND :eventType = ANY(event_types))", nativeQuery = true)
    boolean existsByIdAndEventType(@Param("subscriptionId") UUID subscriptionId, @Param("clientId") UUID clientId, @Param("eventType") String eventType);
}
