#!/usr/bin/env bash
# Restores the UniNook (campuscircle) MySQL database from a backup produced
# by backup-mysql.sh (plain .sql or gzipped .sql.gz). The database password
# is read from the container environment; it is never passed on the command
# line.
#
# Usage:
#   scripts/restore-mysql.sh <backup-file>            # restore into the existing database
#   scripts/restore-mysql.sh <backup-file> --drop     # DROP and recreate the database first
#
# Set FORCE_RESTORE=1 to skip the interactive confirmation (used by drills/automation).
#
# Configurable through environment variables:
#   COMPOSE_PROJECT_NAME     Compose project name      (default: campuscircle)
#   MYSQL_SERVICE            Compose MySQL service     (default: mysql)
#   DB_NAME                  Database/schema           (default: campuscircle)

set -euo pipefail

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-campuscircle}"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
DB_NAME="${DB_NAME:-campuscircle}"

BACKUP_FILE="${1:-}"
DROP_FIRST=0
if [ "${2:-}" = "--drop" ]; then
    DROP_FIRST=1
fi

if [ -z "${BACKUP_FILE}" ]; then
    echo "Usage: $0 <backup-file.sql|.sql.gz> [--drop]" >&2
    exit 2
fi
if [ ! -f "${BACKUP_FILE}" ]; then
    echo "[restore-mysql] ERROR: backup file not found: ${BACKUP_FILE}" >&2
    exit 1
fi

echo "[restore-mysql] project=${COMPOSE_PROJECT_NAME} service=${MYSQL_SERVICE} database=${DB_NAME}"
echo "[restore-mysql] source=${BACKUP_FILE} drop-first=${DROP_FIRST}"

if [ "${FORCE_RESTORE:-0}" != "1" ]; then
    if [ "${DROP_FIRST}" = "1" ]; then
        echo "[restore-mysql] WARNING: this will DROP the database '${DB_NAME}' and all its data."
    else
        echo "[restore-mysql] WARNING: this will overwrite matching tables in '${DB_NAME}'."
    fi
    printf "Continue? [yes/N] "
    read -r ANSWER
    if [ "${ANSWER}" != "yes" ]; then
        echo "[restore-mysql] aborted by operator"
        exit 1
    fi
fi

run_sql() {
    docker compose exec -T "${MYSQL_SERVICE}" sh -lc \
        'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "'"$1"'"'
}

restore_from_stdin() {
    docker compose exec -T "${MYSQL_SERVICE}" sh -lc \
        'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" '"${DB_NAME}"
}

if [ "${DROP_FIRST}" = "1" ]; then
    run_sql "DROP DATABASE IF EXISTS ${DB_NAME}; CREATE DATABASE ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"
fi

# Guarantee the target schema exists when restoring without --drop.
run_sql "CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"

if ! case "${BACKUP_FILE}" in
    *.gz) gzip -dc "${BACKUP_FILE}" | restore_from_stdin ;;
    *)    restore_from_stdin < "${BACKUP_FILE}" ;;
esac; then
    echo "[restore-mysql] ERROR: restore failed from ${BACKUP_FILE}" >&2
    exit 1
fi

TABLE_COUNT="$(docker compose exec -T "${MYSQL_SERVICE}" sh -lc \
    "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';\"")"
TABLE_COUNT="$(echo "${TABLE_COUNT}" | tr -d '[:space:]')"
echo "[restore-mysql] OK: restored ${BACKUP_FILE}; database '${DB_NAME}' now has ${TABLE_COUNT} tables"
