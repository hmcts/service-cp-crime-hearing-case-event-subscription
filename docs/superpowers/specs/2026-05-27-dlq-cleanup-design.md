# AMP-530: DLQ Cleanup Design

**Date:** 2026-05-27  
**Branch:** feature/AMP-530-clear-stale-dlq-messages  
**PR:** #270

---

## Background

Stale and undeliverable messages have accumulated in the Azure Service Bus dead-letter queues (DLQ) across environments:

- **DEV/SIT:** Messages that errored before the retry count was increased. Safe to delete. Require ongoing purge at every startup to keep environments clean.
- **PRD/PRP:** Messages from May 2026 that went to DLQ due to subscription changes. Confirmed safe to delete (inbound DLQ = 0, outbound DLQ = 2 messages dated 05/05 and 21/05). One-time purge only. No automated DLQ deletion expected after this.

The original PR #270 used a Flyway Java migration (`V1_015__ClearStaleDlqMessages`) to perform the cleanup. This approach is being replaced because:

- Flyway tracks the migration in `flyway_schema_history` — it runs exactly once and cannot re-run, which breaks the DEV/SIT requirement of purging on every startup
- It mixes Service Bus operations into DB schema migration — wrong separation of concerns
- No environment differentiation — applies the same 7-day threshold everywhere
- Only covered the inbound queue

---

## Queues in Scope

Both queues for all environments:

- `hrds.notifications.inbound`
- `hrds.notifications.outbound`

---

## Design

### Environment Behaviour

| Environment | Behaviour | Trigger |
|---|---|---|
| DEVELOPER | Purge all DLQ messages, both queues, every startup | `EnvironmentName` |
| DEV | Purge all DLQ messages, both queues, every startup | `EnvironmentName` |
| SIT | Purge all DLQ messages, both queues, every startup | `EnvironmentName` |
| PRP | Purge May 2026 DLQ messages only, both queues, on startup | `EnvironmentName` |
| PRD | Purge May 2026 DLQ messages only, both queues, on startup | `EnvironmentName` |

No env vars. No Helm changes. Behaviour is determined solely by `EnvironmentName` already present in `AppProperties`.

### One-Time Nature for PRD/PRP

The PRD/PRP purge is naturally idempotent — after the first startup the May 2026 messages are gone. Subsequent startups run the same date-range query, find nothing, and return immediately. No self-limiting code, audit table, or operator action needed. The code itself is removed in the next PR after the May 2026 cleanup is validated.

### Multi-Pod Safety (PRD/PRP)

PRD/PRP run multiple pods. All pods will attempt the purge simultaneously at startup. This is safe: Azure Service Bus `PEEK_LOCK` semantics lock each message to the receiving pod. A message locked by Pod A cannot be received by Pod B. Concurrent pods share the purge work without duplication.

### May 2026 Date Range

Hardcoded in `PostStartup.java` as constants:

```java
private static final OffsetDateTime MAY_2026_START = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
private static final OffsetDateTime MAY_2026_END   = OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
```

Messages are purged where `enqueuedTime >= MAY_2026_START && enqueuedTime < MAY_2026_END`.

---

## Code Changes

No new files. No Helm changes. No `application.yaml` changes (beyond the revert below). All changes are to files already modified in PR #270 or existing files.

### Delete

- `src/main/java/uk/gov/hmcts/cp/db/migration/V1_015__ClearStaleDlqMessages.java`

### Revert

- `.github/pmd-ruleset.xml` — remove Flyway `ClassNamingConventions` exception
- `src/main/resources/application.yaml` — revert Flyway `locations` to `classpath:db/migration` only

### Keep unchanged

- `ServiceBusClientFactory.java` — `deadLetterReceiverClient()` still needed
- `ServiceBusClientService.clearDeadLetterQueue(queueName, olderThanDays)` — still used for DEV/SIT full purge

### Update: `ServiceBusClientService.java`

Add one overload alongside the existing method:

```java
public int clearDeadLetterQueue(String queueName, OffsetDateTime from, OffsetDateTime to)
```

Same loop and loop-detection logic as the existing method. Predicate changes from `enqueuedTime.isBefore(cutoff)` to `enqueuedTime.isAfter(from) && enqueuedTime.isBefore(to)`. Messages outside the range are abandoned with the sequence-number loop-detection guard.

Existing method and all existing tests remain unchanged.

### Update: `PostStartup.java`

Inject `ServiceBusClientService`. Add `clearDlqIfRequired()` called from `postStartupLogging()`:

```
if environmentName in {DEVELOPER, DEV, SIT}:
    clearDeadLetterQueue(INBOUND_QUEUE,  olderThanDays=0)  // clears all
    clearDeadLetterQueue(OUTBOUND_QUEUE, olderThanDays=0)

else if environmentName in {PRD, PRP}:
    clearDeadLetterQueue(INBOUND_QUEUE,  MAY_2026_START, MAY_2026_END)
    clearDeadLetterQueue(OUTBOUND_QUEUE, MAY_2026_START, MAY_2026_END)
```

`olderThanDays=0` → cutoff = `now().minusDays(0)` = now → every enqueued message is before the cutoff → all messages cleared.

No changes to `AppProperties.java` or `application.yaml`.

---

## Testing

### Existing tests — unchanged

All four `clearDeadLetterQueue(queueName, olderThanDays)` unit tests in `ServiceBusClientServiceTest` remain valid and unchanged.

### New tests required

**`ServiceBusClientServiceTest`** — two new unit tests for the date-range overload:
- Message within May range → `complete()` called, count = 1
- Message outside May range → `abandon()` called, count = 0

**`PostStartupTest`** (already exists — add to it):
- Add `@Mock AppProperties appProperties` and `@Mock ServiceBusClientService serviceBusClientService` to the existing test class
- Existing three tests: stub `appProperties.getEnvironmentName()` to return `UNKNOWN` so they continue to pass unchanged
- New test: DEV environment → `clearDeadLetterQueue(queue, 0)` called for both queues
- New test: PRD environment → `clearDeadLetterQueue(queue, MAY_2026_START, MAY_2026_END)` called for both queues
- New test: UNKNOWN environment → `clearDeadLetterQueue` not called at all

---

## Removal

This purge code is temporary. Once the May 2026 cleanup is validated in PRD:

- Delete `clearDlqIfRequired()` from `PostStartup.java`
- Remove `MAY_2026_START` / `MAY_2026_END` constants from `PostStartup.java`
- Remove the `clearDeadLetterQueue(queueName, from, to)` overload from `ServiceBusClientService`
- Remove the corresponding tests