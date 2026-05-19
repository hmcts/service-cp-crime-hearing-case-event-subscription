## Context

AMP-504 introduces payload storage and a new retrieval endpoint for PCR events. To allow safe incremental delivery, all new behaviour is gated behind `HEARING_EVENT_JSON_ENABLED`. This design covers only the toggle wiring; subsequent changes will add the actual storage and endpoint logic behind it.

`CallbackDeliveryService` and `NotificationController` currently have no feature-flag injection. The toggle must be wired into both before any gated logic is added so that the two classes are toggle-aware from day one.

## Goals / Non-Goals

**Goals:**
- Add `hearing-event.json.enabled` Spring property backed by `HEARING_EVENT_JSON_ENABLED` env var (default `false`)
- Inject the flag into `CallbackDeliveryService` and `NotificationController` as a `boolean` field
- Create `hearing_event_payload` and `hearing_event_subscriptions` tables via Flyway
- Persist raw `EventPayload` and per-subscriber subscription rows in `CallbackDeliveryService`, guarded by the toggle
- Idempotency guards on both inserts

**Non-Goals:**
- The GET endpoint (future PR)
- `hearingEventId` on outbound `EventNotificationPayload` (blocked on external API spec library version bump — separate PR)
- Encryption at rest (open decision b in docs — deferred)

## Decisions

### D1 — `@Value` injection, not a `@ConfigurationProperties` class

The toggle is a single boolean. A dedicated properties class would be justified once multiple sub-properties exist. `@Value("${hearing-event.json.enabled:false}")` is consistent with `subscription.oauth-enabled` and `vault.enabled` already in this codebase.

**Alternative considered**: `@ConditionalOnProperty` on a `@Bean` — rejected because neither class is a `@Bean` definition; both are `@Service` singletons needing the flag at method call time.

### D2 — FK modelling: plain fields, not `@ManyToOne`

`eventTypeId: Long` in `HearingEventPayloadEntity` and `hearingEventId: UUID` in `HearingEventSubscriptionEntity` are plain fields matching the pattern used by `ClientEventEntity` and `ClientHmacEntity`. Avoids lazy-loading surprises; JPQL `@Query` handles any joins.

### D3 — `@JdbcTypeCode(SqlTypes.JSON)` on `rawPayload`

`rawPayload` in `HearingEventPayloadEntity` uses `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(columnDefinition = "jsonb")`. Hibernate 6 binds the parameter as `Types.OTHER` which PostgreSQL accepts for `jsonb` columns.

**ObjectMapper divergence (known, accepted)**: Hibernate's JSON type handling uses its own internal `ObjectMapper` instance, not the project's `JsonMapper` bean. `JsonMapper` registers `JavaTimeModule` and serialises `Instant` as ISO-8601 strings; Hibernate's default `ObjectMapper` serialises `Instant` as numeric epoch seconds. If `EventPayload.timestamp` is populated, the stored representation will differ from what `JsonMapper` would produce. Wiring `JsonMapper` into Hibernate via `HibernatePropertiesCustomizer` + `JacksonJsonFormatMapper` would close this gap but is deferred — `EventPayload.timestamp` is not currently populated by inbound PCR events.

**Alternative considered**: `AttributeConverter<EventPayload, String>` — rejected because Hibernate binds the converted `String` as `VARCHAR`, which PostgreSQL rejects for `jsonb` columns without a `@ColumnTransformer(write = "?::jsonb")` workaround.

### D4 — Persistence in `CallbackDeliveryService`, not `NotificationService`

Both rows are created in `CallbackDeliveryService.submitOutboundEvents()` because the subscribed client list is only available there. `HearingEventPayloadService` is the persistence abstraction injected into it.

### D5 — Per-subscriber `hearingEventId` requires per-client payload instance

`hearing_event_subscriptions.id` (the consumer-facing hearingEventId) differs per subscriber. Once the API spec library adds `hearingEventId` to `EventNotificationPayload`, the payload must be constructed per-client (not shared across the loop).

## Risks / Trade-offs

- [Risk] Developer skips toggle guard when adding payload logic → Mitigation: checklist and PR template call it out explicitly
- [Risk] `hearingEventId` on outbound payload blocked on external API spec library bump → Mitigation: task 6.4 marked BLOCKED; persistence tasks 6.1–6.3 proceed independently

## Migration Plan

1. Merge this change — toggle off by default, no behaviour change in any environment
2. Flyway runs V1.013 and V1.014 automatically on startup
3. Enable toggle in non-prod for testing, then prod when ready
4. Rollback: drop the two new tables; remove toggle; revert `CallbackDeliveryService`

### D6 — Test strategy: real PostgreSQL, no H2

Repository tests extend `IntegrationTestBase` (PostgreSQL 15 on port 5433 via docker-compose). H2 is not used anywhere in this codebase. `IntegrationTestBase.clearAllTables()` is extended to delete from `hearing_event_subscriptions` then `hearing_event_payload` (child before parent, per FK constraint). Unit tests for `HearingEventPayloadService` use `@ExtendWith(MockitoExtension.class)`. Toggle-sensitive scenarios in `CallbackDeliveryServiceTest` use `ReflectionTestUtils.setField()` to set the non-constructor `@Value` field.

## Open Questions

- Decision b (encrypt payload at rest) — deferred; an `AttributeConverter` wrapping `@JdbcTypeCode` is the right hook when ready
