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

### Requirement: Toggle injected into CallbackDeliveryService
`CallbackDeliveryService` SHALL receive `hearing-event.json.enabled` as an injected `boolean` field so future AMP-504 logic can be guarded by it.

#### Scenario: Field present when feature off
- **WHEN** `hearing-event.json.enabled=false`
- **THEN** `CallbackDeliveryService` is instantiated with `hearingEventJsonEnabled = false` and existing outbound dispatch behaviour is unchanged

#### Scenario: Field present when feature on
- **WHEN** `hearing-event.json.enabled=true`
- **THEN** `CallbackDeliveryService` is instantiated with `hearingEventJsonEnabled = true`

---

### Requirement: Toggle injected into NotificationController
`NotificationController` SHALL receive `hearing-event.json.enabled` as an injected `boolean` field so the future GET endpoint can be gated by it.

#### Scenario: Controller available with feature off
- **WHEN** `hearing-event.json.enabled=false`
- **THEN** `NotificationController` is instantiated with `hearingEventJsonEnabled = false` and existing POST endpoint behaviour is unchanged

---
