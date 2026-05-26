package uk.gov.hmcts.cp.subscription.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.subscription.entities.ClientEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientEventRepository extends JpaRepository<ClientEventEntity, Long> {

    /**
     * Check whether a given subscription (identified by both subscriptionId and clientId)
     * has an entry in client_events for the requested event type name.
     */
    @Query("SELECT COUNT(ce) FROM ClientEventEntity ce " +
            "JOIN ClientEntity c ON ce.subscriptionId = c.subscriptionId " +
            "JOIN EventTypeEntity et ON ce.eventTypeId = et.id " +
            "WHERE c.subscriptionId = :subscriptionId " +
            "AND et.eventName = :eventName")
    long countByClientSubscriptionAndEventName(@Param("subscriptionId") UUID subscriptionId,
                                               @Param("eventName") String eventName);

    @Query("SELECT et.eventName FROM ClientEventEntity ce " +
            "JOIN EventTypeEntity et ON ce.eventTypeId = et.id " +
            "JOIN ClientEntity c ON ce.subscriptionId = c.subscriptionId " +
            "WHERE c.id = :clientId AND c.subscriptionId = :subscriptionId " +
            "ORDER BY et.eventName ASC")
    List<String> findEventNamesForClient(@Param("clientId") UUID clientId, @Param("subscriptionId") UUID subscriptionId);

    void deleteBySubscriptionId(UUID subscriptionId);
}
