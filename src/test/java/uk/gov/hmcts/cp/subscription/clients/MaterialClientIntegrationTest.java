package uk.gov.hmcts.cp.subscription.clients;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import uk.gov.hmcts.cp.subscription.clients.model.MaterialResponse;
import uk.gov.hmcts.cp.subscription.config.TestContainersInitialise;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@EnableWireMock({@ConfigureWireMock(name = "material-client", baseUrlProperties = "material-client.url")})
class MaterialClientIntegrationTest {

    @Autowired
    private MaterialClient materialClient;

    @Test
    void should_return_material_by_id() {

        final UUID materialId = UUID.fromString("6c198796-08bb-4803-b456-fa0c29ca6021");
        MaterialResponse response = materialClient.getMetadataById(materialId);

        assertThat(response).satisfies(resp -> {
            assertThat(resp.getMaterialId()).isEqualTo(materialId);
            assertThat(resp.getAlfrescoAssetId()).isEqualTo(UUID.fromString("82257b1b-571d-432e-8871-b0c5b4bd18b1"));
            assertThat(resp.getMimeType()).isEqualTo("application/pdf");
            assertThat(resp.getFileName()).isEqualTo("PrisonCourtRegister_20251219083322.pdf");
        });
    }

    @Test
    void should_throw_not_found_when_material_does_not_exist() {
        UUID materialId = UUID.fromString("6c198796-08bb-4803-b456-fa0c29ca6022");
        assertThatThrownBy(() -> materialClient.getMetadataById(materialId))
                .isInstanceOf(feign.FeignException.NotFound.class);
    }

    @Test
    void should_return_content_by_id() {

        final UUID materialId = UUID.fromString("7c198796-08bb-4803-b456-fa0c29ca6021");
        byte[] response = materialClient.getContentById(materialId);

        assertThat(response);
        String pdfAsString = new String(response, StandardCharsets.ISO_8859_1);
        assertThat(pdfAsString).startsWith("%PDF");
        assertThat(pdfAsString).contains("%%EOF");
        assertThat(response).hasSizeGreaterThan(500);
    }

    @Test
    void should_throw_not_found_when_content_does_not_exist() {
        UUID materialId = UUID.fromString("7c198796-08bb-4803-b456-fa0c29ca6023");
        assertThatThrownBy(() -> materialClient.getContentById(materialId))
                .isInstanceOf(feign.FeignException.NotFound.class);
    }
}
