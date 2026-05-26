package uk.gov.hmcts.cp.subscription.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;

import java.util.Optional;

@Repository
public interface EventTypeRepository extends JpaRepository<EventTypeEntity, Long> {

    Optional<EventTypeEntity> findByEventName(String eventName);

    boolean existsByEventName(String eventName);
}
