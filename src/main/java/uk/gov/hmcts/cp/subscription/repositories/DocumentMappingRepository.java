package uk.gov.hmcts.cp.subscription.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentMappingRepository extends JpaRepository<DocumentMappingEntity, UUID> {

    Optional<DocumentMappingEntity> findByDocumentId(UUID documentId);

    List<DocumentMappingEntity> findByCreatedAtBefore(OffsetDateTime cutoff);
}

