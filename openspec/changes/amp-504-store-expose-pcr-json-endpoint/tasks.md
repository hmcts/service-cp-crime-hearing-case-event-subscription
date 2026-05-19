## 1. Configuration

- [x] 1.1 Add `hearing-event.json.enabled: ${HEARING_EVENT_JSON_ENABLED:false}` block to `src/main/resources/application.yaml`

## 2. Service Wiring

- [x] 2.1 Inject `@Value("${hearing-event.json.enabled:false}") boolean hearingEventJsonEnabled` into `CallbackDeliveryService`
- [x] 2.2 Inject `@Value("${hearing-event.json.enabled:false}") boolean hearingEventJsonEnabled` into `NotificationController`

## 3. Database Migrations

- [x] 3.1 Create `src/main/resources/db/migration/V1.013__add_hearing_event_payload.sql` — includes `hearing_event_id UUID PRIMARY KEY` (`@GeneratedValue`), `event_id UUID NOT NULL UNIQUE` (inbound event identity), and index on both
- [x] 3.2 Create `src/main/resources/db/migration/V1.014__add_hearing_event_subscriptions.sql`

## 4. JPA Entities

- [x] 4.1 Create `HearingEventPayloadEntity` (table: `hearing_event_payload`, pk: `hearingEventId UUID @GeneratedValue`, add `eventId UUID` field mapping `event_id`, `@JdbcTypeCode(SqlTypes.JSON)` on `rawPayload`)
- [x] 4.2 Create `HearingEventSubscriptionEntity` (table: `hearing_event_subscriptions`, pk: `id UUID @GeneratedValue UUID`)

## 5. Persistence Infrastructure

- [x] 5.1 `@JdbcTypeCode(SqlTypes.JSON)` on `rawPayload` — Hibernate 6 binds as `Types.OTHER`; no separate converter class needed
- [x] 5.2 Create `HearingEventPayloadRepository extends JpaRepository<HearingEventPayloadEntity, UUID>`
- [x] 5.3 Create `HearingEventSubscriptionRepository extends JpaRepository<HearingEventSubscriptionEntity, UUID>`
- [x] 5.4 Create `HearingEventPayloadService` — `UUID saveIfAbsent(EventPayload)` (returns generated `hearingEventId` or `null` if already exists) + `saveSubscriptionIfAbsent(UUID, UUID)`

## 6. Processing Flow

- [x] 6.1 Inject `HearingEventPayloadService` into `CallbackDeliveryService`
- [x] 6.2 In `submitOutboundEvents()`: persist `HearingEventPayloadEntity` when toggle on, guarded by `existsByEventId` check; capture returned `hearingEventId`
- [x] 6.3 In `submitOutboundEvents()`: persist `HearingEventSubscriptionEntity` per client when toggle on and `hearingEventId != null`, guarded by `existsBySubscriptionIdAndHearingEventId` check

## 7. Unit Tests

- [x] 7.1 Add V1.013 and V1.014 checksums to `FlywayMigrationIntegrityTest`
- [x] 7.2 Add new-table deletions to `IntegrationTestBase.clearAllTables()`
- [x] 7.3 `HearingEventPayloadServiceTest` — `saveIfAbsent()` happy path
- [x] 7.4 `HearingEventPayloadServiceTest` — `saveIfAbsent()` unknown event type throws
- [x] 7.5 `HearingEventPayloadServiceTest` — `saveSubscriptionIfAbsent()` persists supplied values
- [x] 7.6 `HearingEventPayloadServiceTest` — `saveSubscriptionIfAbsent()` skips when row already exists
- [x] 7.7 `CallbackDeliveryServiceTest` — toggle off: no persistence calls
- [x] 7.8 `CallbackDeliveryServiceTest` — toggle on, `saveIfAbsent()` called
- [x] 7.9 `CallbackDeliveryServiceTest` — toggle on, payload present: `saveIfAbsent()` skips
- [x] 7.10 `CallbackDeliveryServiceTest` — toggle on, subscription absent: `saveSubscriptionIfAbsent()` called
- [x] 7.11 `CallbackDeliveryServiceTest` — toggle on, subscription present: `saveSubscriptionIfAbsent()` skips
- [x] 7.12 `CallbackDeliveryServiceTest` — toggle on, `saveIfAbsent()` returns null (re-delivery): `saveSubscriptionIfAbsent()` never called

## 8. Integration Tests

- [x] 8.1 `HearingEventPayloadRepositoryTest` — `existsByEventId` returns true
- [x] 8.2 `HearingEventPayloadRepositoryTest` — `existsByEventId` returns false
- [x] 8.3 `HearingEventSubscriptionRepositoryTest` — `existsBySubscriptionIdAndHearingEventId` returns true
- [x] 8.4 `HearingEventSubscriptionRepositoryTest` — `existsBySubscriptionIdAndHearingEventId` returns false
- [x] 8.5 `HearingEventSubscriptionRepositoryTest` — `findByIdAndSubscriptionId` returns entity on match
- [x] 8.6 `HearingEventSubscriptionRepositoryTest` — `findByIdAndSubscriptionId` returns empty on mismatch
- [x] 8.7 `HearingEventSubscriptionRepositoryTest` — duplicate insert throws `DataIntegrityViolationException`
