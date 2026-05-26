# AGENTS.md - AI Coding Agent Guide

Guide for AI coding agents working on the **Court Hearing Cases Event Subscription API** (`service-cp-crime-hearing-case-event-subscription`).

## Overview

This is a **Spring Boot 4.0** event subscription microservice for criminal court cases. It manages client subscriptions for notifications, processes events from Progression/HearingNows services, retrieves documents from Material service, and delivers notifications to subscribers via callback URLs.

## Architecture & Data Flow

### Event Processing Pipeline

```
PCR Inbound Event (from Progression)
    ↓
NotificationController.createNotification()
    ↓ [if ServiceBus enabled → queued to PCR_INBOUND_TOPIC]
    ↓ [else → synchronous]
NotificationManager.processPcrNotification()
    ↓
NotificationService.processInboundEvent()
    ├→ MaterialService.waitForMaterialMetadata() [polls Material service with retry]
    └→ DocumentService.saveDocumentMapping()
    ↓
CallbackDeliveryService.submitOutboundPcrEvents()
    ├→ Query subscriptions by event type
    └→ [if ServiceBus enabled → queue to PCR_OUTBOUND_TOPIC per subscriber]
        [else → CallbackService.sendToSubscriber()]
```

### Key Components

- **Controllers**: `NotificationController`, `SubscriptionController` - HTTP endpoints (OpenAPI generated)
- **Managers**: `NotificationManager` - orchestrates service interactions (business logic)
- **Services**: 
  - `NotificationService`, `SubscriptionService`, `DocumentService` - domain logic
  - `MaterialService` - polls Material API with exponential backoff
  - `CallbackDeliveryService` - routes notifications to subscribers
  - `CallbackService` - HTTP delivery to callback URLs
- **Entities**: `ClientSubscriptionEntity`, `ClientEventEntity`, `DocumentMappingEntity`, `EventTypeEntity`
- **Clients**: `MaterialClient`, `CallbackClient` - external HTTP integrations
- **Repositories**: Spring Data JPA extending `JpaRepository<Entity, UUID>`

### Critical Design Decisions

1. **Async vs Sync**: Service Bus (Azure) integration toggles async processing. When disabled, same-thread processing. See `application.yaml` `service-bus.enabled`.
2. **MaterialService polling**: Uses `awaitility` library with configurable retry intervals/timeouts (not exponential backoff). See `src/main/java/uk/gov/hmcts/cp/subscription/config/AppProperties.java`.
3. **Manager pattern**: Orchestration logic in `NotificationManager` keeps controllers thin and services focused.
4. **Multi-tenant subscriptions**: Each client has multiple subscriptions; each subscription filters event types. Enforce client ID in all queries via MDC (`ClientIdResolutionFilter.MDC_CLIENT_ID`).

## Build & Test Workflows

### Build Commands

```bash
# Clean build with tests
./gradlew clean build

# Skip tests
./gradlew clean build -x test

# Run unit/integration tests
./gradlew test

# Run API tests (Docker required)
cd apiTest && ./build-and-run-apitest.sh

# Run code quality checks
./gradlew pmdMain  # PMD static analysis (uses .github/pmd-ruleset.xml)
./gradlew jacocoTestReport  # Code coverage (JaCoCo HTML report in build/reports/jacoco)
```

### Local Development

```bash
# Start PostgreSQL (required)
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=appdb postgres:15

# Set env vars (or use direnv: cp .envrc.example .envrc && direnv allow)
export DATASOURCE_URL=jdbc:postgresql://localhost:5432/appdb
export DATASOURCE_PASSWORD=postgres
export MATERIAL_CLIENT_URL=http://localhost:8081
export DOCUMENT_SERVICE_URL=http://localhost:8082

# Build and run
./gradlew bootRun  # Starts on http://localhost:4550

# Health check
curl http://localhost:4550/actuator/health
```

### Gradle Structure

- `build.gradle` - declares plugins (Spring Boot 4.0.3, OpenAPI Generator 7.20.0, JaCoCo, PMD) and external dependencies
- `gradle/dependencies/` - shared dependency versions (java-core.gradle, spring-core.gradle, spring-db.gradle, etc.)
- `gradle/github/` - build tasks (test.gradle enforces Mockito agent, PMD rules, JaCoCo reporting)
- **Database migrations**: Flyway auto-runs from `src/main/resources/db/migration/` (ensure SQL naming follows Flyway convention)

