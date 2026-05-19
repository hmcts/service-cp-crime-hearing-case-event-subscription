## ADDED Requirements

### Requirement: hearing_event_payload table created
The service SHALL have a Flyway migration `V1.013__add_hearing_event_payload.sql` that creates the `hearing_event_payload` table with columns: `hearing_event_id UUID PRIMARY KEY NOT NULL` (service-generated UUID), `event_id UUID NOT NULL UNIQUE` (the external inbound event identity from `EventPayload.getEventId()`), `event_type_id INTEGER NOT NULL REFERENCES event_type(id)`, `raw_payload JSONB NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, an index on `event_type_id`, and a unique index on `event_id`.

#### Scenario: Migration applies on startup
- **WHEN** the service starts against a database without `hearing_event_payload`
- **THEN** Flyway applies V1.013 and the table exists with all columns and the index

---

### Requirement: hearing_event_subscriptions table created
The service SHALL have a Flyway migration `V1.014__add_hearing_event_subscriptions.sql` that creates the `hearing_event_subscriptions` table with columns: `id UUID PRIMARY KEY NOT NULL`, `subscription_id UUID NOT NULL REFERENCES client(subscription_id)`, `hearing_event_id UUID NOT NULL REFERENCES hearing_event_payload(hearing_event_id)`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, a unique index on `(subscription_id, hearing_event_id)`, and an index on `hearing_event_id`.

#### Scenario: Migration applies on startup
- **WHEN** the service starts against a database without `hearing_event_subscriptions`
- **THEN** Flyway applies V1.014 and the table exists with all columns, the unique constraint, and both indexes

#### Scenario: Duplicate subscription insert rejected
- **WHEN** a row with the same `(subscription_id, hearing_event_id)` pair is inserted twice
- **THEN** the database rejects the second insert with a unique constraint violation

---

### Requirement: HearingEventPayloadEntity exists
The service SHALL have a JPA entity `HearingEventPayloadEntity` mapping `hearing_event_payload` with: PK `hearingEventId (UUID)` annotated `@GeneratedValue(strategy = GenerationType.UUID)` (service-generated, not supplied by caller), `eventId (UUID)` plain field (maps to `event_id` column — the external inbound event identity from `EventPayload.getEventId()`), `eventTypeId (Long)` plain field, `rawPayload (EventPayload)` annotated with `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(columnDefinition = "jsonb")`, `createdAt (OffsetDateTime)`. Lombok: `@Getter @Builder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode`.

#### Scenario: Entity maps to correct table
- **WHEN** `HearingEventPayloadEntity` is persisted with `eventId` set and no `hearingEventId` supplied
- **THEN** a row is written to `hearing_event_payload` with a service-generated UUID as `hearing_event_id` PK and the supplied value in `event_id`

---

### Requirement: HearingEventSubscriptionEntity exists
The service SHALL have a JPA entity `HearingEventSubscriptionEntity` mapping `hearing_event_subscriptions` with: PK `id (UUID, @GeneratedValue(strategy = GenerationType.UUID))`, `subscriptionId (UUID)` plain field, `hearingEventId (UUID)` plain field, `createdAt (OffsetDateTime)`. Lombok: `@Getter @Builder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode`.

#### Scenario: Entity generates UUID on persist
- **WHEN** `HearingEventSubscriptionEntity` is persisted without an explicit `id`
- **THEN** a UUID is generated and assigned as the PK

---

### Requirement: EventPayload serialised to JSONB via @JdbcTypeCode
`HearingEventPayloadEntity.rawPayload` SHALL be annotated `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(columnDefinition = "jsonb")`. Hibernate 6 uses its own internal `ObjectMapper` (not the project's `JsonMapper` bean) — see design D3. The divergence in `Instant` serialisation is accepted as a known trade-off; `EventPayload.timestamp` is not currently populated by inbound PCR events so there is no immediate impact.

#### Scenario: Payload persists and is retrievable
- **WHEN** an `EventPayload` is written to the database
- **THEN** the row exists in `hearing_event_payload` and the `raw_payload` column contains the serialised JSON
---

### Requirement: HearingEventPayloadRepository persists entities
`HearingEventPayloadRepository extends JpaRepository<HearingEventPayloadEntity, UUID>` SHALL persist `HearingEventPayloadEntity` rows. Idempotency on re-delivery is enforced by the `UNIQUE` constraint on `event_id` at the database level — no application-level existence check method is required.

#### Scenario: Entity persists with service-generated hearingEventId
- **WHEN** a `HearingEventPayloadEntity` is saved with `eventId` set and no `hearingEventId` supplied
- **THEN** a row is written with a service-generated UUID as `hearing_event_id` PK and the supplied value in `event_id`

---

### Requirement: HearingEventSubscriptionRepository provides existence check
`HearingEventSubscriptionRepository extends JpaRepository<HearingEventSubscriptionEntity, UUID>` SHALL provide:
- `boolean existsBySubscriptionIdAndHearingEventId(UUID subscriptionId, UUID hearingEventId)`

#### Scenario: existsBySubscriptionIdAndHearingEventId returns true on duplicate
- **WHEN** a row exists for the given `(subscriptionId, hearingEventId)` pair
- **THEN** `existsBySubscriptionIdAndHearingEventId` returns `true`

#### Scenario: existsBySubscriptionIdAndHearingEventId returns false for unknown pair
- **WHEN** no row exists for the given `(subscriptionId, hearingEventId)` pair
- **THEN** `existsBySubscriptionIdAndHearingEventId` returns `false`

#### Scenario: Duplicate subscription insert rejected
- **WHEN** a row with the same `(subscription_id, hearing_event_id)` pair is inserted twice
- **THEN** the database rejects the second insert with a unique constraint violation

---

### Requirement: HearingEventPayloadService persists rows
`HearingEventPayloadService` SHALL provide:
- `UUID saveIfAbsent(EventPayload eventPayload)` — null-checks `eventPayload.getEventId()`; resolves `eventTypeId` via `EventTypeRepository.findByEventName(eventPayload.getEventType())`; builds and persists a `HearingEventPayloadEntity` with `eventId` set; returns the service-generated `hearingEventId`. Idempotency on re-delivery is enforced by the DB `UNIQUE` constraint on `event_id`.
- `saveSubscriptionIfAbsent(UUID subscriptionId, UUID hearingEventId)` — checks `existsBySubscriptionIdAndHearingEventId`; if absent, builds and persists a `HearingEventSubscriptionEntity` with a generated `id`

#### Scenario: saveIfAbsent persists entity with correct eventTypeId and returns generated hearingEventId
- **WHEN** `saveIfAbsent(eventPayload)` is called with a known event type
- **THEN** `HearingEventPayloadEntity` is persisted with the correct `eventTypeId` resolved from `event_type`, `eventId` set from `eventPayload.getEventId()`, and the service-generated `hearingEventId` is returned

#### Scenario: saveIfAbsent throws when eventId is null
- **WHEN** `saveIfAbsent(eventPayload)` is called with `eventPayload.getEventId()` returning `null`
- **THEN** a `NullPointerException` is thrown with message "eventId must not be null"

#### Scenario: saveSubscriptionIfAbsent persists entity
- **WHEN** `saveSubscriptionIfAbsent(subscriptionId, hearingEventId)` is called and no existing row
- **THEN** `HearingEventSubscriptionEntity` is persisted with the supplied values and a generated `id`

---

### Requirement: CallbackDeliveryService persists rows when toggle on
`CallbackDeliveryService.submitOutboundEvents(eventPayload, documentId, hearingEventJsonEnabled)` receives the toggle as a `boolean` parameter from `NotificationManager`. When `hearingEventJsonEnabled = true`, it SHALL:
1. Before the per-client loop: call `hearingEventPayloadService.saveIfAbsent(eventPayload)` and capture the returned `hearingEventId`
2. For each client: call `hearingEventPayloadService.saveSubscriptionIfAbsent(subscriptionId, hearingEventId)` only when `hearingEventId != null`
When `hearingEventJsonEnabled = false`, no persistence calls are made.

#### Scenario: Payload row created on first delivery
- **WHEN** `hearingEventJsonEnabled = true` and no row exists for the event
- **THEN** `HearingEventPayloadEntity` is persisted exactly once

#### Scenario: Payload row not duplicated on re-delivery
- **WHEN** `hearingEventJsonEnabled = true` and a row already exists for the event
- **THEN** `saveIfAbsent()` skips the insert (idempotency guard)

#### Scenario: Subscription row created per subscriber
- **WHEN** `hearingEventJsonEnabled = true` and no subscription row exists for the client
- **THEN** `HearingEventSubscriptionEntity` is persisted for that client

#### Scenario: Subscription row not duplicated on re-delivery
- **WHEN** `hearingEventJsonEnabled = true` and a subscription row already exists for the client/event
- **THEN** `saveSubscriptionIfAbsent()` skips the insert (idempotency guard)

#### Scenario: No persistence when toggle off
- **WHEN** `hearingEventJsonEnabled = false`
- **THEN** no rows are written to `hearing_event_payload` or `hearing_event_subscriptions`
