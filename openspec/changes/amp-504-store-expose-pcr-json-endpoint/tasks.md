## 1. Configuration

- [x] 1.1 Add `hearing-event.json.enabled: ${HEARING_EVENT_JSON_ENABLED:false}` block to `src/main/resources/application.yaml`

## 2. Service Wiring

- [x] 2.1 Remove `@RequiredArgsConstructor` from `CallbackDeliveryService`; add explicit constructor with `@Value("${hearing-event.json.enabled:false}") final boolean hearingEventJsonEnabled` as last parameter (T4: toggle stored in the service that controls persistence, never in a repository-owning class)
- [x] 2.2 Add `@Value("${hearing-event.json.enabled:false}") private boolean hearingEventJsonEnabled` field injection in `NotificationController`; gates GET endpoint

## 3. Database Migrations

- [x] 3.1 Create `src/main/resources/db/migration/V1.013__add_hearing_event_payload.sql` — includes `hearing_event_id UUID PRIMARY KEY` (`@GeneratedValue`), `event_id UUID NOT NULL UNIQUE` (inbound event identity), and index on both
- [x] 3.2 Create `src/main/resources/db/migration/V1.014__add_hearing_event_subscriptions.sql`

## 4. JPA Entities

- [x] 4.1 Create `HearingEventPayloadEntity` (table: `hearing_event_payload`, pk: `hearingEventId UUID @GeneratedValue`, add `eventId UUID` field mapping `event_id`, `@JdbcTypeCode(SqlTypes.JSON)` on `rawPayload`)
- [x] 4.2 Create `HearingEventSubscriptionEntity` (table: `hearing_event_subscriptions`, pk: `id UUID @GeneratedValue UUID`)

## 5. Persistence Infrastructure

- [x] 5.1 `@JdbcTypeCode(SqlTypes.JSON)` on `rawPayload` — Hibernate 6 binds as `Types.OTHER`; no separate converter class needed
- [x] 5.2 Create `HearingEventPayloadRepository extends JpaRepository<HearingEventPayloadEntity, UUID>` with `Optional<HearingEventPayloadEntity> findByEventId(UUID eventId)` for application-level idempotency
- [x] 5.3 Create `HearingEventSubscriptionRepository extends JpaRepository<HearingEventSubscriptionEntity, UUID>` with `existsBySubscriptionIdAndHearingEventId` and `Optional<HearingEventSubscriptionEntity> findByIdAndSubscriptionId(UUID id, UUID subscriptionId)` for GET endpoint
- [x] 5.4 Create `HearingEventService` — `UUID saveIfAbsent(EventPayload)` (null-checks `eventId`; calls `findByEventId` first and returns existing `hearingEventId` if found; otherwise persists and returns generated `hearingEventId`) + `saveSubscriptionIfAbsent(UUID, UUID)` (guarded by `existsBySubscriptionIdAndHearingEventId`) + `HearingEventResponse getHearingEvent(UUID, UUID)` (resolves subscription via `findByIdAndSubscriptionId`, builds response via `JsonMapper.toMap`)

## 6. Processing Flow

- [x] 6.1 Inject `HearingEventService` into `CallbackDeliveryService` constructor
- [x] 6.2 In `submitOutboundEvents(eventPayload, documentId)` (two-argument): when `hearingEventJsonEnabled` field is `true`, call `hearingEventService.saveIfAbsent(eventPayload)` and capture returned `hearingEventId`
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
- [x] 7.13 `NotificationManagerTest` — `getHearingEvent` delegates to `HearingEventService.getHearingEvent`

## 8. Integration Tests

- [x] 8.1 `HearingEventPayloadRepositoryTest` — saved entity has service-generated `hearingEventId` (not null) and `eventId` set correctly
- [x] 8.2 `HearingEventSubscriptionRepositoryTest` — `existsBySubscriptionIdAndHearingEventId` returns true for existing pair
- [x] 8.3 `HearingEventSubscriptionRepositoryTest` — `existsBySubscriptionIdAndHearingEventId` returns false for unknown pair
- [x] 8.4 `HearingEventSubscriptionRepositoryTest` — duplicate insert throws `DataIntegrityViolationException`

## 9. API Version Bump

- [x] 9.1 Update `uk.gov.hmcts.cp:api-cp-crime-hearing-results-document-subscription` dependency in `build.gradle` from `2.0.9` to `2.0.10`; `2.0.10` introduces `HearingEventResponse` model and the `getHearingEvent` operation on `NotificationApi`

## 10. GET Endpoint Implementation

- [x] 10.1 Add `Map<String, Object> toMap(Object object)` to `JsonMapper` using Jackson `convertValue` with `TypeReference<Map<String, Object>>`
- [x] 10.2 Add `HearingEventResponse getHearingEvent(UUID subscriptionId, UUID hearingEventId)` to `HearingEventService`: resolves subscription via `findByIdAndSubscriptionId`, fetches payload entity, builds `HearingEventResponse` with consumer-facing `hearingEventId = subscription.getId()`
- [x] 10.3 Add thin delegation method `getHearingEvent(UUID, UUID)` to `NotificationManager` → `hearingEventService.getHearingEvent()`
- [x] 10.4 Implement `NotificationController.getHearingEvent`: returns `404` when `hearingEventJsonEnabled = false`; delegates to `notificationManager.getHearingEvent()` and returns `200 OK` when enabled

## 11. Unit Tests — GET Endpoint

- [x] 11.1 `HearingEventServiceTest` — `getHearingEvent` returns `HearingEventResponse` with correct `hearingEventId`, `eventType`, `createdAt`, `payload` when subscription found
- [x] 11.2 `HearingEventServiceTest` — `getHearingEvent` throws `ResponseStatusException(NOT_FOUND)` when no subscription row exists for `(id, subscriptionId)`
- [x] 11.3 `HearingEventServiceTest` — `saveIfAbsent` skips insert and returns existing `hearingEventId` when `findByEventId` returns a present row
- [x] 11.4 `NotificationManagerTest` — `getHearingEvent` delegates to `hearingEventService.getHearingEvent`