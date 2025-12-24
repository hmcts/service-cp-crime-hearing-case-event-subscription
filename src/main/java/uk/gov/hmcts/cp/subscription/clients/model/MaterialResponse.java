package uk.gov.hmcts.cp.subscription.clients.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MaterialResponse {

    @NotNull
    private UUID materialId;

    private UUID alfrescoAssetId;

    private String fileName;

    private String mimeType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime materialAddedDate;
}

