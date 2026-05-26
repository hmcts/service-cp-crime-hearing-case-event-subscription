package uk.gov.hmcts.cp.subscription.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_mapping")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMappingEntity {

    @Id
    private UUID documentId;
    private UUID materialId;
    private String eventType;
    private OffsetDateTime createdAt;
}

