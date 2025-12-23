package uk.gov.hmcts.cp.subscription.clients;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import uk.gov.hmcts.cp.subscription.clients.model.MaterialResponse;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@EnableWireMock({@ConfigureWireMock(name = "material-service", baseUrlProperties = "material-service.url")})
class MaterialServiceIntegrationTest {

    @Autowired
    private MaterialService materialService;

    @Test
    void should_return_material_by_id() {

        UUID materialId = UUID.fromString("6c198796-08bb-4803-b456-fa0c29ca6021");
        MaterialResponse response = materialService.getByMaterialId(materialId);

        assertThat(response).isNotNull();
        assertThat(response.getMaterialId()).isEqualTo(materialId);
        assertThat(response.getAlfrescoAssetId()).isEqualTo(UUID.fromString("82257b1b-571d-432e-8871-b0c5b4bd18b1"));
        assertThat(response.getMimeType()).isEqualTo("application/pdf");
        assertThat(response.getFileName()).isEqualTo("PrisonCourtRegister_20251219083322.pdf");
        assertThat(response.getMaterialAddedDate()).isEqualTo("2025-12-19T08:33:29.866Z");
    }

    @Test
    void should_throw_not_found_when_material_does_not_exist() {
        UUID materialId = UUID.fromString("6c198796-08bb-4803-b456-fa0c29ca6022");
        assertThatThrownBy(() -> materialService.getByMaterialId(materialId))
                .isInstanceOf(feign.FeignException.NotFound.class);
    }
}
