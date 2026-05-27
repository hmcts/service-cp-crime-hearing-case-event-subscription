## ADDED Requirements

### Requirement: Toggle property configured in application.yaml
The service SHALL declare `hearing-event.json.enabled` as a Spring property in `application.yaml`, defaulting to `false` via the `HEARING_EVENT_JSON_ENABLED` environment variable.

#### Scenario: Default is off when env var absent
- **WHEN** `HEARING_EVENT_JSON_ENABLED` is not set in the environment
- **THEN** `hearing-event.json.enabled` resolves to `false`

#### Scenario: Env var enables the toggle
- **WHEN** `HEARING_EVENT_JSON_ENABLED=true` is set in the environment
- **THEN** `hearing-event.json.enabled` resolves to `true`

---

### Requirement: Toggle injected via constructor into CallbackDeliveryService
`CallbackDeliveryService` SHALL receive `hearing-event.json.enabled` as a `final boolean hearingEventJsonEnabled` field, injected via an explicit constructor with `@Value("${hearing-event.json.enabled:false}")` on the boolean parameter. The field is `final`; `@RequiredArgsConstructor` is replaced with the explicit constructor. `submitOutboundEvents(EventPayload, UUID)` remains a two-argument method; the toggle is read from the field directly inside the method.

#### Scenario: Toggle off — no persistence calls
- **WHEN** `hearing-event.json.enabled=false`
- **THEN** `CallbackDeliveryService` makes no persistence calls and existing outbound dispatch behaviour is unchanged

#### Scenario: Toggle on — persistence calls made
- **WHEN** `hearing-event.json.enabled=true`
- **THEN** `CallbackDeliveryService` invokes `HearingEventService` to persist payload and subscription rows

#### Scenario: Unit test controls toggle via constructor
- **WHEN** `CallbackDeliveryServiceTest` constructs the service with `hearingEventJsonEnabled=false` or `true`
- **THEN** toggle-off and toggle-on paths are exercised without `ReflectionTestUtils` or `@SpringBootTest`

---

### Requirement: Toggle injected into NotificationController; gates GET endpoint
`NotificationController` SHALL receive `hearing-event.json.enabled` as an injected `boolean` field. The existing POST endpoint is unaffected. The GET endpoint `getHearingEvent` returns `404 Not Found` when the toggle is `false`; it delegates to `NotificationManager.getHearingEvent()` when `true`.

#### Scenario: GET returns 404 when feature off
- **WHEN** `hearing-event.json.enabled=false`
- **THEN** `GET /client-subscriptions/{id}/hearing-events/{hearingEventId}` returns `404`

#### Scenario: GET delegates when feature on
- **WHEN** `hearing-event.json.enabled=true`
- **THEN** `NotificationController` delegates to `NotificationManager.getHearingEvent()` and returns `200 OK`

---