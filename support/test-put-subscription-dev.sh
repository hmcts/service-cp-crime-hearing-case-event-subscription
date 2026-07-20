#!/usr/bin/env bash
# Reproduces WAF 400→404 mangling in prod.
#
# 1. GET the subscription to confirm it exists (expect 200).
# 2. PUT with eventTypes set to "Not_Valid" — APIM correctly returns 400 Bad Request,
#    but the prod WAF (amp.cjscp.org.uk) mangles this into a 404.
#
# Required env vars (do NOT commit real values):
#   SIT_CLIENT_ID, SIT_CLIENT_SECRET, SIT_DATA_SCOPE
#   SIT_TENANT_ID, SIT_APIM_SUBSCRIPTION_KEY, SIT_SUBSCRIPTION_ID, SIT_APIM_BASE_URL

set -euo pipefail

: "${SIT_CLIENT_ID:?Set SIT_CLIENT_ID}"
: "${SIT_CLIENT_SECRET:?Set SIT_CLIENT_SECRET}"
: "${SIT_DATA_SCOPE:?Set SIT_DATA_SCOPE}"
: "${SIT_APIM_SUBSCRIPTION_KEY:?Set SIT_APIM_SUBSCRIPTION_KEY}"
: "${SIT_TENANT_ID:?Set SIT_TENANT_ID}"
: "${SIT_SUBSCRIPTION_ID:?Set SIT_SUBSCRIPTION_ID}"

: "${SIT_APIM_BASE_URL:?Set SIT_APIM_BASE_URL}"

# --- Get token ---
echo "Fetching token..." >&2
TOKEN_RESPONSE=$(curl --silent --request POST \
  --url "https://login.microsoftonline.com/${SIT_TENANT_ID}/oauth2/v2.0/token" \
  --data "grant_type=client_credentials" \
  --data "client_id=${SIT_CLIENT_ID}" \
  --data "client_secret=${SIT_CLIENT_SECRET}" \
  --data "scope=${SIT_DATA_SCOPE}")

TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "$TOKEN" ]]; then
  echo "Failed to get token: $TOKEN_RESPONSE" >&2
  exit 1
fi
echo "Token acquired." >&2

echo "--- Client: $SIT_SUBSCRIPTION_ID ---" >&2

RESPONSE=$(curl --silent --location --write-out "\nHTTP_STATUS:%{http_code}" \
  "${SIT_APIM_BASE_URL}/client-subscriptions/${SIT_SUBSCRIPTION_ID}" \
  --header "Ocp-Apim-Subscription-Key: ${SIT_APIM_SUBSCRIPTION_KEY}" \
  --header "Authorization: Bearer ${TOKEN}" \
  --header "Content-Type: application/json")

HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
echo "GET HTTP $HTTP_STATUS"

PUT_RESPONSE=$(curl --silent --location --request PUT \
  --write-out "\nHTTP_STATUS:%{http_code}" \
  "${SIT_APIM_BASE_URL}/client-subscriptions/${SIT_SUBSCRIPTION_ID}" \
  --header "Ocp-Apim-Subscription-Key: ${SIT_APIM_SUBSCRIPTION_KEY}" \
  --header "Authorization: Bearer ${TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{
  "notificationEndpoint": {
    "callbackUrl": "https://sitamp01.ingress01.dev.nl.cjscp.org.uk/hrds/mock-callback"
  },
  "eventTypes": [
    "NEE_FootballBanningXX"
  ]
}')

PUT_STATUS=$(echo "$PUT_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
echo "PUT HTTP $PUT_STATUS"
