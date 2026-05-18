## 1. Configuration

- [x] 1.1 Add `hearing-event.json.enabled: ${HEARING_EVENT_JSON_ENABLED:false}` block to `src/main/resources/application.yaml`
- [x] 1.2 Add `export HEARING_EVENT_JSON_ENABLED=false` to `.envrc.example`

## 2. Service Wiring

- [x] 2.1 Inject `@Value("${hearing-event.json.enabled:false}") boolean hearingEventJsonEnabled` into `CallbackDeliveryService`
- [x] 2.2 Inject `@Value("${hearing-event.json.enabled:false}") boolean hearingEventJsonEnabled` into `NotificationController`
