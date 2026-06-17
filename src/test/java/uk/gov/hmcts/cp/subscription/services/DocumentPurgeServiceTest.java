package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentPurgeServiceTest {

    @Mock
    DocumentMappingRepository documentMappingRepository;
    @Mock
    ClockService clockService;

    @InjectMocks
    DocumentPurgeService documentPurgeService;

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 6, 16, 2, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void purge_should_delete_documents_older_than_configured_retention() {
        ReflectionTestUtils.setField(documentPurgeService, "retentionDays", 30);
        when(clockService.nowOffsetUTC()).thenReturn(NOW);
        List<DocumentMappingEntity> oldDocuments = List.of(
                DocumentMappingEntity.builder().documentId(UUID.randomUUID()).materialId(UUID.randomUUID()).createdAt(NOW.minusMonths(2)).build(),
                DocumentMappingEntity.builder().documentId(UUID.randomUUID()).materialId(UUID.randomUUID()).createdAt(NOW.minusMonths(3)).build());
        when(documentMappingRepository.findByCreatedAtBefore(NOW.minusDays(30))).thenReturn(oldDocuments);

        documentPurgeService.purgeOldDocuments();

        verify(documentMappingRepository).deleteAll(oldDocuments);
    }
}
