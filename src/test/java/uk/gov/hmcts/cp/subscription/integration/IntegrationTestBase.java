package uk.gov.hmcts.cp.subscription.integration;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.NotificationEndpoint;
import uk.gov.hmcts.cp.subscription.entities.ClientEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientEventEntity;
import uk.gov.hmcts.cp.subscription.entities.ClientHmacEntity;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.integration.config.PostgresInitialise;
import uk.gov.hmcts.cp.subscription.integration.helpers.JwtHelper;
import uk.gov.hmcts.cp.subscription.repositories.ClientEventRepository;
import uk.gov.hmcts.cp.subscription.repositories.ClientHmacRepository;
import uk.gov.hmcts.cp.subscription.repositories.ClientRepository;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;


@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresInitialise.class)
@TestPropertySource(properties = {
        "vault.enabled=false",
        "service-bus.auto-start-processors=false",
        "cp.audit.enabled=false"
})
public abstract class IntegrationTestBase {

    protected static final UUID MATERIAL_ID_TIMEOUT = UUID.fromString("11111111-1111-1111-1111-111111111112");
    protected static final String NOTIFICATIONS_URI = "/notifications";
    protected static final String CLIENT_SUBSCRIPTIONS_URI = "/client-subscriptions";
    protected static final String CALLBACK_URI = "/callback/notify";
    protected static final UUID TEST_CLIENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    protected static final String AUTHORIZATION_HEADER_VALUE = JwtHelper.bearerTokenWithAzp(TEST_CLIENT_ID.toString());

    @Resource
    protected MockMvc mockMvc;

    @Autowired
    protected ClientHmacRepository clientHmacRepository;

    @Autowired
    protected DocumentMappingRepository documentMappingRepository;

    @Autowired
    protected EventTypeRepository eventTypeRepository;

    @Autowired
    protected ClientRepository clientRepository;

    @Autowired
    protected ClientEventRepository clientEventRepository;

    @Autowired
    protected ClockService clockService;

    protected NotificationEndpoint notificationEndpoint = NotificationEndpoint.builder()
            .callbackUrl("https://my-callback-url")
            .build();
    protected ClientSubscriptionRequest request = ClientSubscriptionRequest.builder()
            .notificationEndpoint(notificationEndpoint)
            .eventTypes(List.of("PRISON_COURT_REGISTER_GENERATED"))
            .build();

    @AfterEach
    protected void tearDown() {
        clearAllTables();
    }

    protected void clearAllTables() {
        log.info("Clearing all tables");
        clientHmacRepository.deleteAll();
        clientEventRepository.deleteAll();
        clientRepository.deleteAll();
        documentMappingRepository.deleteAll();
    }

    protected UUID insertSubscription(String notificationUri, List<String> entityEventTypes) {
        return insertSubscription(TEST_CLIENT_ID, entityEventTypes, notificationUri);
    }

    protected UUID insertSubscription(UUID clientId, List<String> entityEventTypes, String notificationUri) {
        return insertSubscription(clientId, entityEventTypes, notificationUri, "kid-v1-keyid");
    }

    protected UUID insertSubscription(UUID clientId, List<String> entityEventTypes, String notificationUri, String keyId) {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        UUID subscriptionId = UUID.randomUUID();

        clientRepository.save(ClientEntity.builder()
                .clientId(clientId)
                .subscriptionId(subscriptionId)
                .callbackUrl(notificationUri)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // client_events.subscription_id FK references client.subscription_id — client row must be persisted first.
        entityEventTypes.forEach(eventType ->
                eventTypeRepository.findByEventName(eventType).ifPresent(eventTypeEntity ->
                        clientEventRepository.save(ClientEventEntity.builder()
                                .subscriptionId(subscriptionId)
                                .eventTypeId(eventTypeEntity.getId())
                                .build())));

        clientHmacRepository.save(ClientHmacEntity.builder()
                .subscriptionId(subscriptionId)
                .keyId(keyId)
                .build());

        return subscriptionId;
    }

    protected ClientEntity insertClient(UUID clientId, UUID subscriptionId) {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        ClientEntity client = ClientEntity.builder()
                .clientId(clientId)
                .subscriptionId(subscriptionId)
                .callbackUrl("https://callback")
                .createdAt(now)
                .updatedAt(now)
                .build();
        return clientRepository.save(client);
    }

    protected DocumentMappingEntity insertDocument(UUID materialId) {
        return insertDocument(materialId, "PRISON_COURT_REGISTER_GENERATED");
    }

    protected DocumentMappingEntity insertDocument(UUID materialId, String eventType) {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        EventTypeEntity eventTypeEntity = eventTypeRepository.findByEventName(eventType).get();
        DocumentMappingEntity document = DocumentMappingEntity.builder()
                .documentId(UUID.randomUUID())
                .materialId(materialId)
                .eventTypeId(eventTypeEntity)
                .createdAt(now)
                .build();
        return documentMappingRepository.save(document);
    }

    protected String loadPayload(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
