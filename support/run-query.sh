#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# run-query.sh — run a .kql file against Log Analytics via Azure CLI
#
# Usage:
#   ./run-query.sh <path-to-query.kql> [timespan] [correlation-id|--raw]
#
# Examples:
#   ./run-query.sh logs-kql/postStartup-queue-check.kql
#   ./run-query.sh logs-kql/errors-all.kql PT6H
#   ./run-query.sh logs-kql/trace-correlation.kql P1D 1c7dbbcd72bc
#   ./run-query.sh chart-kql/counts-by-message-type-weekly.kql P28D --json
#
# Timespan uses ISO 8601 duration format (default: PT1H)
#   PT1H = last 1 hour, PT6H = last 6 hours, P1D = last 1 day, P7D = last 7 days
#
# Prerequisites:
#   az cli installed and logged in (az login)
#   az extension add --name log-analytics  (one-time; prompts on first run if skipped)
#   jq installed (brew install jq)
#   WORKSPACE_ID set as an environment variable (export WORKSPACE_ID=<guid>)
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

WORKSPACE_ID="${WORKSPACE_ID:-}"
QUERY_FILE="${1:-}"
TIMESPAN="${2:-PT1H}"
CORR_ID="${3:-}"
RAW=false
[[ "${3:-}" == "--json" || "${4:-}" == "--json" ]] && RAW=true

# Validate ISO 8601 duration — must start with P, no PT prefix before day/week/month/year units
if ! [[ "$TIMESPAN" =~ ^P([0-9]+Y)?([0-9]+M)?([0-9]+W)?([0-9]+D)?(T([0-9]+H)?([0-9]+M)?([0-9]+S)?)?$ ]] || [[ "$TIMESPAN" == "P" ]] || [[ "$TIMESPAN" =~ ^PT[0-9]+[DWMY] ]]; then
  echo "Error: invalid timespan '$TIMESPAN'"
  echo ""
  echo "Use ISO 8601 duration format:"
  echo "  P1D  = 1 day     P7D  = 7 days    P30D = 30 days"
  echo "  PT1H = 1 hour    PT6H = 6 hours   PT30M = 30 minutes"
  echo ""
  echo "Common mistake: PT7D should be P7D (days are not a time component)"
  exit 1
fi

if [[ -z "$QUERY_FILE" ]]; then
  echo "Usage: $0 <path-to-query.kql> [timespan]"
  echo ""
  echo "Available queries:"
  find "$SCRIPT_DIR/logs-kql" "$SCRIPT_DIR/chart-kql" -name "*.kql" 2>/dev/null | sort | sed "s|$SCRIPT_DIR/||"
  exit 1
fi

if [[ ! -f "$QUERY_FILE" ]] && [[ ! -f "$SCRIPT_DIR/$QUERY_FILE" ]]; then
  echo "Error: query file not found: $QUERY_FILE"
  exit 1
fi

# Resolve relative paths
[[ -f "$QUERY_FILE" ]] || QUERY_FILE="$SCRIPT_DIR/$QUERY_FILE"

if [[ -z "$WORKSPACE_ID" ]]; then
  echo "Error: WORKSPACE_ID is not set."
  echo ""
  echo "Set it with:"
  echo "  export WORKSPACE_ID=<your-log-analytics-workspace-guid>"
  echo ""
  echo "Find it in the Azure portal under:"
  echo "  LA-MPD-PRD-INT-WS > Overview > Workspace ID"
  exit 1
fi

QUERY=$(cat "$QUERY_FILE")
if [[ -n "$CORR_ID" ]]; then
  QUERY=$(echo "$QUERY" | sed "s/REPLACE_ME/$CORR_ID/")
fi

echo "Running: $(basename "$QUERY_FILE")  [timespan: $TIMESPAN]${CORR_ID:+ [corr: $CORR_ID]}"
echo "--- KQL ---"
echo "$QUERY"
echo "--- Results ---"

az monitor log-analytics query \
  --workspace "$WORKSPACE_ID" \
  --analytics-query "$QUERY" \
  --timespan "$TIMESPAN" \
  --output json > /tmp/kql-result.json

RESULT=$(cat /tmp/kql-result.json)
if $RAW || ! echo "$RESULT" | jq -e '.[0].TimeGenerated' > /dev/null 2>&1; then
  echo "$RESULT" | jq '.'
else
  echo "$RESULT" \
    | jq -r '.[] | [(.TimeGenerated // .Day), (.Level // "-"), (.CorrId // "-"), ((.Message // (.Count | tostring) // "") | gsub("\n"; " "))] | @tsv' \
    | awk -F'\t' '{ printf "%-28s [%-5s] [%-12s] %s\n", $1, $2, $3, $4 }'
fi
