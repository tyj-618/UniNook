#!/usr/bin/env bash
# Dumps the UniNook (campuscircle) MySQL database from the Compose service,
# compresses it, stores it in BACKUP_DIR, and prunes backups older than
# BACKUP_RETENTION_DAYS. The database password is read from the container
# environment (CAMPUSCIRCLE_DB_PASSWORD in .env); it is never passed on the
# command line.
#
# Usage:
#   scripts/backup-mysql.sh
#
# Configurable through environment variables:
#   COMPOSE_PROJECT_NAME     Compose project name            (default: campuscircle)
#   MYSQL_SERVICE            Compose MySQL service name      (default: mysql)
#   DB_NAME                  Database/schema to dump         (default: campuscircle)
#   BACKUP_DIR               Directory for backup files      (default: <repo>/backups)
#   BACKUP_RETENTION_DAYS    Days to keep backups            (default: 7)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-campuscircle}"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
DB_NAME="${DB_NAME:-campuscircle}"
BACKUP_DIR="${BACKUP_DIR:-${REPO_ROOT}/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"

TIMESTAMP="$(date +%F-%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}-${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "[backup-mysql] project=${COMPOSE_PROJECT_NAME} service=${MYSQL_SERVICE} database=${DB_NAME}"
echo "[backup-mysql] target=${BACKUP_FILE} retention=${BACKUP_RETENTION_DAYS}d"

# --single-transaction keeps a consistent snapshot of InnoDB tables without locking.
# The exit status of the dump must not be masked by gzip, hence pipefail above.
if ! docker compose exec -T "${MYSQL_SERVICE}" sh -lc \
    'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --default-character-set=utf8mb4 '"${DB_NAME}" \
    | gzip > "${BACKUP_FILE}"; then
    echo "[backup-mysql] ERROR: mysqldump failed; removing partial file" >&2
    rm -f "${BACKUP_FILE}"
    exit 1
fi

# A valid dump always starts with a mysqldump header comment.
FIRST_LINE="$(gzip -dc "${BACKUP_FILE}" | sed -n 1p)"
case "${FIRST_LINE}" in
    "-- MySQL dump"*) ;;
    *)
        echo "[backup-mysql] ERROR: ${BACKUP_FILE} does not look like a mysqldump output" >&2
        rm -f "${BACKUP_FILE}"
        exit 1
        ;;
esac

SIZE="$(du -h "${BACKUP_FILE}" | cut -f1)"
echo "[backup-mysql] OK: ${BACKUP_FILE} (${SIZE})"

# Prune expired backups.
find "${BACKUP_DIR}" -name "${DB_NAME}-*.sql.gz" -type f -mtime "+${BACKUP_RETENTION_DAYS}" -print -delete \
    | sed 's/^/[backup-mysql] pruned: /' || true
