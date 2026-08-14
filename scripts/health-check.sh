#!/usr/bin/env bash
# Polls the Spring Boot Actuator health endpoint and writes ALERT lines to the
# ops log when the service is down or not UP. Intended for cron, e.g.:
#   */5 * * * * /srv/campuscircle/scripts/health-check.sh >/dev/null 2>&1
#
# The actuator health status aggregates the datasource (MySQL) and Redis
# indicators: if either dependency fails, the composite status is DOWN.
# Elasticsearch is intentionally not part of /health (retrieval degrades
# gracefully without it); set CHECK_ES=1 to probe it separately.
#
# Alerting is log-based for now; a DingTalk/e-mail hook can be added later in
# the alert() function without changing the checks.
#
# Configurable through environment variables:
#   HEALTH_URL       Actuator health URL        (default: http://127.0.0.1:8080/actuator/health)
#   ES_URL           Elasticsearch base URL     (default: http://127.0.0.1:9200)
#   CHECK_ES         Also probe Elasticsearch   (default: 0)
#   OPS_LOG          Alert log file             (default: /var/log/campuscircle/health-check.log)
#   TIMEOUT_SECONDS  curl timeout               (default: 10)

set -uo pipefail

HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
ES_URL="${ES_URL:-http://127.0.0.1:9200}"
CHECK_ES="${CHECK_ES:-0}"
OPS_LOG="${OPS_LOG:-/var/log/campuscircle/health-check.log}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-10}"

log_line() {
    echo "$(date '+%F %T') $*"
}

alert() {
    # Future: forward to DingTalk / e-mail here. For now: durable ops log.
    mkdir -p "$(dirname "${OPS_LOG}")" 2>/dev/null || true
    log_line "ALERT $*" | tee -a "${OPS_LOG}" >&2 || log_line "ALERT $*" >&2
}

BODY="$(curl -fsS -m "${TIMEOUT_SECONDS}" "${HEALTH_URL}" 2>/dev/null)"
CURL_STATUS=$?

if [ ${CURL_STATUS} -ne 0 ]; then
    alert "health-check endpoint unreachable url=${HEALTH_URL} curl_exit=${CURL_STATUS}"
    exit 1
fi

STATUS="$(echo "${BODY}" | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
if [ -z "${STATUS}" ]; then
    alert "health-check unexpected response url=${HEALTH_URL} body=${BODY}"
    exit 1
fi

if [ "${STATUS}" != "UP" ]; then
    alert "health-check status=${STATUS} url=${HEALTH_URL} body=${BODY}"
    exit 1
fi

if [ "${CHECK_ES}" = "1" ]; then
    if ! curl -fsS -m "${TIMEOUT_SECONDS}" "${ES_URL}/_cluster/health" >/dev/null 2>&1; then
        alert "health-check elasticsearch unreachable url=${ES_URL} (app health still UP; retrieval degrades)"
        # Non-fatal: the application keeps serving with degraded retrieval.
    fi
fi

log_line "OK health-check status=${STATUS} url=${HEALTH_URL}"
echo "${BODY}"
exit 0
