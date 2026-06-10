# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo: service-cp-crime-hearing-results-document-subscription

Spring Boot service that manages webhook subscriptions for Crime Hearing Results document events — receives inbound PCR events from Service Bus, fetches document metadata from a material service, stores subscriber state in PostgreSQL, and delivers signed callbacks to registered subscriber URLs.

**Pattern**: DB-backed event subscription service
**Spring Boot version**: 4.0.6 (current — target 4.0.6+ per upgrade cycle)
**Implements**: `api-cp-crime-hearing-results-document-subscription`

## Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| PostgreSQL 18 | Primary database (port 5432) | Subscriber entities, event tracking, HMAC keys, document mappings |
| Azure Service Bus | Emulator (ports 5672, 5300) | Inbound PCR event queue + outbound per-subscriber notification topics |
| Flyway | Auto-runs on boot | Schema migrations in `src/main/resources/db/migration/` |
| Azure Key Vault | Optional (`AZURE_VAULT_ENABLED`) | HMAC signing secrets; stub/debug/Azure implementations switchable |

## Running Tests

Integration and E2E tests require PostgreSQL on `localhost:5432` (database `appdb`). `PostgresInitialise` will fail fast with a clear message if it's not reachable.

```bash
# Start only PostgreSQL (sufficient for unit + integration tests)
docker compose -f docker/docker-compose.yml up -d db

# Start full stack including Service Bus emulator (required for E2E tests)
docker compose -f docker/docker-compose.yml up -d

# Run all tests (unit + integration)
./gradlew test

# Run a single test class
./gradlew test --tests 'uk.gov.hmcts.cp.subscription.integration.controllers.SubscriptionCreateControllerIntegrationTest'

# Run a single test method
./gradlew test --tests 'uk.gov.hmcts.cp.subscription.integration.controllers.SubscriptionCreateControllerIntegrationTest.shouldCreateSubscription'

# API tests (separate subproject in apiTest/, requires full Docker stack)
cd apiTest && ./gradlew test
```

### Test tiers

| Tier | Location | Requires |
|---|---|---|
| Unit | `src/test/java/.../unit/` | Nothing |
| Integration | `src/test/java/.../integration/` (excl. `e2e/`) | PostgreSQL on `localhost:5432` |
| E2E | `src/test/java/.../integration/e2e/` | PostgreSQL + Service Bus emulator |
| API tests | `apiTest/` subproject | Full docker-compose stack |

`IntegrationTestBase` sets `service-bus.auto-start-processors=false` — integration tests (non-E2E) do not need the Service Bus emulator running.

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
      HearingEventPayloadEntity                 Stores raw inbound event payloads
      HearingEventSubscriptionEntity            Links hearing events to subscriber subscriptions
    mappers/ (MapStruct)
      ClientEntityMapper / ClientEventEntityMapper / ClientHmacMapper
      ClientSubscriptionMapper / DocumentMapper / EventTypeMapper
      HearingEventMapper / NotificationMapper
    repositories/
      ClientRepository / ClientEventRepository / ClientHmacRepository
      DocumentMappingRepository / EventTypeRepository
      HearingEventPayloadRepository / HearingEventSubscriptionRepository
    services/
      SubscriptionService                       Client registration and management
      NotificationService                       Inbound event processing
      CallbackDeliveryService                   Outbound callback dispatch
      HearingEventService                       Query hearing events by subscription
      EventTypeService / DocumentService / MaterialService / SubscriptionValidationService
      EventPayloadValidator                     Validates inbound event payload structure
      JsonMapper                                ObjectMapper wrapper for payload serialisation
      ClockService                              Testable time source (wraps Instant.now)
    util/
      JwtTokenParser                            Extracts azp claim from Bearer token
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
- **Controller → Manager → Service → Client/Repository**: this service has an extra `Manager` layer — `NotificationManager` sits between controllers and services. No business logic in controllers.
- **Service Bus toggle**: `AZURE_SERVICE_BUS_AUTO_START_PROCESSORS=false` makes all event processing synchronous — useful for integration tests without a running emulator.
- **HMAC signing**: `HmacSigningService` signs callback payloads; keys stored per client via `ClientHmacEntity`. Key Vault implementation switches via `AZURE_VAULT_ENABLED`.
- **Immutability**: builders not setters; `final` fields (PMD enforces in main code).
- **ClockService over Instant.now()**: always inject `ClockService` rather than calling `Instant.now()` directly — allows deterministic time in tests.

## Debugging

| Symptom | Cause / Fix |
|---|---|
| Service won't start | PostgreSQL running? (`docker compose -f docker/docker-compose.yml up -d db`) Port 4550 free? |
| Integration tests fail immediately | `PostgresInitialise` can't reach `localhost:5432` — start postgres first |
| Material timeout on notification | Tune `MATERIAL_CLIENT_URL`; check material service reachable; awaitility polling interval |
| Callbacks not delivered | Check `AZURE_SERVICE_BUS_AUTO_START_PROCESSORS`; inspect Service Bus connection string in logs |
| Missing client ID in queries | Check `ClientIdResolutionFilter` is registered; JWT present and parseable; MDC not cleared early |
| PMD build failures | Check `.github/pmd-ruleset.xml` — common: cyclomatic complexity, method length, naming |

## Observability — KQL Queries

See [`support/README.md`](support/README.md) for the full query index, `run-query.sh` usage, and the workflow for syncing `dashboard-kql/` and `alerts-kql/` to their Terraform repos.

## Repo-Specific Notes

- `auto-merge-dependabot.yml` present — auto-merges Dependabot PRs on minor/patch bumps.
- `ci-build-publish.yml` present alongside standard `ci-draft.yml` / `ci-released.yml`.
- Docker compose is at `docker/docker-compose.yml` — uses PostgreSQL + Service Bus emulator; no WireMock container (WireMock is started in-process by tests via `wiremock-spring-boot`).
- All entities use `@GeneratedValue(strategy = GenerationType.UUID)` — UUID primary keys throughout.
- The `apiTest/` directory is a standalone Gradle subproject with its own `build.gradle` and docker-compose for black-box API testing.