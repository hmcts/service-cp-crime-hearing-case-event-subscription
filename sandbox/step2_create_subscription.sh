#!/usr/bin/env bash
set -euo pipefail

CLIENT_ID="${CLIENT_ID:-11111111-1111-1111-1111-111111111111}"
WIREMOCK_INTERNAL_URL="${WIREMOCK_INTERNAL_URL:-https://wiremock:8443}"
CALLBACK_URL="${CALLBACK_URL:-${WIREMOCK_INTERNAL_URL}/callback}"
BASE_URL="${BASE_URL:-http://localhost:4550}"

echo ""
echo "  CLIENT_ID:    ${CLIENT_ID}"
echo "  CALLBACK_URL: ${CALLBACK_URL}"
echo "  URL:          ${BASE_URL}/client-subscriptions"
echo ""

curl -s -X POST "${BASE_URL}/client-subscriptions" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: ${CLIENT_ID}" \
  -d "{
    \"notificationEndpoint\": {
      \"callbackUrl\": \"${CALLBACK_URL}\"
    },
    \"eventTypes\": [\"PRISON_COURT_REGISTER_GENERATED\"]
  }" | tee /tmp/subscription.json

echo ""
echo "  SUBSCRIPTION_ID: $(jq -r .clientSubscriptionId /tmp/subscription.json)"
echo "  KEY_ID:          $(jq -r .hmac.keyId /tmp/subscription.json)"
echo "  SECRET:          $(jq -r .hmac.secret /tmp/subscription.json)"
echo ""
echo "The secret is shown once only — store it now. Or you can find it in /tmp/subscription.json"
