#!/usr/bin/env bash
# Checks free disk space on the given mount points and writes ALERT lines to
# the ops log when the available space falls below the threshold. Intended
# for cron, e.g.:
#   */30 * * * * /srv/campuscircle/scripts/disk-space-check.sh >/dev/null 2>&1
#
# Configurable through environment variables:
#   DISK_CHECK_PATHS        Space-separated mount points (default: / /var/lib/docker /srv)
#   DISK_MIN_FREE_GB        Alert below this many free GB (default: 5)
#   OPS_LOG                 Alert log file (default: /var/log/campuscircle/disk-space.log)

set -uo pipefail

DISK_CHECK_PATHS="${DISK_CHECK_PATHS:-/ /var/lib/docker /srv}"
DISK_MIN_FREE_GB="${DISK_MIN_FREE_GB:-5}"
OPS_LOG="${OPS_LOG:-/var/log/campuscircle/disk-space.log}"
MIN_FREE_KB=$((DISK_MIN_FREE_GB * 1024 * 1024))

alert() {
    mkdir -p "$(dirname "${OPS_LOG}")" 2>/dev/null || true
    echo "$(date '+%F %T') ALERT $*" | tee -a "${OPS_LOG}" >&2 || echo "$(date '+%F %T') ALERT $*" >&2
}

EXIT_CODE=0
for PATH_TO_CHECK in ${DISK_CHECK_PATHS}; do
    if [ ! -d "${PATH_TO_CHECK}" ]; then
        continue
    fi
    # df -Pk: POSIX format, free space in column 4 (1K blocks).
    FREE_KB="$(df -Pk "${PATH_TO_CHECK}" | sed -n '2p' | awk '{print $4}')"
    if [ -z "${FREE_KB}" ]; then
        alert "disk-space-check cannot read free space for ${PATH_TO_CHECK}"
        EXIT_CODE=1
        continue
    fi
    FREE_GB=$((FREE_KB / 1024 / 1024))
    if [ "${FREE_KB}" -lt "${MIN_FREE_KB}" ]; then
        alert "disk-space-check low free space path=${PATH_TO_CHECK} free=${FREE_GB}GB threshold=${DISK_MIN_FREE_GB}GB"
        EXIT_CODE=1
    else
        echo "$(date '+%F %T') OK disk-space-check path=${PATH_TO_CHECK} free=${FREE_GB}GB"
    fi
done

exit ${EXIT_CODE}
