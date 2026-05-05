# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Keep replies extremely concise. No unnecessary filler, no long code snippets.

## Commands

```bash
./gradlew clean build
./gradlew test                                                                           # all tests; Testcontainers starts PostgreSQL automatically
./gradlew test --tests "uk.gov.hmcts.cp.subscription.services.NotificationServiceTest"  # single test class
./gradlew dockerTest          # integration tests against full docker-compose stack (Service Bus emulator + SQL Edge)
./gradlew bootRun             # requires PostgreSQL on localhost:5432 — see README.md for docker run command
./gradlew pmdMain             # PMD static analysis
./gradlew jacocoTestReport    # coverage report → build/reports/jacoco/
```

## Overview

Spring Boot 4.0 event subscription microservice for criminal court cases. Manages client subscriptions, processes PCR events from Progression, retrieves documents from Material service, and delivers notifications to subscribers via callback URLs.

## Event Processing Pipeline

```
PCR Inbound Event (from Progression)
    ↓
NotificationController.createNotification()
    ↓ [if ServiceBus enabled → queued to PCR_INBOUND_TOPIC]
    ↓ [else → synchronous]
NotificationManager.processPcrNotification()
    ↓
NotificationService.processInboundEvent()
    ├→ MaterialService.waitForMaterialMetadata()  [awaitility polling]
    └→ DocumentService.saveDocumentMapping()
    ↓
CallbackDeliveryService.submitOutboundPcrEvents()
    ├→ Query subscriptions by event type
    └→ [if ServiceBus enabled → queue to PCR_OUTBOUND_TOPIC per subscriber]
        [else → CallbackService.sendToSubscriber()]
```

## Key Components

- **Controllers**: `NotificationController`, `SubscriptionController` — implement OpenAPI-generated interfaces
- **Managers**: `NotificationManager` — orchestrates multi-service workflows
- **Services**: `NotificationService`, `SubscriptionService`, `DocumentService`, `MaterialService`, `CallbackDeliveryService`, `CallbackService`
- **Clients**: `MaterialClient`, `CallbackClient` — stateless HTTP integrations
- **Entities**: `ClientSubscriptionEntity`, `ClientEventEntity`, `DocumentMappingEntity`, `EventTypeEntity` — all use UUID PKs
- **Repositories**: Spring Data JPA (`JpaRepository<Entity, UUID>`)

## Critical Design Decisions

1. **Async vs Sync**: `AZURE_SERVICE_BUS_ENABLED` toggles async processing via Azure Service Bus topics.
2. **MaterialService polling**: Uses `awaitility` with configurable `MATERIAL_CLIENT_INTERVAL_MSECS` / `MATERIAL_CLIENT_TIMEOUT_MSECS`.
3. **Manager pattern**: `NotificationManager` keeps controllers thin.
4. **Multi-tenancy**: Every query must filter by client ID from MDC key `ClientIdResolutionFilter.MDC_CLIENT_ID`.

## Service Bus (Azure)

- **Enable**: `AZURE_SERVICE_BUS_ENABLED=true`
- **Inbound topic**: `PCR_INBOUND_TOPIC` — queues PCR events so Progression gets a fast reply; consumer polls Material service (may wait for doc readiness)
- **Outbound topic**: `PCR_OUTBOUND_TOPIC` — one queue item per subscriber; processed independently
- **Message wrapper**: `ServiceBusMessage` body is a JSON string with `correlationId`, `failureCount`, `targetUrl`, etc.
- **Retries**: `SERVICE_BUS_RETRY_SECONDS` (comma-separated, e.g. `"0,1000,2000,10000"`), `SERVICE_BUS_MAX_TRIES`
- **Connection**: `AZURE_SERVICEBUS_URI`, `AZURE_SERVICE_BUS_ADMIN_URI`

## Patterns & Conventions

- **OpenAPI-first**: Controllers implement generated interfaces. Never manually edit `build/generated/`. Regenerate: `./gradlew openApiGenerate`.
- **Service layering**: Controller → Manager → Service → Client/Repository.
- **MapStruct mappers**: `src/main/java/uk/gov/hmcts/cp/subscription/mappers/` — entity ↔ DTO. Don't edit generated `Impl` classes.
- **Security**: Extract client ID with `UUID.fromString(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID))`. All repo queries must include client ID.
- **Error handling**: `GlobalExceptionHandler` centralises HTTP status mapping. Use `EntityNotFoundException` for 404s, `ResponseStatusException` for business errors.
- **Config**: `application.yaml` uses `${VAR:default}` overrides. Document all vars in `.envrc.example`.
- **Logging**: `@Slf4j`, INFO for business events, DEBUG for tracing.

## Database

PostgreSQL 15, Flyway migrations in `src/main/resources/db/migration/` (naming: `V<VERSION>__<description>.sql`). Flyway runs automatically on `bootRun`.

## External Services

| Service | Env Var | Retry |
|---------|---------|-------|
| Material API | `MATERIAL_CLIENT_URL` | awaitility (interval/timeout msecs) |
| Document Service | `DOCUMENT_SERVICE_URL` | configured in `DocumentService` |
| Callback URLs | `ClientSubscriptionEntity.notificationEndpoint` | `callback-client.retry` (interval/timeout msecs) |

## Key Files

| File | Purpose |
|------|---------|
| `src/main/resources/application.yaml` | Spring config with env var overrides |
| `src/main/resources/db/migration/` | Flyway migrations |
| `src/main/java/.../config/AppProperties.java` | Typed config properties |
| `src/main/java/.../filters/ClientIdResolutionFilter.java` | Client ID → MDC |
| `.github/pmd-ruleset.xml` | PMD static analysis rules |
| `gradle/github/test.gradle` | Mockito agent + JaCoCo config |
| `Dockerfile` | Multi-stage build; port 4550, non-root user |

## Debugging

- **Won't start**: Check PostgreSQL running, env vars set, port 4550 free.
- **Material timeout**: Tune `MATERIAL_CLIENT_TIMEOUT_MSECS`, `MATERIAL_CLIENT_INTERVAL_MSECS`.
- **PMD failures**: Common causes — cyclomatic complexity, method naming. See `.github/pmd-ruleset.xml`.
- **Test failures**: Run with `-i` flag; check MDC setup in filters.
- **Service Bus issues**: Toggle `AZURE_SERVICE_BUS_ENABLED`; check connection strings in logs.