package uk.gov.hmcts.cp.subscription.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hearing_event_subscriptions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HearingEventSubscriptionEntity {

    @Id
    private UUID hearingEventId;

    private UUID subscriptionId;

    private OffsetDateTime createdAt;
}
