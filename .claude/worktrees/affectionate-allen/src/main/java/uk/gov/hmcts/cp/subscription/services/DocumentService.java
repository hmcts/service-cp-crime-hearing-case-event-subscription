package uk.gov.hmcts.cp.subscription.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;
import uk.gov.hmcts.cp.subscription.clients.MaterialDocumentClient;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.mappers.DocumentMapper;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;
import uk.gov.hmcts.cp.subscription.model.MaterialMetadata;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;

import java.net.URI;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentMappingRepository documentMappingRepository;
    private final ClockService clockService;
    private final DocumentMapper documentMapper;
    private final MaterialClient materialClient;
    private final MaterialDocumentClient materialDocumentClient;

    @Transactional
    public UUID saveDocumentMapping(final UUID materialId, final String eventType) {
        final DocumentMappingEntity entity = documentMapper.mapToNewEntity(clockService, materialId, eventType);
        log.info("saving DocumentMapping materialId:{} to documentId:{}", materialId, entity.getDocumentId());
        documentMappingRepository.save(entity);
        return entity.getDocumentId();
    }

    @Transactional
    public UUID getDocumentIdForMaterialId(final UUID materialId) {
        return documentMappingRepository.findByMaterialId(materialId).get().getDocumentId();
    }

    @Transactional
    public String getEventTypeForDocument(final UUID documentId) {
        return documentMappingRepository.findById(documentId).get().getEventType();
    }

    public DocumentContent getDocumentContent(final UUID documentId) {
        final DocumentMappingEntity documentMapping = documentMappingRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));
        final UUID materialId = documentMapping.getMaterialId();
        log.info("getDocumentContent documentId:{} resolved to materialId:{}", documentId, materialId);
        final MaterialMetadata metadata = materialClient.getMetadata(materialId);
        final String contentUrl = materialClient.getContentUrl(materialId);
        log.info("fetching document bytes for materialId:{}", materialId);
        final ResponseEntity<byte[]> document = materialDocumentClient.getMaterialDocument(URI.create(contentUrl));
        return DocumentContent.builder()
                .body(document.getBody())
                .contentType(MediaType.APPLICATION_PDF)
                .fileName(metadata.getFileName())
                .build();
    }
}
