# Support Queries

KQL queries for the `hearing-results-document-subscription` service.

- Paste into Log Analytics for ad-hoc investigation.
- Source of truth for dashboard panels in the Terraform sister repo.

| Folder | Use |
|---|---|
| [`logs-kql/`](logs-kql/) | Dev/int — queries `ContainerLogV2` directly using `PodName` |
| [`kql-prod/`](kql-prod/) | Prod — joins `KubePodInventory` with `ContainerLog` (until `ContainerLogV2` rolls out to prod) |
| [`chart-kql/`](chart-kql/) | Pivot/summary queries for dashboard charts |

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

| Query | Description |
|---|---|
| `counts-by-message-type-weekly.kql` | Inbound/outbound/getDocument/failures pivot by week |
