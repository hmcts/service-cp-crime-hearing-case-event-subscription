package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void purge_should_delete_documents_older_than_retention_batch_by_batch_until_empty() {
        ReflectionTestUtils.setField(documentPurgeService, "retentionDays", 30);
        when(clockService.nowOffsetUTC()).thenReturn(NOW);
        final OffsetDateTime cutoff = NOW.minusDays(30);
        final List<DocumentMappingEntity> firstBatch = documents(3);
        final List<DocumentMappingEntity> secondBatch = documents(1);
        when(documentMappingRepository.findByCreatedAtBefore(eq(cutoff), any(Limit.class)))
                .thenReturn(firstBatch)
                .thenReturn(secondBatch)
                .thenReturn(List.of());

        documentPurgeService.purgeOldDocuments();

        verify(documentMappingRepository, times(3)).findByCreatedAtBefore(eq(cutoff), any(Limit.class));
        verify(documentMappingRepository).deleteAllInBatch(firstBatch);
        verify(documentMappingRepository).deleteAllInBatch(secondBatch);
    }

    @Test
    void purge_should_not_delete_when_nothing_is_older_than_retention() {
        ReflectionTestUtils.setField(documentPurgeService, "retentionDays", 30);
        when(clockService.nowOffsetUTC()).thenReturn(NOW);
        when(documentMappingRepository.findByCreatedAtBefore(eq(NOW.minusDays(30)), any(Limit.class)))
                .thenReturn(List.of());

        documentPurgeService.purgeOldDocuments();

        verify(documentMappingRepository, times(1)).findByCreatedAtBefore(eq(NOW.minusDays(30)), any(Limit.class));
        verify(documentMappingRepository, never()).deleteAllInBatch(any());
    }

    private List<DocumentMappingEntity> documents(final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> DocumentMappingEntity.builder()
                        .documentId(UUID.randomUUID())
                        .materialId(UUID.randomUUID())
                        .createdAt(NOW.minusMonths(2))
                        .build())
                .toList();
    }
}
