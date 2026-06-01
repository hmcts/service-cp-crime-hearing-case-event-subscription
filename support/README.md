# Support Queries

KQL queries for the `hearing-results-document-subscription` service.

- Paste into Log Analytics for ad-hoc investigation.
- Source of truth for dashboard panels in the Terraform sister repo.

| Folder | Use |
|---|---|
| [`logs-kql/`](logs-kql/) | Dev/int — queries `ContainerLogV2` directly using `PodName` |
| [`kql-prod/`](kql-prod/) | Prod — joins `KubePodInventory` with `ContainerLog` (until `ContainerLogV2` rolls out to prod) |
| [`chart-kql/`](chart-kql/) | Dashboard tile queries — source of truth for [`cp-amp-terraform-az-dashboard`](https://github.com/hmcts/cp-amp-terraform-az-dashboard) |
| [`alerts-kql/`](alerts-kql/) | Alert threshold queries — source of truth for [`cp-amp-terraform-alerts`](https://github.com/hmcts/cp-amp-terraform-alerts) |

---

## Running Queries via Azure CLI

**Prerequisites:** `az` CLI and `jq` installed.

```bash
brew install jq                              # if not already installed
az extension add --name log-analytics        # one-time
az login
```

**Set your workspace ID** (find it: Azure portal > your workspace > Overview > Workspace ID):
```bash
export WORKSPACE_ID=<your-workspace-guid>
```

Or add shell functions to `~/.zshrc` for quick switching:
```bash
kqlsit() { export WORKSPACE_ID=<sit-workspace-guid>; }
kqlprd() { export WORKSPACE_ID=<prd-workspace-guid>; }
```

**Run a query:**
```bash
cd support

./run-query.sh logs-kql/logs-all.kql P1D
./run-query.sh logs-kql/errors-all.kql PT6H
./run-query.sh logs-kql/trace-event-id.kql P1D 85ae7d8d-8784-44e0-aac5-465a6c5baf49
```

**List available queries:**
```bash
cd support
./run-query.sh
```

---

## Query Index

### logs-kql

| Query | Description |
|---|---|
| `logs-all.kql` | All logs for the service |
| `logs-inbound-notifications.kql` | All inbound notification processing logs |
| `errors-all.kql` | ERROR level only |
| `errors-inbound.kql` | Inbound processing errors |
| `errors-outbound.kql` | Outbound callback errors |
| `inbound-counts-by-day.kql` | Count of inbound notifications per day |
| `dlq-failures-by-day.kql` | FAILED FINALLY count per day |
| `dlq-failures-by-queue-by-day.kql` | FAILED FINALLY split by queue per day |
| `startup-queue-check.kql` | Queue state logged on pod startup |
| `trace-correlation.kql` | Full journey by correlation ID |
| `trace-event-id.kql` | Full journey by eventId |

### chart-kql

> **Source of truth** for dashboard tile queries deployed via [`cp-amp-terraform-az-dashboard`](https://github.com/hmcts/cp-amp-terraform-az-dashboard).
>
> After changing a query here:
> 1. Run `./sync-dashboard-to-terraform.sh` to copy files to the Terraform repo
> 2. Commit, push and raise a PR in `cp-amp-terraform-az-dashboard`
> 3. Once merged, run the Terraform pipeline in that repo to apply the changes to Azure

| Query | Description |
|---|---|
| `counts-by-message-type-weekly.kql` | Inbound/outbound/getDocument/failures pivot by week |
| `received-notifications-by-day.kql` | Received notifications trend — 84 day line chart |
| `received-notifications-by-hour.kql` | Received notifications trend — 1 day column chart |
| `get-documents-by-day.kql` | Get document requests trend — 84 day line chart |
| `get-documents-by-hour.kql` | Get document requests trend — 1 day column chart |
| `error-rate-by-hour.kql` | ResponseStatusException error rate — 7 day line chart |
| `errors-recent.kql` | Last 50 errors — table |
| `all-logs-recent.kql` | All logs last 12h — table |
| `todays-summary.kql` | Today's notification/error/exception counts — table |

### alerts-kql

> **Source of truth** for alert queries deployed via [`cp-amp-terraform-alerts`](https://github.com/hmcts/cp-amp-terraform-alerts).
>
> After changing a query here:
> 1. Run `./sync-alerts-to-terraform.sh` to copy files to the Terraform repo
> 2. Commit, push and raise a PR in `cp-amp-terraform-alerts`
> 3. Once merged, run the Terraform pipeline in that repo to apply the changes to Azure Monitor

| Query | Description |
|---|---|
| `amp-hrds-inbound-failure-count-6.kql` | Inbound messages that have reached 6 failures (~48s of retries elapsed) |
| `amp-hrds-outbound-failure-count-6.kql` | Outbound messages that have reached 6 failures (~48s of retries elapsed) |
| `amp-hrds-duplicate-subscription.kql` | Duplicate subscription requests (409 CONFLICT) — useful for testing alert groups |
