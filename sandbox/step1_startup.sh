#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo ""
echo "NOTE: Service Bus emulator (Azure SQL Edge, linux/amd64) takes 60-90s to start."
echo "      On Apple Silicon, enable Rosetta in Docker Desktop:"
echo "      Settings → Features in development → Use Rosetta for x86/amd64 emulation"
echo ""
echo "Press Ctrl-C to stop all services."
echo ""

docker compose -f sandbox-docker-compose.yml up
