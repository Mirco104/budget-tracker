#!/usr/bin/env bash
# Daily database backup (section 15), e.g. by cron: 30 3 * * * /opt/budget-tracker/scripts/backup.sh
# Keeps 7 days in backups/. BACKUP_COPY_CMD copies each dump off this server, e.g.
#   BACKUP_COPY_CMD='rclone copy "$1" remote:budget-backups'
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p backups
file="backups/budget-$(date +%Y-%m-%d-%H%M%S).dump"
docker compose exec -T db pg_dump -U budget -d budget -Fc > "$file"
echo "Backup erstellt: $file"

find backups -name 'budget-*.dump' -mtime +7 -delete

if [ -n "${BACKUP_COPY_CMD:-}" ]; then
  bash -c "$BACKUP_COPY_CMD" _ "$file"
  echo "Backup kopiert."
fi
