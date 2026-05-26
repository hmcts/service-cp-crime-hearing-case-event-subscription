package uk.gov.hmcts.cp.subscription.integration.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.Optional;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentMappingRepositoryTest extends IntegrationTestBase {

    private static final UUID MATERIAL_ID = randomUUID();

    @BeforeEach
    void beforeEach() {
        clearDocumentMappingTable();
    }

    @Transactional
    @Test
    void findByMaterialId_should_save_and_return_document() {
        DocumentMappingEntity saved = insertDocument(MATERIAL_ID);

        Optional<DocumentMappingEntity> found = documentMappingRepository.findByMaterialId(MATERIAL_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getDocumentId()).isEqualTo(saved.getDocumentId());
        assertThat(found.get().getMaterialId()).isEqualTo(MATERIAL_ID);
        assertThat(found.get().getEventType()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }
}
