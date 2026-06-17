package uk.gov.hmcts.cp.subscription.integration.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;
import uk.gov.hmcts.cp.subscription.services.DocumentPurgeService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentPurgeServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    DocumentPurgeService documentPurgeService;

    @BeforeEach
    void beforeEach() {
        clearAllTables();
    }

    @Test
    void purge_should_delete_documents_older_than_retention_and_keep_recent() {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        DocumentMappingEntity old = insertDocumentWithCreatedAt(now.minusDays(40));
        DocumentMappingEntity recent = insertDocumentWithCreatedAt(now.minusDays(1));

        documentPurgeService.purgeOldDocuments();

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
