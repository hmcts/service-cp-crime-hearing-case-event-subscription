## Why

When Progression or HearingNows sends an inbound `EventPayload`, consumers receive only a trimmed `EventNotificationPayload` with no way to retrieve the original inbound data. This change persists the raw payload per event and exposes a stable per-subscriber `hearingEventId`, delivered incrementally behind a feature flag.

## What Changes

- Add `hearing-event.json.enabled` property (backed by `HEARING_EVENT_JSON_ENABLED` env var, default `false`) to `application.yaml`
- Wire the toggle into `CallbackDeliveryService` and `NotificationController` so both are toggle-aware from the start
- Flyway migrations `V1.013__add_hearing_event_payload.sql` and `V1.014__add_hearing_event_subscriptions.sql`
- `HearingEventPayloadEntity` and `HearingEventSubscriptionEntity` JPA entities
- `EventPayloadConverter` (JPA `AttributeConverter<EventPayload, String>`) for JSONB serialisation
- `HearingEventPayloadRepository` and `HearingEventSubscriptionRepository`
- `HearingEventPayloadService` with `saveIfAbsent(EventPayload)` and `saveSubscriptionIfAbsent(UUID, UUID)`
- Persistence logic added to `CallbackDeliveryService.submitOutboundEvents()` guarded by `hearingEventJsonEnabled`
- Unit tests: `HearingEventPayloadServiceTest`, `EventPayloadConverterTest`, extended `CallbackDeliveryServiceTest`
- Integration tests: `HearingEventPayloadRepositoryTest`, `HearingEventSubscriptionRepositoryTest`
- `FlywayMigrationIntegrityTest` updated with V1.013/V1.014 checksums
- `IntegrationTestBase.clearAllTables()` extended to clear new tables

## Capabilities

### New Capabilities

- `hearing-event-json-feature-toggle`: `HEARING_EVENT_JSON_ENABLED` property wired into `CallbackDeliveryService` and `NotificationController`; when `false` (default) all new AMP-504 behaviour is suppressed
- `store-inbound-pcr-payload`: persists the raw `EventPayload` once per inbound event (`hearing_event_payload`) and one row per subscriber per event (`hearing_event_subscriptions`), with idempotency guards on both inserts

### Modified Capabilities

(none — no existing requirement changes)

## Impact

- **Database**: two new tables (`hearing_event_payload`, `hearing_event_subscriptions`); no changes to existing tables
- **application.yaml**: `hearing-event.json.enabled` property block
- **New source files**: `HearingEventPayloadEntity`, `HearingEventSubscriptionEntity`, `EventPayloadConverter`, `HearingEventPayloadRepository`, `HearingEventSubscriptionRepository`, `HearingEventPayloadService`
- **CallbackDeliveryService**: gains persistence logic gated by toggle; existing dispatch path unchanged
- **NotificationController**: toggle-injected; no logic change in this increment
- **Test files**: `HearingEventPayloadServiceTest`, `EventPayloadConverterTest`, `HearingEventPayloadRepositoryTest`, `HearingEventSubscriptionRepositoryTest`; `CallbackDeliveryServiceTest` extended with 5 toggle-gated scenarios; `FlywayMigrationIntegrityTest` and `IntegrationTestBase` updated
- **Out of scope (separate PRs)**: GET endpoint, API spec library version bump for `hearingEventId` on outbound payload