## Critical Dependencies & Integrations

### External Services (HTTP Clients)

| Service | Config | Use Case | Retry Strategy |
|---------|--------|----------|-----------------|
| Material API | `MATERIAL_CLIENT_URL` (default: `http://localhost:8081`) | Fetch material metadata when PCR event arrives | `MaterialService.waitForMaterialMetadata()` uses awaitility (interval: `MATERIAL_CLIENT_INTERVAL_MSECS`, timeout: `MATERIAL_CLIENT_TIMEOUT_MSECS`) |
| Document Service | `DOCUMENT_SERVICE_URL` (default: `http://localhost:8082`) | Retrieve document content for subscribers | Configured via `DocumentService` |
| Callback URLs | `ClientSubscriptionEntity.notificationEndpoint` | Deliver notifications to subscribers | Configurable retry in `callback-client.retry` (interval/timeout milliseconds) |

### Azure Service Bus (Optional)

- **Enable**: Set `AZURE_SERVICE_BUS_ENABLED=true`
- **Config**: `AZURE_SERVICEBUS_URI` (connection string), `AZURE_SERVICE_BUS_ADMIN_URI`
- **Topics**: `PCR_INBOUND_TOPIC` (inbound), `PCR_OUTBOUND_TOPIC` (outbound notifications)
- **Queuing**: When enabled, inbound PCR events and outbound notifications are queued separately
- **Retries**: Configurable via `SERVICE_BUS_RETRY_SECONDS` (comma-separated list, e.g., "0,1000,2000,10000") and `SERVICE_BUS_MAX_TRIES`

### Database

- **Engine**: PostgreSQL 15 (required)
- **Migrations**: Flyway handles schema via `src/main/resources/db/migration/`
- **Entities**: All use UUID primary keys (`@GeneratedValue(strategy = GenerationType.UUID)`)
- **Connection**: JDBC URL in `application.yaml` (overridable via `DATASOURCE_URL`)

### Libraries & Frameworks

- **Spring Boot** 4.0.3, **Spring Data JPA** - ORM, repository pattern
- **Lombok** 1.18.44 - annotation processing for getters, builders
- **MapStruct** 1.6.3 - compile-time DTO mapping (see `mappers/` directory)
- **Awaitility** 4.3.0 - polling with timeout (used in `MaterialService`)
- **OpenAPI Generator** 7.20.0 - generates API interfaces from spec (see `uk.gov.hmcts.cp.openapi.api.*`)
- **Azure Service Bus** 7.17.17 - async message broker
- **Logback** + **Logstash encoder** - JSON logging for log aggregation

## Project Patterns & Conventions

### 1. **OpenAPI-First Design**

- API specs are external (likely in `api-cp-crime-hearing-case-event-subscription` dependency)
- Controllers **implement** generated API interfaces (e.g., `NotificationController implements InternalApi, NotificationApi`)
- DTO models are auto-generated (e.g., `EventPayload`, `ClientSubscription`)
- **Never manually modify generated code**; regenerate via `./gradlew openApiGenerate`

### 2. **Service Layering**

- **Controllers**: Thin, delegate to managers/services, format HTTP responses only
- **Managers**: Orchestrate multi-service workflows (e.g., `NotificationManager`)
- **Services**: Single responsibility, domain logic, can call repositories/clients
- **Clients**: Stateless HTTP/external API calls with error handling
- **Repositories**: Spring Data JPA, custom queries use `@Query` annotation

### 3. **Mappers (MapStruct)**

- **Location**: `src/main/java/uk/gov/hmcts/cp/subscription/mappers/`
- **Pattern**: Entity ↔ DTO conversions (e.g., `SubscriptionMapper.mapEntityToResponse()`)
- **Generated**: MapStruct generates `Impl` classes; don't edit them
- **Custom logic**: Add `@Mapping` for field transformations, `@AfterMapping` for post-processing

### 4. **Multi-Tenancy & Security**

- **Client ID resolution**: `ClientIdResolutionFilter` extracts client from request, stores in MDC under key `ClientIdResolutionFilter.MDC_CLIENT_ID`
- **All queries must filter by client ID**: `subscriptionRepository.findByIdAndClientId(id, clientId)`
- **HMAC authentication** (optional): `SubscriptionService.saveSubscription()` generates HMAC credentials; see `HmacKeyStore`
- **Correlation ID**: `TracingFilter` adds `X-Correlation-Id` to MDC (key: `CORRELATION_ID_KEY`) for request tracing

