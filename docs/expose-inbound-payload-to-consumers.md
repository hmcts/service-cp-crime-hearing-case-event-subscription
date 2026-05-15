# Exposing Inbound PCR/HearingNows Payload to Consumers

## Open Decisions

> These need team sign-off before implementation begins.

### a) Payload storage format — string vs structured data

Should `raw_payload` be stored as `JSONB` (structured, queryable) or encrypted `TEXT` (see decision b)?

| Option | Pros | Cons |
|--------|------|------|
| `JSONB` | PostgreSQL JSON path queries; human-readable in DB | PII visible in plaintext to anyone with DB access |
| Encrypted `TEXT` | PII protected at rest | Cannot query payload fields in SQL; requires app-layer decrypt on every read |

**Note:** if we encrypt (decision b), JSONB is moot — we must use `TEXT`.

---

### b) Encrypt payload at rest — **Colin says yes**

The payload contains `DefendantName` and `DefendantDateOfBirth`, which are personal data. Storing in plaintext means anyone with database access can read it.

**Proposed approach:** application-level encryption using a symmetric key (e.g. AES-256-GCM) managed via Azure Key Vault. Encrypt before `INSERT`, decrypt after `SELECT`. The JPA `@Converter` (`EventPayloadConverter`) is the natural place to do this — no change to the entity or repository interface.

