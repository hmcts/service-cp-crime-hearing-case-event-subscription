## Context

AMP-504 introduces payload storage and a GET retrieval endpoint for PCR events. All new behaviour is gated behind `HEARING_EVENT_JSON_ENABLED`. The toggle is constructor-injected into `CallbackDeliveryService` (which orchestrates persistence) and field-injected into `NotificationController` (which gates the GET endpoint). `NotificationManager` remains toggle-blind — it delegates unconditionally to `HearingEventService`; the controller decides whether to call it at all.

## Goals / Non-Goals

**Goals:**
- Add `hearing-event.json.enabled` Spring property backed by `HEARING_EVENT_JSON_ENABLED` env var (default `false`)
- Constructor-inject the toggle into `CallbackDeliveryService`; field-inject into `NotificationController` to gate the GET endpoint
- Bump `api-cp-crime-hearing-results-document-subscription` to `2.0.10` for `HearingEventResponse` and `getHearingEvent` operation
- Create `hearing_event_payload` and `hearing_event_subscriptions` tables via Flyway
- Persist raw `EventPayload` and per-subscriber subscription rows in `CallbackDeliveryService`, guarded by the toggle; application-level idempotency via `findByEventId` (DB UNIQUE constraint as safety net)
- Serve `GET /client-subscriptions/{id}/hearing-events/{hearingEventId}` via `HearingEventService.getHearingEvent`; returns 404 when toggle off

**Non-Goals:**
- `hearingEventId` on outbound `EventNotificationPayload` (blocked on external API spec library — separate PR)
- Encryption at rest (open decision b in docs — deferred)

## Decisions

### D1 — `@Value` injection, not a `@ConfigurationProperties` class

The toggle is a single boolean. A dedicated properties class would be justified once multiple sub-properties exist. `@Value("${hearing-event.json.enabled:false}")` is consistent with `subscription.oauth-enabled` and `vault.enabled` already in this codebase.

**Alternative considered**: `@ConditionalOnProperty` on a `@Bean` — rejected because neither class is a `@Bean` definition; both are `@Service` singletons needing the flag at method call time.

### D2 — FK modelling: plain fields, not `@ManyToOne`; two-identity design for `hearing_event_payload`

`eventTypeId: Long` in `HearingEventPayloadEntity` and `hearingEventId: UUID` in `HearingEventSubscriptionEntity` are plain fields matching the pattern used by `ClientEventEntity` and `ClientHmacEntity`. Avoids lazy-loading surprises; JPQL `@Query` handles any joins.

`HearingEventPayloadEntity` separates two identities:
- `hearingEventId (UUID)` — `@Id @GeneratedValue(strategy = GenerationType.UUID)`: service-internal PK, FK target in `hearing_event_subscriptions`. Never supplied by the caller; Hibernate generates it on insert.
- `eventId (UUID)` — plain field, maps to `event_id UUID NOT NULL UNIQUE`: the external inbound identity from `EventPayload.getEventId()`. The `UNIQUE` constraint on `event_id` enforces idempotency at the DB level. This is what callers supply; `hearingEventId` is what consumers receive.

`saveIfAbsent` calls `findByEventId` first (application-level idempotency); if a row exists, it returns the existing `hearingEventId` immediately. Only on absence does it persist and return the generated `hearingEventId`. The DB `UNIQUE` constraint on `event_id` remains as a safety net against races.

`HearingEventSubscriptionRepository` provides `findByIdAndSubscriptionId(UUID id, UUID subscriptionId)` for the GET endpoint. The `id` PK of `hearing_event_subscriptions` is the consumer-facing `hearingEventId` returned in `HearingEventResponse` — distinct from the `hearingEventId` FK pointing to the payload.

### D3 — `@JdbcTypeCode(SqlTypes.JSON)` on `rawPayload`

`rawPayload` in `HearingEventPayloadEntity` uses `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(columnDefinition = "jsonb")`. Hibernate 6 binds the parameter as `Types.OTHER` which PostgreSQL accepts for `jsonb` columns.

**ObjectMapper divergence (known, accepted)**: Hibernate's JSON type handling uses its own internal `ObjectMapper` instance, not the project's `JsonMapper` bean. `JsonMapper` registers `JavaTimeModule` and serialises `Instant` as ISO-8601 strings; Hibernate's default `ObjectMapper` serialises `Instant` as numeric epoch seconds. If `EventPayload.timestamp` is populated, the stored representation will differ from what `JsonMapper` would produce. Wiring `JsonMapper` into Hibernate via `HibernatePropertiesCustomizer` + `JacksonJsonFormatMapper` would close this gap but is deferred — `EventPayload.timestamp` is not currently populated by inbound PCR events.

