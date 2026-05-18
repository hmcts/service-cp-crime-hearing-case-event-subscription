## 1. Configuration

- [x] 1.1 Add `notification.json.enabled: ${NOTIFICATION_JSON_ENABLED:false}` block to `src/main/resources/application.yaml`
- [x] 1.2 Add `export NOTIFICATION_JSON_ENABLED=false` to `.envrc.example`

## 2. Service Wiring

- [x] 2.1 Inject `@Value("${notification.json.enabled:false}") boolean notificationJsonEnabled` into `CallbackDeliveryService`
- [x] 2.2 Inject `@Value("${notification.json.enabled:false}") boolean notificationJsonEnabled` into `NotificationController`
