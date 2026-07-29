# Audit Rollout — Production Deployment Phases

This service uses `cp-audit-springboot-annotations` to emit structured audit events to an Artemis/Service Bus broker.
The rollout to production is split into three phases to minimise risk of breaking existing functionality.

---

## Phase 1 — Subscription endpoints only, non-blocking

**What changes**
- Audit enabled on subscription CRUD endpoints (`/client-subscriptions/**`).
- `getDocument` endpoint excluded from audit.
- `block-on-failure=false` — if the Artemis broker is unreachable, the audit event is silently dropped and the request succeeds.

**Why**
- Subscription endpoints are low-volume; any audit noise is easy to monitor.
- Non-blocking means a broken broker cannot take down the API.
- Safe to deploy immediately without any dependency on Artemis availability.

**MDC context**
- `user` and `materialId` are not set — both fields appear as `null` in the audit event.

---

## Phase 2 — Extend audit to `getDocument`, set `materialId` and `user` in MDC

**What changes**
- Audit extended to `getDocument` (`/documents/{documentId}/content`) — this is a high-volume endpoint.
- `DocumentService.getDocumentContent()` sets two MDC keys after resolving the materialId from the database:
  - `AuditMdcKeys.MATERIAL_ID` — the UUID of the material resource fetched from the material service.
  - `AuditMdcKeys.USER_ID` — the `cjscppuid` value (the system service account UUID sent to the material service).
- `block-on-failure=false` — still non-blocking.

**Why**
- `getDocument` is the most security-sensitive endpoint (serves document bytes); its audit trail is essential.
- Setting MDC in the service layer (not a filter) means the values are available to the `AuditFilter` when it builds the RESPONSE event after `chain.doFilter()` returns.
- Keeping non-blocking ensures a broker outage during a high-volume period cannot cascade into document serving failures.

---

## Phase 3 — Full enforcement (`block-on-failure=true`)

**What changes**
- `block-on-failure` set to `true`.
- A failure to emit an audit event now causes the request to fail with a 500 error.

**Why**
- Full audit compliance: no document access or subscription change goes unrecorded.
- Only safe to enable once Artemis availability and audit pipeline reliability are proven over Phases 1 and 2.
- Monitor broker health and audit consumer lag before enabling — rollback plan is to revert to `block-on-failure=false`.

**Config**
```yaml
cp:
  audit:
    enabled: true
    block-on-failure: true
```

---

## Summary

| Phase | Endpoints audited             | block-on-failure | materialId / user in MDC     |
|-------|-------------------------------|------------------|------------------------------|
| 1     | Subscription only             | false            | No — both null               |
| 2     | Subscription + getDocument    | false            | Yes — set in DocumentService |
| 3     | Subscription + getDocument    | true             | Yes — set in DocumentService |
