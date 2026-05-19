# Exposing Inbound PCR/HearingNows Payload to Consumers

## Open Decisions

### a) Payload storage format — **resolved: JSONB**

Implemented as `JSONB` in `hearing_event_payload.raw_payload`. Encryption (decision b) is deferred; if adopted, the column type will change to `TEXT` and an `AttributeConverter` (`EventPayloadConverter`) will handle encrypt-before-insert / decrypt-after-select.

---

### b) Encrypt payload at rest — **Colin says yes — deferred to future **

The payload contains `DefendantName` and `DefendantDateOfBirth`, which are personal data. Storing in plaintext means anyone with database access can read it.

**Proposed approach:** application-level encryption using a symmetric key (e.g. AES-256-GCM) managed via Azure Key Vault. Encrypt before `INSERT`, decrypt after `SELECT`. The JPA `@Converter` (`EventPayloadConverter`) is the natural place to do this — no change to the entity or repository interface.

**Reference implementation:** [hmcts/service-hmcts-springboot-demo — postgres-encrypt-demo](https://github.com/hmcts/service-hmcts-springboot-demo/tree/main/postgres-encrypt-demo)

---

## Overview

When Progression or HearingNows sends us an inbound event (`EventPayload`), we currently process it and deliver a trimmed `EventNotificationPayload` to subscribers. This change persists the **raw inbound payload** and exposes it to consumers via a stable per-subscriber `hearingEventId`.

---

## Feature Toggle

The entire feature is gated by `HEARING_EVENT_JSON_ENABLED` (default `false`). When off: no rows are written to `hearing_event_payload` or `hearing_event_subscriptions`, `hearingEventId` is omitted from the outbound payload, and the GET endpoint returns 404.

```yaml
# application.yaml
hearing-event:
  json:
    enabled: ${HEARING_EVENT_JSON_ENABLED:false}
```

The toggle is injected into `NotificationManager` (which has no repository dependencies) and passed as a `boolean` parameter to `CallbackDeliveryService.submitOutboundEvents(eventPayload, documentId, hearingEventJsonEnabled)`. This keeps persistence-owning classes toggle-blind (T4).

Switch on in tests via `application-test.yaml` or `@TestPropertySource(properties = "hearing-event.json.enabled=true")`.

---

## What We Need

| # | Concern | Approach |
|---|---------|----------|
| 1 | Persist raw payload once per event | `hearing_event_payload` table |
| 2 | Track which subscribers received which events | `hearing_event_subscriptions` table — one row per subscriber per event |
| 3 | Stable consumer-facing ID | `hearingEventId` (UUID) generated per subscriber on `hearing_event_subscriptions` |
| 4 | Block new subscribers from older events | Rows only created for subscribers active at event time — no back-fill |
| 5 | Consumer access | New `GET /client-subscriptions/{clientSubscriptionId}/hearing-events/{hearingEventId}` endpoint |

---

## Database

### Table: `hearing_event_payload`

Stores the raw `EventPayload` JSON **once per inbound event**.

```sql
-- V1.013__add_hearing_event_payload.sql
CREATE TABLE hearing_event_payload (
    hearing_event_id  uuid        primary key not null,
    event_id          uuid        not null unique,
    event_type_id     integer     not null REFERENCES event_type(id),
    raw_payload       jsonb       not null,
    created_at        timestamptz not null default now()
);

CREATE INDEX idx_hearing_event_payload_event_type ON hearing_event_payload (event_type_id);
```

| Column | Notes |
|--------|-------|
| `hearing_event_id` | UUID — service-generated PK (`@GeneratedValue`); FK target in `hearing_event_subscriptions`; opaque to external callers |
| `event_id` | UUID — the `eventId` from the inbound `EventPayload`; natural key for idempotency; `UNIQUE` enforces one row per inbound event at DB level |
| `event_type_id` | FK to `event_type(id)` |
| `raw_payload` | Full `EventPayload` serialised as JSONB |
| `created_at` | Ingest timestamp |

---

### Table: `hearing_event_subscriptions`

One row per subscriber per event. This is where access control lives.

```sql
-- V1.014__add_hearing_event_subscriptions.sql
CREATE TABLE hearing_event_subscriptions (
    id                uuid        primary key not null,
    subscription_id   uuid        not null REFERENCES client(subscription_id),
    hearing_event_id  uuid        not null REFERENCES hearing_event_payload(hearing_event_id),
    created_at        timestamptz not null default now()
);

CREATE UNIQUE INDEX idx_ns_sub_event ON hearing_event_subscriptions (subscription_id, hearing_event_id);
CREATE INDEX idx_ns_hearing_event_id ON hearing_event_subscriptions (hearing_event_id);
```

| Column | Notes |
|--------|-------|
| `id` | UUID we generate — unique per subscriber, this is the `hearingEventId` exposed to consumers |
| `subscription_id` | FK to `client.subscription_id` |
| `hearing_event_id` | FK to `hearing_event_payload.hearing_event_id` |
| `created_at` | When this subscriber was notified |

**Access control is implicit**: a subscriber can only retrieve a notification if a row exists for their `subscription_id`. New subscribers are never back-filled, so they have no rows for historical events.

The unique index on `(subscription_id, hearing_event_id)` also provides idempotency — re-delivery of the same event to the same subscriber is a no-op.

---

## Entities

### `HearingEventPayloadEntity`
```
table: hearing_event_payload
pk: hearingEventId (UUID, @GeneratedValue(strategy = GenerationType.UUID)) — service-generated, not supplied by caller
fields: eventId (UUID) — EventPayload.getEventId(), natural key for idempotency (maps to event_id UNIQUE column)
        eventTypeId (Long, FK → EventTypeEntity)
        rawPayload (EventPayload, @JdbcTypeCode(SqlTypes.JSON), columnDefinition="jsonb")
        createdAt (OffsetDateTime)
```

### `HearingEventSubscriptionEntity`
```
table: hearing_event_subscriptions
pk: id (UUID, @GeneratedValue UUID strategy)
fields: subscriptionId (UUID), hearingEventId (UUID, FK → HearingEventPayloadEntity), createdAt (OffsetDateTime)
```

---

## Repositories

```java
// HearingEventPayloadRepository extends JpaRepository<HearingEventPayloadEntity, UUID>
// No application-level existence check — idempotency on event_id is enforced by the DB UNIQUE constraint

// HearingEventSubscriptionRepository extends JpaRepository<HearingEventSubscriptionEntity, UUID>
boolean existsBySubscriptionIdAndHearingEventId(UUID subscriptionId, UUID hearingEventId);  // implemented
Optional<HearingEventSubscriptionEntity> findByIdAndSubscriptionId(UUID id, UUID subscriptionId);  // future: needed for GET endpoint
```

---

## Processing Flow

The toggle decision is made in `NotificationManager` and passed as a boolean to `CallbackDeliveryService`. Both payload and subscription rows are created inside `submitOutboundEvents()` — at the point where the list of subscribed clients is available.

```
NotificationManager.processNotification(EventPayload)
    ↓
    hearingEventJsonEnabled = @Value field (default false)
    ↓
CallbackDeliveryService.submitOutboundEvents(EventPayload, documentId, hearingEventJsonEnabled)
    ↓
1. If hearingEventJsonEnabled:
       hearingEventPayloadService.saveIfAbsent(eventPayload)
       → resolves eventTypeId, persists HearingEventPayloadEntity
       → returns service-generated hearingEventId (UUID)
       → DB UNIQUE constraint on event_id rejects duplicate on re-delivery

For each subscribed client (only when hearingEventId != null):
    2. hearingEventPayloadService.saveSubscriptionIfAbsent(subscriptionId, hearingEventId)
       → skips if existsBySubscriptionIdAndHearingEventId  ← application-level idempotency for subscriptions
       → else persists HearingEventSubscriptionEntity (id=@GeneratedValue, subscriptionId, hearingEventId)
    3. [existing] Map EventPayload → EventNotificationPayload
    4. [existing] Queue/send to subscriber
```

---

### API spec change (OpenAPI)

Add `hearingEventId` (string, UUID format, required) to the `EventNotificationPayload` schema in the OpenAPI spec, then run `./gradlew openApiGenerate`.

```yaml
EventNotificationPayload:
  properties:
    hearingEventId:
      type: string
      format: uuid
    documentId:
      type: string
      format: uuid
    # ... existing fields
```

`hearingEventId` is populated in `CallbackDeliveryService` at the point the `HearingEventSubscriptionEntity` is persisted (step 2 of the processing flow above).

---

## New Endpoint

```
GET /client-subscriptions/{clientSubscriptionId}/hearing-events/{hearingEventId}
```

- **Auth**: `ClientIdResolutionFilter` supplies `clientId` via MDC; resolve to `subscriptionId` via `ClientRepository`.
- **Query**: `findByIdAndSubscriptionId(hearingEventId, subscriptionId)` on `hearing_event_subscriptions` → 404 if no row.
- **Payload**: join to `hearing_event_payload` via `hearing_event_id`, deserialise `rawPayload`.
- **Layer**: `NotificationController` → `NotificationManager.getInboundPayload(clientId, hearingEventId)` → `HearingEventPayloadService`.

### Response shape (draft)

```json
{
  "hearingEventId": "...",
  "eventId": "...",
  "eventType": "PRISON_COURT_REGISTER_GENERATED",
  "createdAt": "2026-05-13T10:00:00Z",
  "payload": { /* full EventPayload fields */ }
}
```

---

## Serialisation

Use `@JdbcTypeCode(SqlTypes.JSON)` on `rawPayload` — Hibernate 6 binds the column as `Types.OTHER`, which PostgreSQL accepts for `jsonb` columns without a cast. No separate `AttributeConverter` class is needed.

**Known trade-off**: Hibernate uses its own internal `ObjectMapper`, not the project's `JsonMapper` bean. If `EventPayload.timestamp (Instant)` is populated, it will be serialised as epoch seconds rather than ISO-8601. This is accepted as deferred — `EventPayload.timestamp` is not currently populated by inbound PCR events.

Store as `JSONB` (not `TEXT`) — enables future PostgreSQL JSON path queries.

---

## Checklist

- [x] `V1.013__add_hearing_event_payload.sql`
- [x] `V1.014__add_hearing_event_subscriptions.sql`
- [x] `HearingEventPayloadEntity` + `HearingEventSubscriptionEntity`
- [x] `HearingEventPayloadRepository` + `HearingEventSubscriptionRepository`
- [x] `HearingEventPayloadService` — `saveIfAbsent(EventPayload)` + `saveSubscriptionIfAbsent(UUID, UUID)`
- [x] Persist rows in `CallbackDeliveryService.submitOutboundEvents()`
- [x] `HEARING_EVENT_JSON_ENABLED` property wired into `NotificationManager` (passed as parameter to `CallbackDeliveryService`) and `NotificationController`
- [x] Idempotency guards — DB UNIQUE constraint on `event_id`; `existsBySubscriptionIdAndHearingEventId` before subscription insert
- [x] Unit tests for service, mapper, idempotency path, and toggle behaviour
- [x] Integration tests for repository persistence and unique constraint
- [ ] `HearingEventPayloadService.getByHearingEventId(clientId, hearingEventId)` — needed for GET endpoint
- [ ] `findByIdAndSubscriptionId` on `HearingEventSubscriptionRepository` — needed for GET endpoint
- [ ] `NotificationManager.getInboundPayload()` — thin orchestration for GET endpoint
- [ ] `NotificationController` — new GET endpoint
- [ ] `InboundPayloadResponse` DTO (OpenAPI spec update → `openApiGenerate`)
- [ ] Add `hearingEventId` to `EventNotificationPayload` in OpenAPI spec → `openApiGenerate`
- [ ] Populate `hearingEventId` on outbound payload in `CallbackDeliveryService`
- [ ] Integration test: POST createNotification → GET payload returns same data; new subscriber cannot GET older notification