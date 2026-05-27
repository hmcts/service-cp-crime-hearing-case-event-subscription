## 1. Configuration

- [x] 1.1 Add `hearing-event.json.enabled: ${HEARING_EVENT_JSON_ENABLED:false}` block to `src/main/resources/application.yaml`

## 2. Service Wiring

- [x] 2.1 Inject `@Value("${hearing-event.json.enabled:false}") boolean hearingEventJsonEnabled` into `NotificationManager`; pass as `boolean` parameter to `CallbackDeliveryService.submitOutboundEvents()` (T4: toggle kept in the orchestrator that has no repository dependencies)
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
- [x] 5.3 Create `HearingEventSubscriptionRepository extends JpaRepository<HearingEventSubscriptionEntity, UUID>` with `existsBySubscriptionIdAndHearingEventId`
- [x] 5.4 Create `HearingEventPayloadService` — `UUID saveIfAbsent(EventPayload)` (null-checks `eventId`; persists entity and returns generated `hearingEventId`; DB `UNIQUE` constraint on `event_id` enforces idempotency at storage level) + `saveSubscriptionIfAbsent(UUID, UUID)` (guarded by `existsBySubscriptionIdAndHearingEventId`)

## 6. Processing Flow

- [x] 6.1 Inject `HearingEventPayloadService` into `CallbackDeliveryService`
- [x] 6.2 In `submitOutboundEvents(eventPayload, documentId, hearingEventJsonEnabled)`: when toggle `true`, call `hearingEventPayloadService.saveIfAbsent(eventPayload)` and capture returned `hearingEventId`
- [x] 6.3 In `submitOutboundEvents()`: persist `HearingEventSubscriptionEntity` per client when toggle on and `hearingEventId != null`, guarded by `existsBySubscriptionIdAndHearingEventId` check inside `saveSubscriptionIfAbsent`

## 7. Unit Tests

- [x] 7.1 Add V1.013 and V1.014 checksums to `FlywayMigrationIntegrityTest`
- [x] 7.2 Add new-table deletions to `IntegrationTestBase.clearAllTables()`
- [x] 7.3 `HearingEventPayloadServiceTest` — `saveIfAbsent()` happy path
- [x] 7.4 `HearingEventPayloadServiceTest` — `saveIfAbsent()` unknown event type throws
- [x] 7.5 `HearingEventPayloadServiceTest` — `saveSubscriptionIfAbsent()` persists supplied values
- [x] 7.6 `HearingEventPayloadServiceTest` — `saveSubscriptionIfAbsent()` skips when row already exists
- [x] 7.7 `CallbackDeliveryServiceTest` — toggle off (`false` parameter): no persistence calls
- [x] 7.8 `CallbackDeliveryServiceTest` — toggle on (`true` parameter), `saveIfAbsent()` called
- [x] 7.9 `CallbackDeliveryServiceTest` — toggle on, `saveIfAbsent()` returns null (re-delivery): `saveSubscriptionIfAbsent()` never called
- [x] 7.10 `CallbackDeliveryServiceTest` — toggle on, subscription absent: `saveSubscriptionIfAbsent()` called
- [x] 7.11 `CallbackDeliveryServiceTest` — toggle on, subscription present: `saveSubscriptionIfAbsent()` skips
- [x] 7.12 `HearingEventPayloadMapperTest` — `toEntity` maps all fields; `toSubscriptionEntity` maps all fields
- [x] 7.13 `NotificationManagerTest` — `processNotification` passes `hearingEventJsonEnabled` (default `false`) to `submitOutboundEvents`

## 8. Integration Tests

- [x] 8.1 `HearingEventPayloadRepositoryTest` — saved entity has service-generated `hearingEventId` (not null) and `eventId` set correctly
- [x] 8.2 `HearingEventSubscriptionRepositoryTest` — `existsBySubscriptionIdAndHearingEventId` returns true for existing pair
- [x] 8.3 `HearingEventSubscriptionRepositoryTest` — `existsBySubscriptionIdAndHearingEventId` returns false for unknown pair
- [x] 8.4 `HearingEventSubscriptionRepositoryTest` — duplicate insert throws `DataIntegrityViolationException`