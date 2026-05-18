## Context

AMP-504 introduces payload storage and a new retrieval endpoint for PCR events. To allow safe incremental delivery, all new behaviour is gated behind `HEARING_EVENT_JSON_ENABLED`. This design covers only the toggle wiring; subsequent changes will add the actual storage and endpoint logic behind it.

`CallbackDeliveryService` and `NotificationController` currently have no feature-flag injection. The toggle must be wired into both before any gated logic is added so that the two classes are toggle-aware from day one.

## Goals / Non-Goals

**Goals:**
- Add `hearing-event.json.enabled` Spring property backed by `HEARING_EVENT_JSON_ENABLED` env var (default `false`)
- Inject the flag into `CallbackDeliveryService` and `NotificationController` as a `boolean` field

**Non-Goals:**
- Any payload persistence logic (future change)
- The GET endpoint (future change)
- OpenAPI spec changes (future change)

## Decisions

### D1 — `@Value` injection, not a `@ConfigurationProperties` class

The toggle is a single boolean. A dedicated properties class (`NotificationJsonProperties`) would be justified once multiple sub-properties exist. For now, `@Value("${hearing-event.json.enabled:false}")` on the two target classes is simpler and consistent with `subscription.oauth-enabled` and `vault.enabled` which are wired the same way in this codebase.

**Alternative considered**: `@ConditionalOnProperty` on a `@Bean` — rejected because neither class is a `@Bean` definition; both are `@Service` singletons that need the flag at method call time, not at startup.

## Risks / Trade-offs

- [Risk] A future developer skips the toggle guard when adding AMP-504 payload logic → Mitigation: the tasks checklist and PR review template will call out the guard explicitly

## Migration Plan

1. Merge this change — toggle is off by default, no behaviour change in any environment
2. Future changes add payload storage/endpoint logic guarded by `hearingEventJsonEnabled` flag
3. Enable in non-prod environments for testing, then prod when ready
4. No rollback risk — removing the flag injection is a trivial revert

## Open Questions

(none — scope is fully defined)