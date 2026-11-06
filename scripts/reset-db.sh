#!/usr/bin/env bash
# Resets one local H2 database (decision 35): scripts/reset-db.sh dev|sample
# Deletes only the files of that database; the next start of the profile creates it again
# (sample with fresh sample data). Stop the backend first, H2 locks the file while it runs.
set -euo pipefail

name="${1:-}"
if [[ "$name" != "dev" && "$name" != "sample" ]]; then
  echo "Aufruf: scripts/reset-db.sh dev|sample"
  exit 2
fi

data="$(cd "$(dirname "$0")/.." && pwd)/backend/data"
files=("$data/$name.mv.db" "$data/$name.trace.db")

found=false
for file in "${files[@]}"; do
  if [[ -e "$file" ]]; then
    found=true
    rm -- "$file" || { echo "Konnte $file nicht löschen. Läuft das Backend noch? Bitte vorher beenden."; exit 1; }
  fi
done

if [[ "$found" == true ]]; then
  echo "Datenbank '$name' gelöscht. Beim nächsten Start mit dem Profil $name entsteht sie neu."
else
  echo "Datenbank '$name' gibt es noch nicht, nichts zu tun."
fi
