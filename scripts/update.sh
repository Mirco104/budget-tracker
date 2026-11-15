#!/usr/bin/env bash
# Start or update the app in one step (ticket #129, docs/betrieb.md): backup, git pull, build and start with the commit
# shown in the settings. Usage: scripts/update.sh            (with Caddy and HTTPS)
#                               scripts/update.sh --no-https (test in the home network, without Caddy)
set -euo pipefail
cd "$(dirname "$0")/.."

profile=(--profile https)
case "${1:-}" in
  "") ;;
  --no-https) profile=() ;;
  *) echo "Unbekannte Option: $1 (erlaubt: --no-https)" >&2; exit 2 ;;
esac

# On the very first start there is no database yet, so there is nothing to back up
if [ -n "$(docker compose ps -q --status running db 2>/dev/null)" ]; then
  scripts/backup.sh
else
  echo "Datenbank läuft nicht, kein Backup (erster Start?)."
fi

git pull --ff-only

APP_COMMIT=$(git rev-parse --short HEAD)
export APP_COMMIT
docker compose "${profile[@]}" up -d --build
echo "Budget-Tracker läuft mit Commit $APP_COMMIT."
