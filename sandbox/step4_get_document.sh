#!/usr/bin/env bash
set -euo pipefail

CLIENT_ID="${CLIENT_ID:-11111111-1111-1111-1111-111111111111}"
BASE_URL="${BASE_URL:-http://localhost:4550}"
WIREMOCK_URL="${WIREMOCK_URL:-http://localhost:8090}"
OUTPUT_FILE="${OUTPUT_FILE:-document.pdf}"

SUBSCRIPTION_ID=$(jq -r .clientSubscriptionId /tmp/subscription.json)

echo ""
echo "  CLIENT_ID:       ${CLIENT_ID}"
echo "  SUBSCRIPTION_ID: ${SUBSCRIPTION_ID}"
echo "  WIREMOCK_URL:    ${WIREMOCK_URL}/__admin/requests"
echo ""

echo "Fetching documentId from WireMock callbacks..."
DOC_ID=$(curl -s "${WIREMOCK_URL}/__admin/requests" \
  | jq -r '[.requests[] | select(.request.url == "/callback") | .request.body | fromjson] | last | .documentId')

if [ -z "$DOC_ID" ] || [ "$DOC_ID" = "null" ]; then
  echo "No callbacks received yet — run ./step3_send_notification.sh first"
  exit 1
fi

echo "  DOCUMENT_ID:     ${DOC_ID}"
echo "  URL:             ${BASE_URL}/client-subscriptions/${SUBSCRIPTION_ID}/documents/${DOC_ID}"
echo ""

echo "Downloading document..."
curl -s -o "${OUTPUT_FILE}" \
  -H "X-Client-Id: ${CLIENT_ID}" \
  "${BASE_URL}/client-subscriptions/${SUBSCRIPTION_ID}/documents/${DOC_ID}"

echo "Saved to ${OUTPUT_FILE}"
