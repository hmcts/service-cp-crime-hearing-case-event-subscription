package uk.gov.hmcts.cp.subscription.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentMappingRepository extends JpaRepository<DocumentMappingEntity, UUID> {

    Optional<DocumentMappingEntity> findByDocumentId(UUID documentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DocumentMappingEntity d where d.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}

