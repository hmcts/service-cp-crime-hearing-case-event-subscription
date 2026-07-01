# Investigation: weekend getDocument throttling storm (June 2026)

## TL;DR
On weekends, APIM shows **100k–300k+ HTTP 429 (throttled)** requests/day for HRDS, vs a
quiet **~5–10k/day** mid-week. Root cause:

**The Material service is unavailable at weekends → `getDocument` returns 503 → a subscriber
re-fetches documents in a tight loop with no backoff → the retries blow past the APIM
per-minute rate limit → a wall of 429s.**

The HRDS ingest and normal retrieval paths are healthy. On weekdays (Material up) every
document is fetched ~2× (once per subscriber) with **zero** 503s. The storm is entirely
weekend-specific and driven by (a) Material weekend outages and (b) one misbehaving
subscriber.

## Symptom
APIM `requests` (App Insights, gateway role `sp-prd-apim-int`) — HRDS 429s by day:

| Day | 200 | 429 | 500 |
|---|---|---|---|
| Wed 24 Jun | 4,743 | 0 | 0 |
| Thu 25 Jun | 6,892 | 31 | 0 |
| Fri 26 Jun | 9,857 | 108 | 0 |
| **Sat 27 Jun** | 7,620 | **159,144** | 1,207 |
| **Sun 28 Jun** | 4,108 | **315,010** | 2,658 |
| **Mon 29 Jun** | 10,837 | **93,950** | 0 |
| Tue 30 Jun | 7,248 | 10 | 0 |
| Wed 01 Jul | 2,492 | 7 | 0 |

- **200 (success) is flat ~5–11k every day** — that's the rate-limit ceiling; the limit is a
  short-window (per-minute) `rate-limit-by-key`, not a daily quota (successes trickle all day
  rather than front-loading then stopping).
- **429 explodes Sat–Mon**; Monday is the storm **spilling over** (the client kept hammering
  into Monday morning until it cleared).
- Mid-week 429s are single/double/triple digits — normal rate-limit clipping, harmless.

## Root cause (evidenced end-to-end)

### 1. Failures are Material 503s at the metadata step
Across the weekend backend log, **all 2,657 ERROR lines are `503`**, and **2,647 are on the
Material `/metadata` call, 0 on the document/blob**. The 503 body is *"no healthy upstream"* —
i.e. the Material service (`material-query-api`) was down in weekend windows.

### 2. Over-fetching amplifies it (weekend vs weekday)
Per-document join (documentId links ingest ↔ every retrieval):

| Metric | Weekday (Mon→Tue) | Weekend (Sat→Mon) |
|---|---|---|
| Documents fetched | 6,106 | 735 |
| getDocument attempts | 11,973 | 10,592 |
| **Avg fetches / document** | **2.0** | **~14** |
| **503 errors** | **0** | 2,657 |
| Documents with ≥1 503 | 0 (0%) | 675 (92%) |

Weekday normal = ~2 fetches/doc (one per subscriber), zero failures. Weekend = the same docs
re-fetched ~14× because Material was down and the client kept retrying.

### 3. It's one subscriber, and it's preproduction
Two subscribers are notified per event. Example material `caa47979` (weekend):

| Subscriber | clientSubscriptionId | Endpoint | Env | Behaviour |
|---|---|---|---|---|
| A | `2c8cdf6e-…-8e54b324d35f` | `hxpwmnx4ef…/preproduction/ingest` | **preprod** | fetched the doc **18×** Sat→Mon incl. a 3-in-¼-second retry burst |
| B | `e04b3344-…-ea7181468355` | `czbsgqfdwd…/production/ingest` | **prod** | fetched **once**, done |

(preprod↔prod inferred from callback send-time vs first-fetch time: subscriber A fetched
*before production was even notified*. Confirm via the subscription registry.)

On a **weekday**, the same preprod subscriber fetched once and stopped — so the loop is
**weekend-specific**, not its normal behaviour.

## The chain
```
Material service down (weekend)
   → getDocument -> Material /metadata returns 503
   → preprod subscriber re-fetches the same document, no backoff, tight loop
   → retries exceed the APIM per-minute rate limit
   → 100k–300k 429s/day (storm), spilling into Monday until it clears
```

## Recommended actions
1. **Platform:** fix the **weekend Material (`material-query-api`) availability** — it's the trigger.
2. **Consumer (`2c8cdf6e-…` / preproduction):** add **backoff + a retry cap**, and don't
   re-fetch a document that was already retrieved successfully.
3. **HRDS (nice-to-have):** surface a Material 503 as a clear retryable error / `Retry-After`,
   and return **404** (not 500/loopable) for genuinely missing/purged documents.

## How to reproduce / monitor
- APIM (App Insights) queries: see this folder — `result-codes-by-day`, `throttled-429-by-day`,
  `throttled-429-by-subscription`, `subscription-by-operation`, `subscriptions-in-use`.
- Backend log analysis (per-document attempts vs 503s, per-material transaction table by
  correlation-id / subscription): run over the exported logs `prod-weekend-log.csv` /
  `prod-weekday-log.csv` (columns: `TimeGenerated, Level, CorrId, Message`).

## Data sources
- **APIM:** Application Insights `requests` (gateway role `sp-prd-apim-int`, UK South).
- **Backend:** `ContainerLogV2` (pod `hearing-results-document-subscription`), exported to
  `prod-weekend-log.csv` (Sat 27 → Mon 29 Jun) and `prod-weekday-log.csv` (Mon 29 → Tue 30 Jun).
- Join key across APIM ↔ backend: **documentId** (in the APIM request URL and the backend logs).