### 5. **Error Handling**

- **GlobalExceptionHandler**: Catches exceptions and returns appropriate HTTP status codes
- **EntityNotFoundException**: Throw for 404s (JPA)
- **ResponseStatusException**: Use for business logic errors (e.g., `new ResponseStatusException(HttpStatus.FORBIDDEN, "message")`)
- **Logging**: Use `@Slf4j` and log at INFO level for business events, DEBUG for tracing

### 6. **Configuration Management**

- **application.yaml**: Default values, environment variable overrides via `${VAR:default}`
- **.envrc.example**: Document all environment variables; developers copy to `.envrc` (gitignored)
- **AppProperties**: Inject properties via `@ConfigurationProperties` or `@Value`
- **Profiles**: Not heavily used; use env vars instead

### 7. **Testing Conventions**

- **Mockito agent required**: See `gradle/github/test.gradle` - Mockito uses `-javaagent` for mocking
- **Test execution**: `./gradlew test` (fails fast with `failFast = true`)
- **Coverage**: `jacocoTestReport` generates HTML in `build/reports/jacoco/test/html/`
- **API tests**: Run against Docker-containerized app; see `apiTest/` directory

## Adding Features: Common Tasks

### Add a New Endpoint

1. Define in OpenAPI spec (external, likely in `api-cp-crime-hearing-case-event-subscription`)
2. Regenerate: `./gradlew openApiGenerate`
3. Implement interface in controller (e.g., `SubscriptionController`)
4. Extract client ID from MDC: `UUID.fromString(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID))`
5. Delegate to service/manager, return HTTP response

### Integrate New External Service

1. Create client in `src/main/java/uk/gov/hmcts/cp/subscription/clients/` with `RestTemplate`
2. Wrap in service layer (e.g., `MaterialService`)
3. Add config properties to `application.yaml` (URL, retry settings)
4. Add retry logic (use `awaitility` or `@Retry` annotation)
5. Handle timeouts and log appropriately

### Add Database Migration

1. Create SQL file: `src/main/resources/db/migration/V<VERSION>__<description>.sql` (Flyway naming)
2. Flyway auto-runs on `bootRun`
3. To reset: drop PostgreSQL container, restart

### Modify Entity & Update Repository

1. Edit entity in `src/main/java/uk/gov/hmcts/cp/subscription/entities/`
2. Create Flyway migration for schema change
3. Add JPA query to repository if needed (e.g., `@Query("SELECT c FROM ClientSubscription c WHERE c.clientId = ?1")`)
4. Update mapper if DTO changes
5. Update tests

## Key Files to Know

| File | Purpose |
|------|---------|
| `README.md` | Setup & local run instructions |
| `README-servicebus.md` | Azure Service Bus architecture notes |
| `.github/pmd-ruleset.xml` | PMD static analysis rules |
| `src/main/resources/db/migration/` | Flyway database migrations |
| `src/main/resources/application.yaml` | Spring configuration with env var overrides |
| `gradle/dependencies/java-core.gradle` | Lombok, Logback, MapStruct versions |
| `gradle/github/test.gradle` | Test execution config (Mockito agent) |
| `Dockerfile` | Multi-stage build; runs on port 4550 with non-root user |
| `docker/` | Startup script and Service Bus config for Docker |
| `apiTest/` | Docker Compose integration tests |

## Debugging & Troubleshooting

- **Service won't start**: Check PostgreSQL is running, env vars set, ports available (4550 default)
- **Material API timeout**: Adjust `MATERIAL_CLIENT_TIMEOUT_MSECS`, `MATERIAL_CLIENT_INTERVAL_MSECS`
- **PMD failures**: Check `.github/pmd-ruleset.xml` rules; common: cyclomatic complexity, method naming
- **Test failures**: Run with `-i` flag for interactive debugging; check MDC setup in filters
- **Service Bus issues**: Enable/disable via `AZURE_SERVICE_BUS_ENABLED`; check connection strings in logs

## Resources

- OpenAPI spec: Generated in `build/generated/` (do not edit manually)
- Spring Boot 4.0 docs: https://spring.io/projects/spring-boot
- Flyway migrations: https://flywaydb.org/
- MapStruct: https://mapstruct.org/
- Awaitility: https://github.com/awaitility/awaitility

