#!/usr/bin/env bash
# Start or update the app in one step (ticket #129, docs/betrieb.md): database up, backup, git pull, build and start
# with the commit shown in the settings.
# Usage: scripts/update.sh            (with Caddy and HTTPS)
#        scripts/update.sh --no-https (test in the home network, without Caddy)
set -euo pipefail
cd "$(dirname "$0")/.."

profile=(--profile https)
case "${1:-}" in
  "") ;;
  --no-https) profile=() ;;
  *) echo "Unbekannte Option: $1 (erlaubt: --no-https)" >&2; exit 2 ;;
esac

# Always a backup before the update: start the database first (nothing happens if it already runs with the same
# configuration; still the old docker-compose.yml, before git pull) and wait until it is healthy. On the very first
# start the backup is of an empty database, which does no harm.
docker compose up -d --wait db
scripts/backup.sh

git pull --ff-only

APP_COMMIT=$(git rev-parse --short HEAD)
export APP_COMMIT
docker compose "${profile[@]}" up -d --build
echo "Budget-Tracker läuft mit Commit $APP_COMMIT."