**Alternative considered**: `AttributeConverter<EventPayload, String>` — rejected because Hibernate binds the converted `String` as `VARCHAR`, which PostgreSQL rejects for `jsonb` columns without a `@ColumnTransformer(write = "?::jsonb")` workaround.

### D4 — Persistence logic in `CallbackDeliveryService`; toggle constructor-injected

Both rows are created in `CallbackDeliveryService.submitOutboundEvents(eventPayload, documentId)` (two-argument) because the subscribed client list is only available there. `HearingEventService` is the persistence abstraction injected into it.

`CallbackDeliveryService` holds `hearingEventJsonEnabled` as a `final boolean` constructor parameter annotated `@Value("${hearing-event.json.enabled:false}")`. `@RequiredArgsConstructor` is replaced with an explicit constructor. This satisfies T4 because `CallbackDeliveryService` does not own a repository directly — it delegates to `HearingEventService`. Holding the toggle field here avoids passing it through `NotificationManager` as a method argument, which would have leaked toggle knowledge into a toggle-blind orchestrator.

`NotificationController` receives the toggle via `@Value` field injection to gate the GET endpoint. `NotificationManager` and `HearingEventService` have no toggle awareness.

### D5 — Per-subscriber `hearingEventId` requires per-client payload instance

`hearing_event_subscriptions.id` (the consumer-facing hearingEventId) differs per subscriber. Once the API spec library adds `hearingEventId` to `EventNotificationPayload`, the payload must be constructed per-client (not shared across the loop).

## Risks / Trade-offs

- [Risk] Developer skips toggle guard when adding payload logic → Mitigation: checklist and PR template call it out explicitly
- [Risk] `hearingEventId` on outbound `EventNotificationPayload` blocked on external API spec library bump → separate PR; persistence and GET endpoint proceed independently

## Migration Plan

1. Merge this change — toggle off by default, no behaviour change in any environment
2. Flyway runs V1.013 and V1.014 automatically on startup
3. Enable toggle in non-prod for testing: payload rows are stored and GET endpoint becomes live
4. Enable toggle in prod when ready
5. Rollback: drop the two new tables; remove toggle; revert `CallbackDeliveryService` and `NotificationController`

### D6 — Test strategy: real PostgreSQL, no H2

Repository tests extend `IntegrationTestBase` (PostgreSQL 15 on port 5433 via docker-compose). H2 is not used anywhere in this codebase. `IntegrationTestBase.clearAllTables()` is extended to delete from `hearing_event_subscriptions` then `hearing_event_payload` (child before parent, per FK constraint). Unit tests for `HearingEventService` use `@ExtendWith(MockitoExtension.class)`. Toggle-sensitive scenarios in `CallbackDeliveryServiceTest` construct two service instances in `@BeforeEach` — one with `hearingEventJsonEnabled=false`, one with `true` — using the explicit constructor directly. `ReflectionTestUtils.setField()` is not used.

### D7 — GET endpoint: access control via subscription row lookup

`GET /client-subscriptions/{clientSubscriptionId}/hearing-events/{hearingEventId}` is served by `NotificationController → NotificationManager → HearingEventService.getHearingEvent`. Access control is implicit: `findByIdAndSubscriptionId(hearingEventId, subscriptionId)` returns empty if the `hearingEventId` PK belongs to a different subscriber, yielding a `404`. No separate authorisation check is needed — the row join enforces ownership.

`HearingEventResponse.hearingEventId` is the `id` PK of `hearing_event_subscriptions` (the consumer-facing identity), not the `hearingEventId` FK pointing to the payload table. The payload is serialised to `Map<String, Object>` via `JsonMapper.toMap` (Jackson `convertValue`) for the `payload` field.

**Alternative considered**: expose `HearingEventPayloadEntity.hearingEventId` directly as the consumer ID — rejected because it would allow cross-subscriber access by anyone who guesses a `hearingEventId`; the subscription-row PK is per-subscriber and not predictable.

## Open Questions

- Decision b (encrypt payload at rest) — deferred; an `AttributeConverter` wrapping `@JdbcTypeCode` is the right hook when ready
