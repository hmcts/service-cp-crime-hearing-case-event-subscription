#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:4550}"

# Pre-loaded WireMock stub materialId — use this to trigger a full end-to-end flow
MATERIAL_ID="6c198796-08bb-4803-b456-fa0c29ca6021"
EVENT_ID="3fa85f64-5717-4562-b3fc-2c963f66afa6"
TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

echo ""
echo "  URL:         ${BASE_URL}/notifications"
echo "  EVENT_ID:    ${EVENT_ID}"
echo "  MATERIAL_ID: ${MATERIAL_ID}"
echo "  TIMESTAMP:   ${TIMESTAMP}"
echo ""

curl -s -X POST "${BASE_URL}/notifications" \
  -H "Content-Type: application/json" \
  -d "{
    \"eventId\": \"${EVENT_ID}\",
    \"materialId\": \"${MATERIAL_ID}\",
    \"eventType\": \"PRISON_COURT_REGISTER_GENERATED\",
    \"timestamp\": \"${TIMESTAMP}\",
    \"defendant\": {
      \"masterDefendantId\": \"8b8f1c3a-9a41-4f0f-b8a0-1c23d9e8a111\",
      \"name\": \"John Doe\",
      \"dateOfBirth\": \"1990-05-15\",
      \"custodyEstablishmentDetails\": {
        \"emailAddress\": \"prison@moj.gov.uk\"
      },
      \"cases\": [
        { \"urn\": \"CT98KRYCAP\" }
      ]
    }
  }"

echo ""
echo "HTTP 202 expected — the app will fetch metadata from WireMock and deliver a callback to your registered URL."
