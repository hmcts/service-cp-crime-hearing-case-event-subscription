package uk.gov.hmcts.cp.subscription.integration.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentMappingRepositoryTest extends IntegrationTestBase {

    private static final UUID MATERIAL_ID = randomUUID();

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Transactional
    @Test
    void findByMaterialId_should_save_and_return_document() {
        DocumentMappingEntity saved = insertDocument(MATERIAL_ID);

        Optional<DocumentMappingEntity> found = documentMappingRepository.findByDocumentId(saved.getDocumentId());

        assertThat(found).isPresent();
        assertThat(found.get().getDocumentId()).isEqualTo(saved.getDocumentId());
        assertThat(found.get().getMaterialId()).isEqualTo(MATERIAL_ID);
        assertThat(found.get().getEventTypeId().getEventName()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Transactional
    @Test
    void findByCreatedAtBefore_should_return_only_documents_older_than_cutoff() {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        DocumentMappingEntity old = insertDocumentWithCreatedAt(now.minusMonths(2));
        insertDocumentWithCreatedAt(now.minusDays(1));

        List<DocumentMappingEntity> found = documentMappingRepository.findByCreatedAtBefore(now.minusMonths(1));

        assertThat(found).extracting(DocumentMappingEntity::getDocumentId).containsExactly(old.getDocumentId());
    }

    @Transactional
    @Test
    void purging_found_documents_should_remove_old_and_keep_recent() {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        DocumentMappingEntity old = insertDocumentWithCreatedAt(now.minusMonths(2));
        DocumentMappingEntity recent = insertDocumentWithCreatedAt(now.minusDays(1));

        documentMappingRepository.deleteAll(documentMappingRepository.findByCreatedAtBefore(now.minusMonths(1)));

        assertThat(documentMappingRepository.findByDocumentId(old.getDocumentId())).isEmpty();
        assertThat(documentMappingRepository.findByDocumentId(recent.getDocumentId())).isPresent();
    }

    private DocumentMappingEntity insertDocumentWithCreatedAt(final OffsetDateTime createdAt) {
        EventTypeEntity eventType = eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED").orElseThrow();
        return documentMappingRepository.save(DocumentMappingEntity.builder()
                .documentId(randomUUID())
                .materialId(randomUUID())
                .eventTypeId(eventType)
                .createdAt(createdAt)
                .build());
    }
}
