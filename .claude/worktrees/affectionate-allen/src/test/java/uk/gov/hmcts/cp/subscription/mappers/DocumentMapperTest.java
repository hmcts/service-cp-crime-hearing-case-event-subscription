package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DocumentMapperTest {

    @Mock
    ClockService clockService;

    DocumentMapper documentMapper = new DocumentMapperImpl();

    @Test
    void mapper_should_create_entity() {
        UUID materialId = UUID.fromString("245c0d96-ecfe-45e6-a2a2-d51cac9aa643");
        DocumentMappingEntity result = documentMapper.mapToNewEntity(clockService, materialId, "PRISON_COURT_REGISTER_GENERATED");
        assertThat(result.getDocumentId()).isNotNull();
        assertThat(result.getMaterialId()).isEqualTo(materialId);
        assertThat(result.getEventType()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
        assertThat(result.getCreatedAt()).isNull();
    }
}