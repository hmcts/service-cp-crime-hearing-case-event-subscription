package uk.gov.hmcts.cp.subscription.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Daily purge of document mappings older than the configured retention period.
 *
 * <p>Subscribers do not retrieve progression/nows documents older than a few hours, and inbound
 * messages are dead-lettered after two weeks, so a one-month retention is safe. Once a mapping is
 * purged, document retrieval returns 404 (see DocumentService.getEventTypeForDocument).
 *
 * <p>Deletion runs in bounded batches so a large backlog cannot load the whole table into memory or
 * hold a single long transaction — each batch is fetched, logged and deleted in its own transaction.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentPurgeService {

    private final DocumentMappingRepository documentMappingRepository;
    private final ClockService clockService;

    @Value("${document.purge.retention-days}")
    private int retentionDays;

    @Value("${document.purge.batch-size}")
    private int batchSize;

    @Scheduled(cron = "${document.purge.cron}")
    public void purgeOldDocuments() {
        final OffsetDateTime cutoff = clockService.nowOffsetUTC().minusDays(retentionDays);
        log.info("DocumentPurge removing documents older than {} days (cutoff {})", retentionDays, cutoff);
        while (true) {
            final List<DocumentMappingEntity> batch = documentMappingRepository.findByCreatedAtBefore(cutoff, Limit.of(batchSize));
            if (batch.isEmpty()) {
                break;
            }
            batch.forEach(d -> log.info("DocumentPurge deleting documentId:{} createdAt:{}", d.getDocumentId(), d.getCreatedAt()));
            documentMappingRepository.deleteAllInBatch(batch);
            log.info("DocumentPurge deleted batch of {} documents older than {} days", batch.size(), retentionDays);
        }
    }
}
