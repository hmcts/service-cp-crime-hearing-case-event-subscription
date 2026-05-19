# HRDS Sandbox — Subscriber Quick Start

## Quick start in 4 steps

```bash
cd sandbox
./step1_startup.sh             # start HRDS and dependencies in Docker - this mirrors HMCTS running live instance
./step2_create_subscription.sh # register to receive notifications - your app will do this
./step3_send_notification.sh   # fire a test event through HRDS - HMCTS will do this
./step4_get_document.sh        # fetch the resulting document PDF - your app will do this
```

Each script reads `CLIENT_ID` and `CALLBACK_URL` from the environment — defaults work out of the box. See [Supporting Information](#supporting-information) below for details.

---

## Supporting Information

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Docker Desktop | 4.x+ | Required for all services |
| curl + jq | any | Used by the scripts |

The app image is pulled from `ghcr.io` — no authentication needed (public package). To pin to a specific release replace `:latest` in `sandbox-docker-compose.yml` with a version tag.

> **Apple Silicon — action required before first run:**
> The Service Bus emulator depends on Azure SQL Edge (`linux/amd64` only). Enable Rosetta in Docker Desktop:
> **Settings → Features in development → Use Rosetta for x86/amd64 emulation**, then restart Docker Desktop.

> **Startup time:** Service Bus takes **60–90 seconds** to initialise. Logs stream to the terminal so you can follow progress.

---

### What runs in the sandbox

| Service | Port | Purpose |
|---------|------|---------|
| HRDS app | `4550` | The subscription service |
| PostgreSQL | `5432` | Subscriber state + Flyway migrations |
| Service Bus emulator | `5672`, `5300` | Inbound/outbound event queues |
| WireMock | `8090` | Material service stubs + callback receiver |

OAuth is **disabled** and `ENVIRONMENT_NAME=LOCAL` in the sandbox — these settings are never used in a deployed environment.

---

### API reference

Full request/response schemas and examples: [SwaggerHub](https://app.swaggerhub.com/apis/HMCTS-DTS/api-cp-crime-hearing-results-document-subscription)

Base URL: `http://localhost:4550`

---

### Client ID header

In production, APIM extracts the client ID from the JWT. In the sandbox there is no APIM, so pass it yourself via `X-Client-Id` on every `/client-subscriptions` request. The scripts default to `11111111-1111-1111-1111-111111111111` — override with `export CLIENT_ID=<your-uuid>`.

### Callbacks

By default, callbacks are delivered to WireMock at `http://wiremock:8080/callback` (reachable from the app inside Docker). No external tunnel needed. Override with `export CALLBACK_URL=<your-url>`.

---

### Rotate the HMAC secret

```bash
KEY_ID=$(jq -r .hmac.keyId /tmp/subscription.json)

curl -s -X POST \
  "http://localhost:4550/client-subscriptions/${SUBSCRIPTION_ID}/secret/rotate" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: ${CLIENT_ID}" \
  -d "{\"keyId\": \"${KEY_ID}\"}"
```

---

### Verifying HMAC signatures on callbacks

Every callback POST includes:

| Header | Description |
|--------|-------------|
| `X-Key-Id` | Identifies the signing key |
| `X-Signature` | HMAC-SHA256 of the raw request body, base64-encoded |

```
secret_bytes = base64_decode(hmac.secret)
expected     = base64_encode(HMAC-SHA256(secret_bytes, raw_request_body))
assert expected == X-Signature header
```

---

### Resetting the sandbox

To wipe all data and start fresh:

```bash
docker compose -f sandbox-docker-compose.yml down -v
```

The `-v` flag removes the PostgreSQL volume. Run `step1_startup.sh` again to bring everything back up clean.

---

### Troubleshooting

| Symptom | Likely cause |
|---------|-------------|
| Service Bus slow to start | Normal — can take up to 90s; watch the logs |
| App exits on startup | Check Docker logs for `hrds-app` — usually a DB or Service Bus timeout |
| `step2` returns `400` | `eventTypes` must be a non-empty array of valid names from `GET /event-types` |
| `step4` reports no callbacks | Run `step3` first; check `hrds-app` logs for material service errors |
| Service Bus emulator crashes on Apple Silicon | Rosetta not enabled — see Prerequisites above |
