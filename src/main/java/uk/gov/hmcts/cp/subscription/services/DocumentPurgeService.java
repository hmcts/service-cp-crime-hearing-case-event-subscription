package uk.gov.hmcts.cp.subscription.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;

import java.time.OffsetDateTime;

/**
 * Daily purge of document mappings older than the configured retention period.
 *
 * <p>Subscribers do not retrieve progression/nows documents older than a few hours, and inbound
 * messages are dead-lettered after two weeks, so a one-month retention is safe. Once a mapping is
 * purged, document retrieval returns 404 (see DocumentService.getEventTypeForDocument).
 *
 * <p>To effectively disable purging, set {@code document.purge.retention-days} to a large value
 * (e.g. 9999) so the cutoff never matches any documents.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentPurgeService {

    private final DocumentMappingRepository documentMappingRepository;
    private final ClockService clockService;

    @Value("${document.purge.retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "${document.purge.cron:0 0 2 * * *}")
    @Transactional
    public void purgeOldDocuments() {
        final OffsetDateTime cutoff = clockService.nowOffsetUTC().minusDays(retentionDays);
        final int deletedCount = documentMappingRepository.deleteByCreatedAtBefore(cutoff);
        log.info("DocumentPurge removed {} documents older than {}", deletedCount, cutoff);
    }
}
