package uk.gov.hmcts.cp.subscription.integration.stubs;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

public final class MaterialStub {

    private static final String MATERIAL_METADATA_RESPONSE_PATH = "wiremock/material-client/files/material-response.json";
    private static final String MATERIAL_CONTENT_RESPONSE_PATH = "wiremock/material-client/files/material-content-url.txt";
    private static final String MATERIAL_PDF_PATH = "wiremock/material-client/files/material-content.pdf";
    private static final String MATERIAL_URI = "/material-query-api/query/api/rest/material/material/";
    private static final String METADATA = "/metadata";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CONTENT_TYPE = "Content-Type";

    private MaterialStub() {
    }

    public static void stubMaterialMetadata(UUID materialId) throws IOException {
        String materialPath = MATERIAL_URI + materialId;
        String metadataBody = new ClassPathResource(MATERIAL_METADATA_RESPONSE_PATH).getContentAsString(StandardCharsets.UTF_8);
        stubFor(get(urlPathMatching(".*" + materialPath + METADATA))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(metadataBody)));
    }

    public static void stubMaterialContent(UUID materialId) throws IOException {
        String materialPath = MATERIAL_URI + materialId;
        String contentBody = new ClassPathResource(MATERIAL_CONTENT_RESPONSE_PATH).getContentAsString(StandardCharsets.UTF_8);
        stubFor(get(urlPathMatching(".*" + materialPath + "/content"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE, "text/uri-list;charset=UTF-8")
                        .withBody(contentBody)
                        .withTransformers("response-template")));
    }

    public static void stubMaterialBinary(UUID materialId) throws IOException {
        String materialPath = MATERIAL_URI + materialId;
        byte[] pdfBody = new ClassPathResource(MATERIAL_PDF_PATH).getContentAsByteArray();
        stubFor(get(urlPathMatching(".*" + materialPath + "/binary"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE, "application/pdf")
                        .withHeader("Content-Disposition", "inline; filename=\"material-content.pdf\"")
                        .withBody(pdfBody)));
    }

    public static void stubMaterialMetadataNoContent(UUID materialId) {
        String materialPath = MATERIAL_URI + materialId;
        stubFor(get(urlPathMatching(".*" + materialPath + METADATA))
                .willReturn(aResponse().withStatus(204)));
    }
}
