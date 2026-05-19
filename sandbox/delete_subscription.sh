#!/usr/bin/env bash
set -euo pipefail

CLIENT_ID="${CLIENT_ID:-11111111-1111-1111-1111-111111111111}"
BASE_URL="${BASE_URL:-http://localhost:4550}"

SUBSCRIPTION_ID=$(jq -r .clientSubscriptionId /tmp/subscription.json)

echo ""
echo "  CLIENT_ID:       ${CLIENT_ID}"
echo "  SUBSCRIPTION_ID: ${SUBSCRIPTION_ID}"
echo "  URL:             ${BASE_URL}/client-subscriptions/${SUBSCRIPTION_ID}"
echo ""

curl -s -X DELETE \
  "${BASE_URL}/client-subscriptions/${SUBSCRIPTION_ID}" \
  -H "X-Client-Id: ${CLIENT_ID}"

echo "Subscription ${SUBSCRIPTION_ID} deleted."
