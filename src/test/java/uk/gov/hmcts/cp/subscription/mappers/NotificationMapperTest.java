package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.openapi.model.EventNotificationPayload;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.openapi.model.EventPayloadDefendant;
import uk.gov.hmcts.cp.openapi.model.EventPayloadDefendantCasesInner;
import uk.gov.hmcts.cp.openapi.model.EventPayloadDefendantCustodyEstablishmentDetails;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NotificationMapperTest {
    @Spy
    JsonMapper jsonMapper;

    @InjectMocks
    NotificationMapper notificationMapper;

    UUID defendentId = UUID.fromString("f791c82b-dd68-468f-9066-a31008f8f229");
    UUID documentId = UUID.fromString("24f36bc0-ecf7-4fa2-985a-afada1cdee98");
    UUID hearingId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Instant now = Instant.now();
    EventPayloadDefendantCasesInner caseOne = EventPayloadDefendantCasesInner.builder().urn("http://localhost").build();
    EventPayloadDefendantCustodyEstablishmentDetails custodyEstablishment = EventPayloadDefendantCustodyEstablishmentDetails.builder()
            .emailAddress("prison@example.com")
            .build();
    EventPayloadDefendant defendant = EventPayloadDefendant.builder()
            .cases(List.of(caseOne))
            .masterDefendantId(defendentId)
            .name("John Doe")
            .dateOfBirth(LocalDate.of(2000, 1, 1))
            .custodyEstablishmentDetails(custodyEstablishment)
            .build();

    @Test
    void mapper_should_return_populated_payload() {
        EventPayload eventPayload = EventPayload.builder()
                .eventType("PRISON_COURT_REGISTER_GENERATED")
                .hearingId(hearingId)
                .defendant(defendant)
                .timestamp(now)
                .build();

        EventNotificationPayload response = notificationMapper.mapToPayload(documentId, eventPayload);

        assertThat(response.getEventType()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
        assertThat(response.getHearingId()).isEqualTo(hearingId);
        assertThat(response.getCases()).hasSize(1);
        assertThat(response.getCases().get(0).getUrn()).isEqualTo("http://localhost");
        assertThat(response.getMasterDefendantId()).isEqualTo(defendentId);
        assertThat(response.getDocumentId()).isEqualTo(documentId);
        assertThat(response.getDocumentGeneratedTimestamp()).isEqualTo(now);
        assertThat(response.getPrisonEmailAddress()).isEqualTo("prison@example.com");
    }

    @Test
    void mapper_should_return_populated_wrapper() {
        EventNotificationPayload payload = EventNotificationPayload.builder().build();
        EventNotificationPayloadWrapper response = notificationMapper.mapToWrapper(payload, "kid", "signature");

        assertThat(response.getPayload()).isEqualTo(payload);
        assertThat(response.getKeyId()).isEqualTo("kid");
        assertThat(response.getSignature()).isEqualTo("signature");
    }
}