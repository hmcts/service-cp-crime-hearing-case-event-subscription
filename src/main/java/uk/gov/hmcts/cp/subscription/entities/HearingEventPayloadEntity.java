package uk.gov.hmcts.cp.subscription.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.converters.EventPayloadConverter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hearing_event_payload")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HearingEventPayloadEntity {

    @Id
    private UUID hearingEventId;

    private Long eventTypeId;

    @Convert(converter = EventPayloadConverter.class)
    @Column(columnDefinition = "jsonb")
    private EventPayload rawPayload;

    private OffsetDateTime createdAt;
}
