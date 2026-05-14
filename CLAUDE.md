## Repo: service-cp-crime-hearing-results-document-subscription

Spring Boot service that manages webhook subscriptions for Crime Hearing Results document events — receives inbound PCR events from Service Bus, fetches document metadata from a material service, stores subscriber state in PostgreSQL, and delivers signed callbacks to registered subscriber URLs.

**Pattern**: DB-backed event subscription service
**Spring Boot version**: 4.0.6 (current — target 4.0.6+ per upgrade cycle)
**Implements**: `api-cp-crime-hearing-results-document-subscription`

## Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| PostgreSQL 15 | Primary database (port 5433) | Subscriber entities, event tracking, HMAC keys, document mappings |
| Azure Service Bus | Emulator (ports 5672, 5300) | Inbound PCR event queue + outbound per-subscriber notification topics |
| Flyway | Auto-runs on boot | Schema migrations in `src/main/resources/db/migration/` |
| Azure Key Vault | Optional (`AZURE_VAULT_ENABLED`) | HMAC signing secrets; stub/debug/Azure implementations switchable |

## Event Processing Pipeline

```
PCR Inbound Event (from Progression/HearingNows)
    ↓
NotificationController.createNotification()
    ↓ [AZURE_SERVICE_BUS_AUTO_START_PROCESSORS=true → queue to inbound topic]
    ↓ [else → synchronous]
NotificationManager.processPcrNotification()
    ↓
NotificationService.processInboundEvent()
    ├→ MaterialService.waitForMaterialMetadata()  [awaitility polling]
    └→ DocumentService.saveDocumentMapping()
    ↓
CallbackDeliveryService.submitOutboundPcrEvents()
    └→ [Service Bus enabled → queue to outbound topic per subscriber]
       [else → CallbackClient.sendToSubscriber()]
```

## Source Structure

```
uk.gov.hmcts.cp/
  Application.java                              @SpringBootApplication
  PostStartup                                   @Service — initialises subscriptions on startup
  filters/
    ClientIdResolutionFilter                    Resolves client ID from JWT; stores in MDC (CLIENT_ID key)
    TracingFilter                               Reads/generates X-Correlation-Id; propagates via MDC
    UUIDService                                 Generates UUIDs for correlation
  hmac/
    HmacManager                                 Orchestrates HMAC key lifecycle
    EncodingService / HmacKeyService / HmacSigningService   HMAC-SHA256 signing for callbacks
  servicebus/
    ServiceBusAdminAzureImpl / EmulatorImpl     Service Bus admin (topic/subscription creation)
    ServiceBusClientFactory / ClientService     Client lifecycle management
    ServiceBusHandlers / ProcessorService       Message receive and dispatch
    ServiceBusRetryService                      Retry with configurable backoff delays
  subscription/
    clients/
      CallbackClient                            HTTP POST to subscriber webhook URLs
      MaterialClient / MaterialDocumentClient   Fetch document metadata from material service
    controllers/
      SubscriptionController                    POST /client-subscriptions
      NotificationController                    Inbound PCR notification endpoint
      MockCallbackController                    Test webhook receiver (non-production)
    entities/ (JPA, UUID PKs)
      ClientEntity / ClientEventEntity / ClientHmacEntity
      DocumentMappingEntity / EventTypeEntity
    mappers/ (MapStruct)
      ClientEntityMapper / ClientEventEntityMapper / ClientHmacMapper
      ClientSubscriptionMapper / DocumentMapper / EventTypeMapper
    repositories/
      ClientRepository / ClientEventRepository / ClientHmacRepository
      DocumentMappingRepository / EventTypeRepository
    services/
      SubscriptionService                       Client registration and management
      NotificationService                       Inbound event processing
      CallbackDeliveryService                   Outbound callback dispatch
      EventTypeService / DocumentService / MaterialService / SubscriptionValidationService
  vault/
    SecretStoreServiceAzureImpl                 Production: reads secrets from Azure Key Vault
    SecretStoreServiceStubImpl                  Local dev: returns hardcoded values
    SecretStoreServiceDebug                     Debug: logs secret lookups
```

## Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `4550` |
| `DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/appdb` |
| `DATASOURCE_USERNAME` | DB username | `postgres` |
| `DATASOURCE_PASSWORD` | DB password | `postgres` |
| `HIKARI_MAX_POOL_SIZE` | Connection pool size | `8` |
| `MATERIAL_CLIENT_URL` | Material metadata service URL | `http://localhost:8081` |
| `DOCUMENT_SERVICE_URL` | Document store service URL | `http://localhost:8082` |
| `AZURE_SERVICE_BUS_URI` | Service Bus AMQP URI | `sb://localhost` (emulator) |
| `AZURE_SERVICE_BUS_ADMIN_URI` | Service Bus admin URI | `sb://localhost:5300` |
| `AZURE_SERVICE_BUS_AUTO_START_PROCESSORS` | Enable async Service Bus processing | `true` |
| `SERVICE_BUS_RETRY_SECONDS` | Comma-separated retry delays (ms) | `0,1000,2000,10000,30000,60000` |
| `SERVICE_BUS_MAX_TRIES` | Max delivery attempts | `5` |
| `SUBSCRIPTION_OAUTH_ENABLED` | Enforce OAuth on subscription endpoints | `true` |
| `AZURE_VAULT_ENABLED` | Use Azure Key Vault for HMAC secrets | `false` |
| `AZURE_VAULT_URI` | Key Vault URI (when enabled) | — |
| `AZURE_CLIENT_ID` | Azure managed identity client ID | `00000000-0000-0000-0000-000000000000` |
| `CJSCPPUID` | User UUID header on all backend calls | `00000000-0000-0000-0000-000000000000` |
| `ENVIRONMENT_NAME` | Environment label in logs | `UNKNOWN` |
| `rpe.AppInsightsInstrumentationKey` | Azure Application Insights key | `00000000-0000-0000-0000-000000000000` |

## Repo-Specific Architecture Rules

- **Client ID mandatory in every query**: `ClientIdResolutionFilter` stores the resolved client UUID in MDC as `CLIENT_ID`. Every repository call must include it — `UUID.fromString(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID))`.
- **Controller → Manager → Service → Client/Repository**: no layer skipping. Controllers delegate to `NotificationManager` or services; no business logic in controllers.
- **MapStruct mappers**: never edit generated `*Impl` classes. All entity ↔ DTO mapping goes through typed `@Mapper` interfaces.
- **Flyway migrations**: naming `V<VERSION>__<description>.sql` in `src/main/resources/db/migration/`. Auto-runs on boot.
- **Service Bus toggle**: `AZURE_SERVICE_BUS_AUTO_START_PROCESSORS=false` makes all event processing synchronous — useful for integration tests without a running emulator.
- **HMAC signing**: `HmacSigningService` signs callback payloads; keys stored per client via `ClientHmacEntity`. Key Vault implementation switches via `AZURE_VAULT_ENABLED`.
- **Immutability**: builders not setters; `final` fields (PMD enforces in main code).

## Debugging

| Symptom | Cause / Fix |
|---|---|
| Service won't start | PostgreSQL running on correct port? `DATASOURCE_URL` set? Port 4550 free? |
| Material timeout on notification | Tune `MATERIAL_CLIENT_URL`; check material service reachable; awaitility polling interval |
| Callbacks not delivered | Check `AZURE_SERVICE_BUS_AUTO_START_PROCESSORS`; inspect Service Bus connection string in logs |
| Missing client ID in queries | Check `ClientIdResolutionFilter` is registered; JWT present and parseable; MDC not cleared early |
| PMD build failures | Check `.github/pmd-ruleset.xml` — common: cyclomatic complexity, method length, naming |
| Test failures | Run with `-i`; check MDC setup in filters; Testcontainers requires Docker running |

## Repo-Specific Notes

- `auto-merge-dependabot.yml` present — auto-merges Dependabot PRs on minor/patch bumps.
- `ci-build-publish.yml` present alongside standard `ci-draft.yml` / `ci-released.yml`.
- No `WireMock` in docker-compose — uses PostgreSQL + Service Bus emulator instead; API tests require Docker.
- All entities use `@GeneratedValue(strategy = GenerationType.UUID)` — UUID primary keys throughout.
