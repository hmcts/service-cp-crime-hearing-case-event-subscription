## ADDED Requirements

### Requirement: Toggle property configured in application.yaml
The service SHALL declare `hearing-event.json.enabled` as a Spring property in `application.yaml`, defaulting to `false` via the `HEARING_EVENT_JSON_ENABLED` environment variable.

#### Scenario: Default is off when env var absent
- **WHEN** `HEARING_EVENT_JSON_ENABLED` is not set in the environment
- **THEN** `hearing-event.json.enabled` resolves to `false`

#### Scenario: Env var enables the toggle
- **WHEN** `HEARING_EVENT_JSON_ENABLED=true` is set in the environment
- **THEN** `hearing-event.json.enabled` resolves to `true`
- 
---

### Requirement: Toggle injected into NotificationManager; passed to CallbackDeliveryService
`NotificationManager` SHALL receive `hearing-event.json.enabled` as an injected `boolean` field and pass it as a `boolean` parameter to `CallbackDeliveryService.submitOutboundEvents(eventPayload, documentId, hearingEventJsonEnabled)`. This keeps `CallbackDeliveryService` (which owns repositories) toggle-blind at the field level.

#### Scenario: Toggle off — no persistence calls
- **WHEN** `hearing-event.json.enabled=false`
- **THEN** `NotificationManager` passes `false` to `submitOutboundEvents`; `CallbackDeliveryService` makes no persistence calls and existing outbound dispatch behaviour is unchanged

#### Scenario: Toggle on — persistence calls made
- **WHEN** `hearing-event.json.enabled=true`
- **THEN** `NotificationManager` passes `true` to `submitOutboundEvents`; `CallbackDeliveryService` invokes `HearingEventPayloadService` to persist payload and subscription rows

---

### Requirement: Toggle injected into NotificationController
`NotificationController` SHALL receive `hearing-event.json.enabled` as an injected `boolean` field so the future GET endpoint can be gated by it.

#### Scenario: Controller available with feature off
- **WHEN** `hearing-event.json.enabled=false`
- **THEN** `NotificationController` is instantiated with `hearingEventJsonEnabled = false` and existing POST endpoint behaviour is unchanged

---
