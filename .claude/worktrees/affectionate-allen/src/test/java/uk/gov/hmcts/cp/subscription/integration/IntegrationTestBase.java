package uk.gov.hmcts.cp.subscription.integration;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
import uk.gov.hmcts.cp.subscription.entities.ClientSubscriptionEntity;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.integration.config.TestContainersInitialise;
import uk.gov.hmcts.cp.subscription.integration.helpers.JwtHelper;
import uk.gov.hmcts.cp.subscription.repositories.ClientEventRepository;
import uk.gov.hmcts.cp.subscription.repositories.ClientHmacRepository;
import uk.gov.hmcts.cp.subscription.repositories.ClientRepository;
import uk.gov.hmcts.cp.subscription.repositories.DocumentMappingRepository;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;
import uk.gov.hmcts.cp.subscription.repositories.SubscriptionRepository;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;


@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@AutoConfigureMockMvc
@Slf4j
@TestPropertySource(properties = {
        "vault.enabled=false"
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
    protected SubscriptionRepository subscriptionRepository;

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

    protected void clearClientSubscriptionTable() {
        log.info("Clearing client_subscription table");
        subscriptionRepository.deleteAll();
    }

    protected void clearDocumentMappingTable() {
        log.info("Clearing document_mapping table");
        documentMappingRepository.deleteAll();
    }

    protected void clearAllTables() {
        log.info("Clearing all tables");
        clientEventRepository.deleteAll();
        clientRepository.deleteAll();
        subscriptionRepository.deleteAll();
        documentMappingRepository.deleteAll();
    }

    protected ClientSubscriptionEntity insertSubscription(String notificationUri, List<String> entityEventTypes) {
        return insertSubscription(TEST_CLIENT_ID, entityEventTypes, notificationUri);
    }

    protected ClientSubscriptionEntity insertSubscription(UUID clientId, List<String> entityEventTypes, String notificationUri) {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        ClientSubscriptionEntity subscription = ClientSubscriptionEntity.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .eventTypes(entityEventTypes)
                .notificationEndpoint(notificationUri)
                .createdAt(now)
                .updatedAt(now)
                .build();
        subscriptionRepository.save(subscription);

        clientRepository.save(ClientEntity.builder()
                .id(clientId)
                .subscriptionId(subscription.getId())
                .callbackUrl(notificationUri)
                .createdAt(now)
                .updatedAt(now)
                .build());

        entityEventTypes.forEach(eventType ->
                eventTypeRepository.findByEventName(eventType).ifPresent(eventTypeEntity ->
                        clientEventRepository.save(ClientEventEntity.builder()
                                .subscriptionId(subscription.getId())
                                .eventTypeId(eventTypeEntity.getId())
                                .build())));

        return subscription;
    }

    protected ClientEntity insertClient(UUID clientId) {
        OffsetDateTime now = clockService.now().atOffset(ZoneOffset.UTC);
        ClientEntity client = ClientEntity.builder()
                .id(clientId)
                .subscriptionId(UUID.randomUUID())
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
        DocumentMappingEntity document = DocumentMappingEntity.builder()
                .documentId(UUID.randomUUID())
                .materialId(materialId)
                .eventType(eventType)
                .createdAt(now)
                .build();
        return documentMappingRepository.save(document);
    }

    protected String loadPayload(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
