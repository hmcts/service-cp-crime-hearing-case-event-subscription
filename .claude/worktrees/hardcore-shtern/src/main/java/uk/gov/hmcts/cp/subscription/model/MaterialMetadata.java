package uk.gov.hmcts.cp.subscription.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialMetadata {
    private UUID materialId;
    private String alfrescoAssetId;
    private String fileName;
    private String mimeType;
    private String materialAddedDate;
    private String externalLink;
}
