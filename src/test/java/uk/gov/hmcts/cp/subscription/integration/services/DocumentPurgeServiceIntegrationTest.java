package uk.gov.hmcts.cp.subscription.integration.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;
import uk.gov.hmcts.cp.subscription.services.DocumentPurgeService;
import org.springframework.data.domain.Limit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

class DocumentPurgeServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    DocumentPurgeService documentPurgeService;

    @MockitoSpyBean
    DocumentMappingRepository documentMappingRepositorySpy;

    @BeforeEach
    void beforeEach() {
        clearAllTables();
        // small batch so the batched loop iterates against the real DB without needing thousands of rows
        ReflectionTestUtils.setField(documentPurgeService, "batchSize", 2);
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

    @Test
    void purge_should_delete_across_multiple_batches_when_more_than_batch_size_are_old() {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        // 3 old with batchSize=2 forces the loop to fetch+delete more than once; if it didn't
        // re-fetch, the third would survive
        DocumentMappingEntity old1 = insertDocumentWithCreatedAt(now.minusDays(40));
        DocumentMappingEntity old2 = insertDocumentWithCreatedAt(now.minusDays(41));
        DocumentMappingEntity old3 = insertDocumentWithCreatedAt(now.minusDays(42));

        documentPurgeService.purgeOldDocuments();

        assertThat(documentMappingRepository.findByDocumentId(old1.getDocumentId())).isEmpty();
        assertThat(documentMappingRepository.findByDocumentId(old2.getDocumentId())).isEmpty();
        assertThat(documentMappingRepository.findByDocumentId(old3.getDocumentId())).isEmpty();
    }

    @Test
    void earlier_committed_batches_survive_when_a_later_iteration_fails() {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        DocumentMappingEntity first = insertDocumentWithCreatedAt(now.minusDays(40));
        DocumentMappingEntity second = insertDocumentWithCreatedAt(now.minusDays(41));
        insertDocumentWithCreatedAt(now.minusDays(42));
        insertDocumentWithCreatedAt(now.minusDays(43));
        // first fetch returns a real batch (deleted for real, committed in its own transaction);
        // the second iteration's fetch blows up. deleteAllInBatch is left unstubbed (real).
        doReturn(List.of(first, second))
                .doThrow(new RuntimeException("simulated failure on the second iteration"))
                .when(documentMappingRepositorySpy).findByCreatedAtBefore(any(OffsetDateTime.class), any(Limit.class));

        assertThatThrownBy(() -> documentPurgeService.purgeOldDocuments())
                .isInstanceOf(RuntimeException.class);

        // the first batch's deletion committed before the failure, so 2 of the 4 remain
        assertThat(documentMappingRepositorySpy.count()).isEqualTo(2);
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