**Reference implementation:** [hmcts/service-hmcts-springboot-demo — postgres-encrypt-demo](https://github.com/hmcts/service-hmcts-springboot-demo/tree/main/postgres-encrypt-demo)

---

## Overview

When Progression or HearingNows sends us an inbound event (`EventPayload`), we currently process it and deliver a trimmed `EventNotificationPayload` to subscribers. This change persists the **raw inbound payload** and exposes it to consumers via a stable per-subscriber `hearingEventId`.

---

## Feature Toggle

The entire feature is gated by `NOTIFICATION_JSON_ENABLED` (default `false`). When off: no rows are written to `notification_payload` or `notification_subscriptions`, `hearingEventId` is omitted from the outbound payload, and the GET endpoint returns 404.

```yaml
# application.yaml
notification:
  json:
    enabled: ${NOTIFICATION_JSON_ENABLED:false}
```

Switch on in tests via `application-test.yaml` or `@TestPropertySource(properties = "notification.json.enabled=true")`.

`NOTIFICATION_JSON_ENABLED` must be added to `.envrc.example`.

---

## What We Need

| # | Concern | Approach |
|---|---------|----------|
| 1 | Persist raw payload once per event | `notification_payload` table |
| 2 | Track which subscribers received which events | `notification_subscriptions` table — one row per subscriber per event |
| 3 | Stable consumer-facing ID | `hearingEventId` (UUID) generated per subscriber on `notification_subscriptions` |
| 4 | Block new subscribers from older events | Rows only created for subscribers active at event time — no back-fill |
| 5 | Consumer access | New `GET /subscription/{subscriptionId}/hearing-events/{hearingEventId}` endpoint |

---

## Database

### Table: `notification_payload`

Stores the raw `EventPayload` JSON **once per inbound event**.

```sql
-- V1.013__add_notification_payload.sql
CREATE TABLE notification_payload (
    event_id    VARCHAR     PRIMARY KEY,
    event_type  VARCHAR     NOT NULL,
    raw_payload JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_payload_event_type ON notification_payload (event_type);
```

| Column | Notes |
|--------|-------|
| `event_id` | The `eventId` from the inbound `EventPayload` — natural PK, ensures one row per event |
| `event_type` | Denormalised for cheap filtering |
| `raw_payload` | Full `EventPayload` serialised as JSONB |
| `created_at` | Ingest timestamp |

---

### Table: `notification_subscriptions`

One row per subscriber per event. This is where access control lives.

```sql
-- V1.014__add_notification_subscriptions.sql
CREATE TABLE notification_subscriptions (
    hearing_event_id  UUID        PRIMARY KEY,
    subscription_id  UUID        NOT NULL REFERENCES client(subscription_id),
    event_id         VARCHAR     NOT NULL REFERENCES notification_payload(event_id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_ns_sub_event ON notification_subscriptions (subscription_id, event_id);
CREATE INDEX idx_ns_event_id         ON notification_subscriptions (event_id);
```

| Column | Notes |
|--------|-------|
| `hearing_event_id` | UUID we generate — unique per subscriber, this is what consumers use |
| `subscription_id` | FK to `client.subscription_id` |
| `event_id` | FK to `notification_payload.event_id` |
| `created_at` | When this subscriber was notified |

**Access control is implicit**: a subscriber can only retrieve a notification if a row exists for their `subscription_id`. New subscribers are never back-filled, so they have no rows for historical events.

The unique index on `(subscription_id, event_id)` also provides idempotency — re-delivery of the same event to the same subscriber is a no-op.

---

## Entities

### `NotificationPayloadEntity`
```
table: notification_payload
pk: eventId (String)
fields: eventType (String), rawPayload (String, columnDefinition="jsonb"), createdAt (OffsetDateTime)
```

### `NotificationSubscriptionEntity`
```
table: notification_subscriptions
pk: hearingEventId (UUID, @GeneratedValue UUID strategy)
fields: subscriptionId (UUID), eventId (String), createdAt (OffsetDateTime)
```

---

## Repositories

```java
// NotificationPayloadRepository extends JpaRepository<NotificationPayloadEntity, String>
boolean existsByEventId(String eventId);

// NotificationSubscriptionRepository extends JpaRepository<NotificationSubscriptionEntity, UUID>
Optional<NotificationSubscriptionEntity> findByHearingEventIdAndSubscriptionId(UUID hearingEventId, UUID subscriptionId);
boolean existsBySubscriptionIdAndEventId(UUID subscriptionId, String eventId);
```

---

## Processing Flow

Both rows are created inside `CallbackDeliveryService.submitOutboundEvents()` — at the point where we already have the list of subscribed clients.

```
CallbackDeliveryService.submitOutboundEvents(EventPayload, documentId)
    ↓
1. If !existsByEventId(eventId):
       Persist NotificationPayloadEntity (eventId, eventType, serialize(eventPayload))

For each subscribed client:
    2. Skip if existsBySubscriptionIdAndEventId(subscriptionId, eventId)  ← idempotency
    3. Generate hearingEventId = UUID.randomUUID()
    4. Persist NotificationSubscriptionEntity (hearingEventId, subscriptionId, eventId)
    5. [existing] Map EventPayload → EventNotificationPayload
    6. [existing] Queue/send to subscriber
```

---

## Outbound Payload API Spec Change

The existing `EventNotificationPayload` (delivered to subscribers via callback) must be updated to include `hearingEventId` alongside the existing `documentId`. Subscribers then have two retrieval paths:

| ID | Retrieval endpoint | Returns |
|----|-------------------|---------|
| `hearingEventId` | `GET /subscription/{subscriptionId}/hearing-events/{hearingEventId}` | Raw JSON payload (this feature) |
| `documentId` | `GET /getDocument/{clientSubscriptionId}/{documentId}` | Document PDF (existing) |

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

`hearingEventId` is populated in `CallbackDeliveryService` at the point the `NotificationSubscriptionEntity` is persisted (step 3 of the processing flow above).

---

## New Endpoint

```
GET /subscription/{subscriptionId}/hearing-events/{hearingEventId}
```

- **Auth**: `ClientIdResolutionFilter` supplies `clientId` via MDC; resolve to `subscriptionId` via `ClientRepository`.
- **Query**: `findByHearingEventIdAndSubscriptionId(hearingEventId, subscriptionId)` on `notification_subscriptions` → 404 if no row.
- **Payload**: join to `notification_payload` via `eventId`, deserialise `rawPayload`.
- **Layer**: `NotificationController` → `NotificationManager.getInboundPayload(clientId, hearingEventId)` → `NotificationPayloadService`.

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

Use `ObjectMapper` (Jackson, already on classpath) to serialise `EventPayload → String` on write and deserialise on read. A `@Converter` implementing `AttributeConverter<EventPayload, String>` keeps the entity clean.

Store as `JSONB` (not `TEXT`) — enables future PostgreSQL JSON path queries.

---

## Checklist

- [ ] `V1.013__add_notification_payload.sql`
- [ ] `V1.014__add_notification_subscriptions.sql`
- [ ] `NotificationPayloadEntity` + `NotificationSubscriptionEntity`
- [ ] `NotificationPayloadRepository` + `NotificationSubscriptionRepository`
- [ ] `EventPayloadConverter` (JPA `AttributeConverter`)
- [ ] `NotificationPayloadService` — `save()` + `getByHearingEventId(clientId, hearingEventId)`
- [ ] `NotificationManager.getInboundPayload()` — thin orchestration
- [ ] `NotificationController` — new GET endpoint
- [ ] `InboundPayloadResponse` DTO (OpenAPI spec update → `openApiGenerate`)
- [ ] Add `hearingEventId` to `EventNotificationPayload` in OpenAPI spec → `openApiGenerate`
- [ ] Populate `hearingEventId` on outbound payload in `CallbackDeliveryService`
- [ ] Persist rows in `CallbackDeliveryService.submitOutboundEvents()`
- [ ] `NOTIFICATION_JSON_ENABLED` property wired into `CallbackDeliveryService` and `NotificationController`
- [ ] Add `NOTIFICATION_JSON_ENABLED` to `.envrc.example`
- [ ] Idempotency guards before each insert
- [ ] Unit tests for service + idempotency path
- [ ] Integration test: POST createNotification → GET payload returns same data; new subscriber cannot GET older notification