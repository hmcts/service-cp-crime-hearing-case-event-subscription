## ADDED Requirements

### Requirement: Toggle property configured in application.yaml
The service SHALL declare `notification.json.enabled` as a Spring property in `application.yaml`, defaulting to `false` via the `NOTIFICATION_JSON_ENABLED` environment variable.

#### Scenario: Default is off when env var absent
- **WHEN** `NOTIFICATION_JSON_ENABLED` is not set in the environment
- **THEN** `notification.json.enabled` resolves to `false`

#### Scenario: Env var enables the toggle
- **WHEN** `NOTIFICATION_JSON_ENABLED=true` is set in the environment
- **THEN** `notification.json.enabled` resolves to `true`

---

### Requirement: Toggle injected into CallbackDeliveryService
`CallbackDeliveryService` SHALL receive `notification.json.enabled` as an injected `boolean` field so future AMP-504 logic can be guarded by it.

#### Scenario: Field present when feature off
- **WHEN** `notification.json.enabled=false`
- **THEN** `CallbackDeliveryService` is instantiated with `notificationJsonEnabled = false` and existing outbound dispatch behaviour is unchanged

#### Scenario: Field present when feature on
- **WHEN** `notification.json.enabled=true`
- **THEN** `CallbackDeliveryService` is instantiated with `notificationJsonEnabled = true`

---

### Requirement: Toggle injected into NotificationController
`NotificationController` SHALL receive `notification.json.enabled` as an injected `boolean` field so the future GET endpoint can be gated by it.

#### Scenario: Controller available with feature off
- **WHEN** `notification.json.enabled=false`
- **THEN** `NotificationController` is instantiated with `notificationJsonEnabled = false` and existing POST endpoint behaviour is unchanged

---

### Requirement: NOTIFICATION_JSON_ENABLED documented in .envrc.example
The `.envrc.example` file SHALL include an entry for `NOTIFICATION_JSON_ENABLED` with value `false`.

#### Scenario: Developer copies .envrc.example
- **WHEN** a developer copies `.envrc.example` to `.envrc`
- **THEN** `NOTIFICATION_JSON_ENABLED` is present and set to `false` by default