#!/usr/bin/env bash
# Restores a dump from scripts/backup.sh and replaces all current data (docs/betrieb.md).
set -euo pipefail
cd "$(dirname "$0")/.."

file="${1:?Aufruf: scripts/restore.sh backups/budget-<Datum>.dump}"
[ -f "$file" ] || { echo "Datei nicht gefunden: $file"; exit 1; }

docker compose stop app
docker compose exec -T db pg_restore -U budget -d budget --clean --if-exists --no-owner < "$file"
docker compose start app
echo "Wiederhergestellt aus $file."
