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
`HearingEventPayloadEntity.rawPayload` SHALL be annotated `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(columnDefinition = "jsonb")`. Hibernate 6 SHALL use the project's `JsonMapper` ObjectMapper (JavaTimeModule + ISO-8601 dates) so that `EventPayload.timestamp (Instant)` serialises consistently with the rest of the codebase.

#### Scenario: Payload round-trips via @JdbcTypeCode
- **WHEN** an `EventPayload` (including `timestamp`) is written to the database and read back
- **THEN** all fields are preserved and equal to the original, including the `Instant` timestamp
---

### Requirement: HearingEventPayloadRepository provides existence check
`HearingEventPayloadRepository extends JpaRepository<HearingEventPayloadEntity, UUID>` SHALL provide `boolean existsByEventId(UUID eventId)` for idempotency checks using the external inbound event identity.

#### Scenario: Returns true for existing event
- **WHEN** a `HearingEventPayloadEntity` with a given `eventId` has been persisted
- **THEN** `existsByEventId(eventId)` returns `true`

#### Scenario: Returns false for unknown event
- **WHEN** no `HearingEventPayloadEntity` exists for a given `eventId`
- **THEN** `existsByEventId(eventId)` returns `false`

---

### Requirement: HearingEventSubscriptionRepository provides lookup and existence check
`HearingEventSubscriptionRepository extends JpaRepository<HearingEventSubscriptionEntity, UUID>` SHALL provide:
- `Optional<HearingEventSubscriptionEntity> findByIdAndSubscriptionId(UUID id, UUID subscriptionId)`
- `boolean existsBySubscriptionIdAndHearingEventId(UUID subscriptionId, UUID hearingEventId)`

#### Scenario: findByIdAndSubscriptionId returns entity on match
- **WHEN** a subscription row exists for the given `id` and `subscriptionId`
- **THEN** `findByIdAndSubscriptionId` returns a non-empty Optional containing that entity

#### Scenario: findByIdAndSubscriptionId returns empty on mismatch
- **WHEN** the `id` exists but belongs to a different `subscriptionId`
- **THEN** `findByIdAndSubscriptionId` returns an empty Optional

#### Scenario: existsBySubscriptionIdAndHearingEventId returns true on duplicate
- **WHEN** a row exists for the given `(subscriptionId, hearingEventId)` pair
- **THEN** `existsBySubscriptionIdAndHearingEventId` returns `true`

#### Scenario: existsBySubscriptionIdAndHearingEventId returns false for unknown pair
- **WHEN** no row exists for the given `(subscriptionId, hearingEventId)` pair
- **THEN** `existsBySubscriptionIdAndHearingEventId` returns `false`

---

### Requirement: HearingEventPayloadService persists rows
`HearingEventPayloadService` SHALL provide:
- `UUID saveIfAbsent(EventPayload eventPayload)` — null-checks `eventPayload.getEventId()`; checks `existsByEventId(eventId)`; if absent, resolves `eventTypeId` via `EventTypeRepository.findByEventName(eventPayload.getEventType())`, builds and persists a `HearingEventPayloadEntity` with `eventId` set, and returns the service-generated `hearingEventId`; if already present, returns `null`
- `saveSubscriptionIfAbsent(UUID subscriptionId, UUID hearingEventId)` — checks `existsBySubscriptionIdAndHearingEventId`; if absent, builds and persists a `HearingEventSubscriptionEntity` with a generated `id`

#### Scenario: saveIfAbsent persists entity with correct eventTypeId and returns generated hearingEventId
- **WHEN** `saveIfAbsent(eventPayload)` is called with a known event type and no existing row
- **THEN** `HearingEventPayloadEntity` is persisted with the correct `eventTypeId` resolved from `event_type`, `eventId` set from `eventPayload.getEventId()`, and the service-generated `hearingEventId` is returned

#### Scenario: saveIfAbsent returns null when row already exists
- **WHEN** `saveIfAbsent(eventPayload)` is called and a row already exists for `eventId`
- **THEN** no insert is performed and `null` is returned

#### Scenario: saveSubscriptionIfAbsent persists entity
- **WHEN** `saveSubscriptionIfAbsent(subscriptionId, hearingEventId)` is called and no existing row
- **THEN** `HearingEventSubscriptionEntity` is persisted with the supplied values and a generated `id`

---

### Requirement: CallbackDeliveryService persists rows when toggle on
When `hearingEventJsonEnabled = true`, `CallbackDeliveryService.submitOutboundEvents()` SHALL:
1. Before the per-client loop: call `hearingEventPayloadService.saveIfAbsent(eventPayload)` and capture the returned `hearingEventId` (may be `null` if the row already exists)
2. For each client: call `hearingEventPayloadService.saveSubscriptionIfAbsent(subscriptionId, hearingEventId)` only when `hearingEventId != null` — idempotency is enforced inside the service
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
