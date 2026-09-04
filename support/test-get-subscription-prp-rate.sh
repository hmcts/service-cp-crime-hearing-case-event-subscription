#!/usr/bin/env bash
# GETs the subscription 110 times concurrently to test rate limiting / WAF behaviour.
#
# Required env vars (do NOT commit real values):
#   PREPROD_CLIENT_ID, PREPROD_CLIENT_SECRET, PREPROD_DATA_SCOPE
#   PREPROD_TENANT_ID, PREPROD_APIM_SUBSCRIPTION_KEY, PREPROD_SUBSCRIPTION_ID, PREPROD_APIM_BASE_URL

set -euo pipefail

: "${PREPROD_CLIENT_ID:?Set PREPROD_CLIENT_ID}"
: "${PREPROD_CLIENT_SECRET:?Set PREPROD_CLIENT_SECRET}"
: "${PREPROD_DATA_SCOPE:?Set PREPROD_DATA_SCOPE}"
: "${PREPROD_APIM_SUBSCRIPTION_KEY:?Set PREPROD_APIM_SUBSCRIPTION_KEY}"
: "${PREPROD_TENANT_ID:?Set PREPROD_TENANT_ID}"
: "${PREPROD_SUBSCRIPTION_ID:?Set PREPROD_SUBSCRIPTION_ID}"
: "${PREPROD_APIM_BASE_URL:?Set PREPROD_APIM_BASE_URL}"

TOTAL=110

# --- Get token ---
echo "Fetching token..." >&2
TOKEN_RESPONSE=$(curl --silent --request POST \
  --url "https://login.microsoftonline.com/${PREPROD_TENANT_ID}/oauth2/v2.0/token" \
  --data "grant_type=client_credentials" \
  --data "client_id=${PREPROD_CLIENT_ID}" \
  --data "client_secret=${PREPROD_CLIENT_SECRET}" \
  --data "scope=${PREPROD_DATA_SCOPE}")

TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "$TOKEN" ]]; then
  echo "Failed to get token: $TOKEN_RESPONSE" >&2
  exit 1
fi
echo "Token acquired. Firing $TOTAL concurrent GET requests..." >&2

do_get() {
  local i=$1
  local status
  status=$(curl --silent --location --write-out "%{http_code}" --output /dev/null \
    "${PREPROD_APIM_BASE_URL}/hrds/client-subscriptions/${PREPROD_SUBSCRIPTION_ID}" \
    --header "Ocp-Apim-Subscription-Key: ${PREPROD_APIM_SUBSCRIPTION_KEY}" \
    --header "Authorization: Bearer ${TOKEN}")
  echo "[$i/$TOTAL] GET HTTP $status"
}

export -f do_get
export PREPROD_APIM_BASE_URL PREPROD_SUBSCRIPTION_ID PREPROD_APIM_SUBSCRIPTION_KEY TOKEN TOTAL

for i in $(seq 1 $TOTAL); do
  do_get "$i" &
done

wait
echo "Done." >&2
