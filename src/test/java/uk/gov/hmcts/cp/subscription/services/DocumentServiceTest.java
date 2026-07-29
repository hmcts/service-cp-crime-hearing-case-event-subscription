package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.filters.UUIDService;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;
import uk.gov.hmcts.cp.subscription.clients.MaterialDocumentClient;
import uk.gov.hmcts.cp.subscription.config.MaterialClientProperties;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.mappers.DocumentMapper;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;
import uk.gov.hmcts.cp.subscription.model.MaterialMetadata;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    ClockService clockService;
    @Mock
    UUIDService uuidService;
    @Mock
    DocumentMapper documentMapper;
    @Mock
    DocumentMappingRepository documentMappingRepository;
    @Mock
    EventTypeRepository eventTypeRepository;
    @Mock
    MaterialClientProperties materialClientProperties;
    @Mock
    MaterialClient materialClient;
    @Mock
    MaterialDocumentClient materialDocumentClient;

    @InjectMocks
    DocumentService documentService;

    OffsetDateTime now = OffsetDateTime.of(2026, 3, 1, 12, 30, 30, 500, ZoneOffset.UTC);
    String materialUrl = "http://material-servce";
    UUID materialId = UUID.fromString("43bb8246-2bdf-487c-a0d3-160bbfd37777");
    UUID documentId = UUID.fromString("2e48f8f1-c057-48f7-92e5-c4183480ea3e");
    EventTypeEntity eventTypeEntity = EventTypeEntity.builder()
            .id(1L)
            .eventName("PRISON_COURT_REGISTER_GENERATED")
            .build();
    DocumentMappingEntity documentMappingEntity = DocumentMappingEntity.builder()
            .documentId(documentId)
            .materialId(materialId)
            .eventTypeId(eventTypeEntity).build();

    @Test
    void save_document_should_save_entity() {
        when(clockService.nowOffsetUTC()).thenReturn(now);
        when(uuidService.random()).thenReturn(documentId);
        when(eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED")).thenReturn(Optional.of(eventTypeEntity));
        when(documentMapper.mapToNewEntity(documentId, materialId, eventTypeEntity, now)).thenReturn(documentMappingEntity);
        when(documentMappingRepository.save(documentMappingEntity)).thenReturn(documentMappingEntity);
        documentService.saveDocumentMapping(materialId, "PRISON_COURT_REGISTER_GENERATED");
        verify(documentMappingRepository).save(documentMappingEntity);
    }

    @Test
    void save_document_should_throw_not_found_when_event_type_is_unknown() {
        when(eventTypeRepository.findByEventName("UNKNOWN_EVENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.saveDocumentMapping(materialId, "UNKNOWN_EVENT"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("UNKNOWN_EVENT");
                });
    }

    @Test
    void get_document_content_should_return_response() {
        when(documentMappingRepository.findByDocumentId(documentId)).thenReturn(Optional.of(documentMappingEntity));
        when(materialClient.getMetadata(materialId)).thenReturn(createMetadata());
        when(materialClient.getContentUrl(materialId)).thenReturn(materialUrl);
        ResponseEntity<byte[]> document = ResponseEntity.ok("pdfcontent".getBytes());
        when(materialDocumentClient.getMaterialDocument(URI.create(materialUrl))).thenReturn(document);

        DocumentContent documentContent = documentService.getDocumentContent(documentId);

        assertThat(documentContent.getBody()).isEqualTo("pdfcontent".getBytes());
        assertThat(documentContent.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(documentContent.getFileName()).isEqualTo("file.pdf");
    }

    @Test
    void get_event_type_for_document_should_return_event_name_when_found() {
        when(documentMappingRepository.findById(documentId)).thenReturn(Optional.of(documentMappingEntity));

        assertThat(documentService.getEventTypeForDocument(documentId)).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
    }

    @Test
    void get_event_type_for_document_should_throw_404_when_document_purged_or_not_found() {
        when(documentMappingRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getEventTypeForDocument(documentId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).contains(documentId.toString());
                });
    }

    private MaterialMetadata createMetadata() {
        return MaterialMetadata.builder()
                .fileName("file.pdf")
                .build();
    }
}
