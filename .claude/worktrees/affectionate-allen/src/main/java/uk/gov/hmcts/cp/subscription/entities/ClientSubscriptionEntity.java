package uk.gov.hmcts.cp.subscription.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "client_subscription")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClientSubscriptionEntity {

    @Id
    private UUID id;

    @Column(name = "client_id")
    private UUID clientId;

    private String notificationEndpoint;
    private List<String> eventTypes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Temp added till we switch to Client / ClientHmac
    private String hmacKeyId;
}