#!/usr/bin/env bash
# Filters UniNook application logs by the shared requestId so one request can
# be traced end to end (retrieval -> prompt -> agent/model -> tool -> response).
# Assistant log lines carry "requestId=<id>" (see docs/agent-production-operations.md).
#
# Usage:
#   scripts/query-by-request-id.sh <request-id>                 # docker compose logs (default, last 24h)
#   scripts/query-by-request-id.sh <request-id> -f app.log      # filter local log file(s)
#   SINCE=2h scripts/query-by-request-id.sh <request-id>        # docker logs window
#
# Configurable through environment variables:
#   COMPOSE_PROJECT_NAME   Compose project name     (default: campuscircle)
#   APP_SERVICE            Compose app service      (default: app)
#   SINCE                  docker logs time window  (default: 24h)

set -euo pipefail

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-campuscircle}"
APP_SERVICE="${APP_SERVICE:-app}"
SINCE="${SINCE:-24h}"

REQUEST_ID="${1:-}"
LOG_FILE=""
if [ "${2:-}" = "-f" ]; then
    LOG_FILE="${3:-}"
fi

if [ -z "${REQUEST_ID}" ]; then
    echo "Usage: $0 <request-id> [-f <log-file>]" >&2
    exit 2
fi

# grep -F: the id is matched literally; anchor to the structured field name to
# avoid false positives from ids embedded in other tokens.
if [ -n "${LOG_FILE}" ]; then
    if [ ! -f "${LOG_FILE}" ]; then
        echo "[query-by-request-id] ERROR: log file not found: ${LOG_FILE}" >&2
        exit 1
    fi
    grep -F "requestId=${REQUEST_ID}" -- "${LOG_FILE}"
else
    docker compose logs --since="${SINCE}" "${APP_SERVICE}" 2>&1 \
        | grep -F "requestId=${REQUEST_ID}"
fi
