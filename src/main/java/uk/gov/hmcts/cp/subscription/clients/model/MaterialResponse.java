package uk.gov.hmcts.cp.subscription.clients.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MaterialResponse {

    @NotNull
    @JsonProperty("materialId")
    private UUID materialId;

    @JsonProperty("alfrescoAssetId")
    private UUID alfrescoAssetId;

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("mimeType")
    private String mimeType;

    @JsonProperty("materialAddedDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime materialAddedDate;
}

