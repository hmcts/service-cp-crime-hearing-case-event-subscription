# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **See also**: `AGENTS.md` for comprehensive architecture details, common tasks, and troubleshooting.

## Build & Test Commands

```bash
./gradlew clean build            # Full build with tests
./gradlew clean build -x test    # Skip tests
./gradlew test                   # Run tests only
./gradlew bootRun                # Run locally on port 4550
./gradlew pmdMain                # PMD static analysis
./gradlew jacocoTestReport       # Coverage report → build/reports/jacoco/test/html/
cd apiTest && ./build-and-run-apitest.sh  # API tests (requires Docker)
```

Local development requires PostgreSQL:
```bash
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=appdb postgres:15
cp .envrc.example .envrc && direnv allow   # configure env vars
```

## Architecture Overview

Spring Boot 4.0.3 microservice that receives criminal court case PCR events, fetches document metadata, and delivers notifications to registered subscribers via callback URLs.

**Event processing pipeline:**
```
NotificationController → NotificationManager → NotificationService
    → MaterialService (polls Material API with awaitility retry)
    → DocumentService (saves document mapping)
    → CallbackDeliveryService → CallbackService (HTTP delivery)
```

Azure Service Bus integration is **optional** (`AZURE_SERVICE_BUS_ENABLED`, default false). When enabled, inbound events queue to `PCR_INBOUND_TOPIC` and outbound notifications to `PCR_OUTBOUND_TOPIC`. When disabled, everything is synchronous.

**Package structure:**
- `subscription/controllers/` — thin REST controllers implementing OpenAPI-generated interfaces
- `subscription/managers/` — orchestration logic (`NotificationManager`)
- `subscription/services/` — domain logic (MaterialService, CallbackDeliveryService, etc.)
- `subscription/clients/` — external HTTP calls (MaterialClient, CallbackClient)
- `subscription/entities/` — JPA entities (all use UUID PKs)
- `subscription/mappers/` — MapStruct DTO↔entity conversions
- `filters/` — `ClientIdResolutionFilter` (JWT→MDC), `TracingFilter` (correlation ID)
- `hmac/` — HMAC signing for secure callbacks
- `servicebus/` — Azure Service Bus config and message handling

## Key Conventions

**OpenAPI-first**: API interfaces are generated from an external spec dependency (`api-cp-crime-hearing-case-event-subscription`). Controllers implement generated interfaces. Never manually edit generated code; regenerate with `./gradlew openApiGenerate`. Generated code lives in `build/generated/`.

**Multi-tenancy**: Client ID is extracted from JWT by `ClientIdResolutionFilter` and stored in MDC under `ClientIdResolutionFilter.MDC_CLIENT_ID`. All repository queries must filter by client ID (e.g., `findByIdAndClientId(id, clientId)`).

**Layering rule**: Controllers → Managers (orchestration) → Services (domain) → Clients (HTTP) / Repositories (DB). Keep each layer focused.

**Database migrations**: Flyway auto-runs from `src/main/resources/db/migration/`. Name files `V<VERSION>__<description>.sql`. To reset: drop and recreate the PostgreSQL container.

**Testing**: Mockito requires `-javaagent` (configured in `gradle/github/test.gradle`). Tests fail fast. Integration tests use TestContainers (PostgreSQL) and WireMock for HTTP mocking.

**Error handling**: Use `EntityNotFoundException` for 404s, `ResponseStatusException` for business logic errors. `GlobalExceptionHandler` centralizes HTTP error responses.
