package uk.gov.hmcts.cp.subscription.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.subscription.entities.ClientHmacEntity;

@Repository
public interface ClientHmacRepository extends JpaRepository<ClientHmacEntity, Long> {
}
