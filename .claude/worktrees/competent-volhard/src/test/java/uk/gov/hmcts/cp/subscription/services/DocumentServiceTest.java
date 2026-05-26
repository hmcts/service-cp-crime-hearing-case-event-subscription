package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;
import uk.gov.hmcts.cp.subscription.clients.MaterialDocumentClient;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.mappers.DocumentMapper;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;
import uk.gov.hmcts.cp.subscription.model.MaterialMetadata;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    ClockService clockService;
    @Mock
    DocumentMapper documentMapper;
    @Mock
    DocumentMappingRepository documentMappingRepository;
    @Mock
    MaterialClient materialClient;
    @Mock
    MaterialDocumentClient materialDocumentClient;

    @InjectMocks
    DocumentService documentService;

    String materialUrl = "http://material-servce";
    UUID materialId = UUID.fromString("43bb8246-2bdf-487c-a0d3-160bbfd37777");
    UUID documentId = UUID.fromString("2e48f8f1-c057-48f7-92e5-c4183480ea3e");
    DocumentMappingEntity documentMappingEntity = DocumentMappingEntity.builder()
            .documentId(documentId)
            .materialId(materialId)
            .eventType("PRISON_COURT_REGISTER_GENERATED").build();

    @Test
    void save_document_should_save_entity() {
        when(documentMapper.mapToNewEntity(clockService, materialId, "PRISON_COURT_REGISTER_GENERATED")).thenReturn(documentMappingEntity);
        when(documentMappingRepository.save(documentMappingEntity)).thenReturn(documentMappingEntity);
        documentService.saveDocumentMapping(materialId, "PRISON_COURT_REGISTER_GENERATED");
        verify(documentMappingRepository).save(documentMappingEntity);
    }

    @Test
    void get_document_id_should_return_id() {
        when(documentMappingRepository.findByMaterialId(materialId)).thenReturn(Optional.of(documentMappingEntity));
        UUID response = documentService.getDocumentIdForMaterialId(materialId);
        assertThat(response).isEqualTo(documentId);
    }

    @Test
    void get_document_content_should_return_response() {
        when(documentMappingRepository.findById(documentId)).thenReturn(Optional.of(documentMappingEntity));
        when(materialClient.getMetadata(materialId)).thenReturn(createMetadata());
        when(materialClient.getContentUrl(materialId)).thenReturn(materialUrl);
        ResponseEntity<byte[]> document = ResponseEntity.ok("pdfcontent".getBytes());
        when(materialDocumentClient.getMaterialDocument(URI.create(materialUrl))).thenReturn(document);

        DocumentContent documentContent = documentService.getDocumentContent(documentId);

        assertThat(documentContent.getBody()).isEqualTo("pdfcontent".getBytes());
        assertThat(documentContent.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(documentContent.getFileName()).isEqualTo("file.pdf");
    }

    private MaterialMetadata createMetadata() {
        return MaterialMetadata.builder()
                .fileName("file.pdf")
                .build();
    }
}
