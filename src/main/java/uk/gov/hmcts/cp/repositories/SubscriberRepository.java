package uk.gov.hmcts.cp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.entities.SubscriberEntity;

import java.util.UUID;

@Repository
public interface SubscriberRepository extends JpaRepository<SubscriberEntity, UUID> {
}
