#!/usr/bin/env bash
# Starts backend and frontend, opens the app in the browser. Ctrl+C stops both.
set -euo pipefail
cd "$(dirname "$0")"

command -v java >/dev/null || { echo "Java wurde nicht gefunden. Bitte JDK 21 oder neuer installieren."; exit 1; }
command -v npm >/dev/null || { echo "Node.js wurde nicht gefunden. Bitte Node.js 22.12 oder neuer installieren."; exit 1; }

if [ ! -d frontend/node_modules ]; then
  echo "Installiere Frontend-Abhängigkeiten ..."
  (cd frontend && npm ci)
fi

trap 'kill 0' EXIT

echo "Starte Backend ..."
(cd backend && ./mvnw spring-boot:run) &

echo "Starte Frontend ..."
(cd frontend && npm run dev) &

echo "Warte auf das Backend (http://localhost:8080) ..."
until curl -s -o /dev/null http://localhost:8080/v3/api-docs; do sleep 2; done
echo "Warte auf das Frontend (http://localhost:5173) ..."
until curl -s -o /dev/null http://localhost:5173; do sleep 1; done

url=http://localhost:5173
if command -v open >/dev/null; then open "$url"
elif command -v xdg-open >/dev/null; then xdg-open "$url"
fi
echo
echo "Die App läuft unter $url. Beenden mit Strg+C."
wait
