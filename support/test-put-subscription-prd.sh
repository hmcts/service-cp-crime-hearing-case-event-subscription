#!/usr/bin/env bash
# Reproduces WAF 400→404 mangling in prod.
#
# 1. GET the subscription to confirm it exists (expect 200).
# 2. PUT with eventTypes set to "Not_Valid" — APIM correctly returns 400 Bad Request,
#    but the prod WAF (amp.cjscp.org.uk) mangles this into a 404.
#
# Required env vars (do NOT commit real values):
#   PREPROD_CLIENT_ID, PREPROD_CLIENT_SECRET, PREPROD_DATA_SCOPE
#   PREPROD_TENANT_ID, PREPROD_APIM_SUBSCRIPTION_KEY, PREPROD_SUBSCRIPTION_ID, PREPROD_APIM_BASE_URL

set -euo pipefail

: "${PROD_CLIENT_ID:?Set PROD_CLIENT_ID}"
: "${PROD_CLIENT_SECRET:?Set PROD_CLIENT_SECRET}"
: "${PROD_DATA_SCOPE:?Set PROD_DATA_SCOPE}"
: "${PROD_APIM_SUBSCRIPTION_KEY:?Set PROD_APIM_SUBSCRIPTION_KEY}"
: "${PROD_TENANT_ID:?Set PROD_TENANT_ID}"
: "${PROD_SUBSCRIPTION_ID:?Set PROD_SUBSCRIPTION_ID}"

: "${PROD_APIM_BASE_URL:?Set PROD_APIM_BASE_URL}"

# --- Get token ---
echo "Fetching token..." >&2
TOKEN_RESPONSE=$(curl --silent --request POST \
  --url "https://login.microsoftonline.com/${PROD_TENANT_ID}/oauth2/v2.0/token" \
  --data "grant_type=client_credentials" \
  --data "client_id=${PROD_CLIENT_ID}" \
  --data "client_secret=${PROD_CLIENT_SECRET}" \
  --data "scope=${PROD_DATA_SCOPE}")

TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "$TOKEN" ]]; then
  echo "Failed to get token: $TOKEN_RESPONSE" >&2
  exit 1
fi
echo "Token acquired." >&2

echo "--- Client: $PROD_SUBSCRIPTION_ID ---" >&2

RESPONSE=$(curl --silent --location --write-out "\nHTTP_STATUS:%{http_code}" \
  "${PROD_APIM_BASE_URL}/hrds/client-subscriptions/${PROD_SUBSCRIPTION_ID}" \
  --header "Ocp-Apim-Subscription-Key: ${PROD_APIM_SUBSCRIPTION_KEY}" \
  --header "Authorization: Bearer ${TOKEN}" \
  --header "Content-Type: application/json")

HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
echo "GET HTTP $HTTP_STATUS"

PUT_RESPONSE=$(curl --silent --location --request PUT \
  --write-out "\nHTTP_STATUS:%{http_code}" \
  "${PROD_APIM_BASE_URL}/hrds/client-subscriptions/${PROD_SUBSCRIPTION_ID}" \
  --header "Ocp-Apim-Subscription-Key: ${PROD_APIM_SUBSCRIPTION_KEY}" \
  --header "Authorization: Bearer ${TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{
  "notificationEndpoint": {
    "callbackUrl": "https://prdamp01.ingress01.prd.lv.cjscp.org.uk/hrds/mock-callback"
  },
  "eventTypes": [
    "NEE_FootballBanningXX"
  ]
}')

PUT_STATUS=$(echo "$PUT_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
echo "PUT HTTP $PUT_STATUS"

DELETE_RESPONSE=$(curl --silent --location --request DELETE \
  --write-out "\nHTTP_STATUS:%{http_code}" \
  "${PROD_APIM_BASE_URL}/hrds/client-subscriptions/${PROD_SUBSCRIPTION_ID}" \
  --header "Ocp-Apim-Subscription-Key: ${PROD_APIM_SUBSCRIPTION_KEY}" \
  --header "Authorization: Bearer ${TOKEN}" )

DELETE_STATUS=$(echo "$DELETE_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
echo "DELETE HTTP $DELETE_STATUS"
