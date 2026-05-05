# CLAUDE.md

Keep replies extremely concise. No filler. No long code snippets.

## Commands

```bash
./gradlew clean build
./gradlew test                                                                           # Testcontainers starts PostgreSQL automatically
./gradlew test --tests "uk.gov.hmcts.cp.subscription.services.NotificationServiceTest"
./gradlew dockerTest          # full docker-compose stack (Service Bus emulator + SQL Edge)
./gradlew bootRun             # requires PostgreSQL on localhost:5432
./gradlew pmdMain
./gradlew jacocoTestReport    # → build/reports/jacoco/
./gradlew openApiGenerate     # regenerate from OpenAPI spec — never edit build/generated/ manually
```

## Rules

- **Client ID is mandatory in every query** — extract with `UUID.fromString(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID))`. Every repository call must include client ID.
- **Controllers are thin** — delegate to `NotificationManager` or services; return HTTP responses only.
- **Service layering**: Controller → Manager → Service → Client/Repository. No skipping layers.
- **MapStruct mappers** in `src/main/java/.../mappers/` handle entity ↔ DTO. Never edit generated `*Impl` classes.
- **Error handling**: `EntityNotFoundException` for 404s, `ResponseStatusException` for business errors. `GlobalExceptionHandler` maps the rest.
- **Config**: `application.yaml` uses `${VAR:default}`. All new vars must be documented in `.envrc.example`.
- **Logging**: `@Slf4j` — INFO for business events, DEBUG for tracing.
- **Migrations**: Flyway naming `V<VERSION>__<description>.sql` in `src/main/resources/db/migration/`. Auto-runs on `bootRun`.

## Event Processing Pipeline

```
PCR Inbound Event (from Progression/HearingNows)
    ↓
NotificationController.createNotification()
    ↓ [ServiceBus enabled → queue to PCR_INBOUND_TOPIC]
    ↓ [else → synchronous]
NotificationManager.processPcrNotification()
    ↓
NotificationService.processInboundEvent()
    ├→ MaterialService.waitForMaterialMetadata()  [awaitility polling]
    └→ DocumentService.saveDocumentMapping()
    ↓
CallbackDeliveryService.submitOutboundPcrEvents()
    └→ [ServiceBus enabled → queue to PCR_OUTBOUND_TOPIC per subscriber]
       [else → CallbackService.sendToSubscriber()]
```

## Service Bus (Azure)

Toggle with `AZURE_SERVICE_BUS_ENABLED=true`. When off, all processing is synchronous.

| Var                          | Purpose                                            |
|------------------------------|----------------------------------------------------|
| `AZURE_SERVICEBUS_URI`       | Connection string                                  |
| `AZURE_SERVICE_BUS_ADMIN_URI` | Admin URI                                         |
| `PCR_INBOUND_TOPIC`          | Inbound PCR events                                 |
| `PCR_OUTBOUND_TOPIC`         | Outbound per-subscriber notifications              |
| `SERVICE_BUS_RETRY_SECONDS`  | Comma-separated delays e.g. `"0,1000,2000,10000"` |
| `SERVICE_BUS_MAX_TRIES`      | Max delivery attempts                              |

Message body is a JSON string wrapped in `ServiceBusMessage` (fields: `correlationId`, `failureCount`, `targetUrl`).

## Database

PostgreSQL 15. Flyway migrations auto-run. All entities use UUID PKs (`@GeneratedValue(strategy = GenerationType.UUID)`).

## Debugging

- **Won't start**: PostgreSQL running? Env vars set? Port 4550 free?
- **Material timeout**: Tune `MATERIAL_CLIENT_TIMEOUT_MSECS` / `MATERIAL_CLIENT_INTERVAL_MSECS`.
- **PMD failures**: Check `.github/pmd-ruleset.xml` — common: cyclomatic complexity, method naming.
- **Test failures**: Run with `-i`; check MDC setup in filters.
- **Service Bus**: Toggle `AZURE_SERVICE_BUS_ENABLED`; inspect connection strings in logs.