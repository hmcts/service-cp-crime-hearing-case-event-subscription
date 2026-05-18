## Why

The full AMP-504 feature (persisting raw PCR payloads and exposing them via a new endpoint) needs to be delivered incrementally behind a flag. This change introduces only the `HEARING_EVENT_JSON_ENABLED` feature toggle so the gate is in place before any payload storage or endpoint logic is added.

## What Changes

- Add `hearing-event.json.enabled` property (backed by `HEARING_EVENT_JSON_ENABLED` env var, default `false`) to `application.yaml`
- Wire the toggle into `CallbackDeliveryService` and `NotificationController` so both are toggle-aware from the start

## Capabilities

### New Capabilities

- `hearing-event-json-feature-toggle`: `HEARING_EVENT_JSON_ENABLED` property wired into `CallbackDeliveryService` and `NotificationController`; when `false` (default) all new AMP-504 behaviour is suppressed — no payload storage, no `hearingEventId` on outbound payloads, GET endpoint returns 404

### Modified Capabilities

(none — no existing requirement changes)

## Impact

- **application.yaml**: new property block
- **CallbackDeliveryService**: injected boolean flag, no logic change yet
- **NotificationController**: injected boolean flag, no logic change yet