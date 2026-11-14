# Betrieb

Die App läuft als ein Docker-Image, in dem Spring Boot das gebaute Frontend ausliefert, zusammen mit PostgreSQL und optional Caddy für HTTPS (Entscheidung 21, Spezifikation Abschnitt 15). Voraussetzung auf dem Server: Docker mit Compose und Git.

## Einrichten

```bash
git clone https://github.com/Mirco104/budget-tracker.git
cd budget-tracker
cp .env.example .env
```

In `.env` jeden Platzhalter ersetzen: ein langes Datenbank-Passwort, E-Mail, Passwort (mindestens 12 Zeichen) und Anzeigename des Systemadmins (US25) und die Domain. `.env` nie einchecken.

Die Domain muss per DNS auf den Server zeigen, Ports 80 und 443 müssen offen sein. Dann:

```bash
docker compose --profile https up -d --build
```

Caddy holt das Zertifikat von Let's Encrypt automatisch und leitet HTTP auf HTTPS um. Die App selbst lauscht nur auf `127.0.0.1:8080`, von außen ist sie nur über Caddy erreichbar. Beim ersten Start legt sie den Systemadmin aus `.env` an; danach kommen weitere Nutzer nur über Einladungen (US15). Systemadmin ist bei jedem Start das Konto mit der E-Mail aus `ADMIN_EMAIL` (US25), deshalb bleibt `ADMIN_EMAIL` in `.env` stehen. `ADMIN_PASSWORD` wirkt nur, wenn das Konto neu angelegt wird; danach kann es aus `.env` entfernt werden. Das Passwort ändert der Systemadmin in der App unter *Einstellungen › Konto*.

Status prüfen:

```bash
docker compose ps
curl -s http://localhost:8080/actuator/health
```

Die Antwort ist nur `{"status":"UP"}`, ohne Details.

## Test im Heimnetz ohne HTTPS

Ohne Domain, etwa auf einem PC im Heimnetz, in `.env` setzen:

```
SESSION_COOKIE_SECURE=false
APP_BIND=0.0.0.0
```

und ohne Caddy starten: `docker compose up -d --build`. Die App ist dann unter `http://<IP>:8080` erreichbar. Das Session-Cookie geht so unverschlüsselt über das Netz, deshalb nur mit Testdaten.

## Update

```bash
scripts/backup.sh
git pull
docker compose --profile https up -d --build
git tag -f produktiv
```

Die Flyway-Migrationen laufen beim Start der App automatisch. Der Tag `produktiv` markiert den Commit, aus dem das laufende Image gebaut wurde. Er bleibt nur auf dem Server und wird nicht gepusht; erst setzen, wenn der Build durchgelaufen ist.

## Stand prüfen

Wie weit liegt die Produktivumgebung hinter `main` oder einem anderen Branch zurück? Alle Befehle auf dem Server im Verzeichnis des Checkouts (z. B. `/opt/budget-tracker`). Sie ändern nichts am laufenden Betrieb.

Zuerst den Stand von GitHub holen, ohne den Checkout zu ändern:

```bash
git fetch origin
```

**Welcher Commit läuft?**

```bash
git log -1 --format='%h %ci %s' produktiv
```

Ohne Tag (vor dem ersten Update mit `git tag -f produktiv`) stattdessen `HEAD` nehmen. Das stimmt nur, wenn nach dem letzten `git pull` auch neu gebaut wurde. Zur Kontrolle das Build-Datum des Images mit dem Datum des Commits vergleichen; das Image muss jünger sein:

```bash
docker image inspect --format '{{.Created}}' "$(docker compose images -q app)"
git log -1 --format=%ci HEAD
```

Lokale Änderungen am Checkout zeigt `git status --short`; die Ausgabe sollte leer sein.

**Rückstand zu `main`**

Anzahl der fehlenden Commits:

```bash
git rev-list --count produktiv..origin/main
```

`0` heißt: Die Produktion ist auf dem Stand von `main`.

Fehlende Pull Requests, eine Zeile pro Merge in `main`:

```bash
git log --oneline --first-parent produktiv..origin/main
```

Alle fehlenden Commits einzeln:

```bash
git log --oneline produktiv..origin/main
```

Geänderte Dateien:

```bash
git diff --stat produktiv origin/main
```

Vor dem Update besonders wichtig sind neue Datenbank-Migrationen, denn sie laufen beim Start automatisch und lassen sich nur über ein Backup zurücknehmen:

```bash
git diff --name-only produktiv origin/main -- backend/src/main/resources/db/migration
```

**Vergleich mit einem anderen Branch**

Dieselben Befehle mit dem Branch statt `main`, z. B. `origin/feat/category-icons`. Bei einem Branch gibt es oft Unterschiede in beide Richtungen, deshalb beide Seiten zählen:

```bash
git rev-list --left-right --count produktiv...origin/feat/category-icons
```

Die linke Zahl sind Commits, die nur in der Produktion sind (der Branch ist also nicht auf dem aktuellen Stand), die rechte Zahl Commits, die nur im Branch sind. Die Commits selbst:

```bash
git log --oneline produktiv..origin/feat/category-icons
git log --oneline origin/feat/category-icons..produktiv
```

Alle Branches von GitHub mit ihrem Rückstand und Vorsprung gegenüber der Produktion:

```bash
git for-each-ref --format='%(refname:short) %(ahead-behind:produktiv)' refs/remotes/origin
```

Die erste Zahl sind Commits, die nur im Branch sind, die zweite Commits, die nur in der Produktion sind (braucht Git 2.41 oder neuer).

**Ohne Zugang zum Server**

Mit dem Commit-Hash aus `git log -1 produktiv` zeigt GitHub den Unterschied im Browser:

```
https://github.com/Mirco104/budget-tracker/compare/<commit>...main
```

Oben steht die Zahl der Commits und der geänderten Dateien, darunter die Commits und die Änderungen. Für einen anderen Branch `main` durch den Branch-Namen ersetzen.

## Backup

`scripts/backup.sh` schreibt einen Dump (`pg_dump`, Custom-Format) nach `backups/` und löscht Dumps, die älter als 7 Tage sind. Täglich per Cron, z. B.:

```
30 3 * * * /opt/budget-tracker/scripts/backup.sh >> /var/log/budget-backup.log 2>&1
```

Ein Backup nur auf demselben Server reicht nicht. `BACKUP_COPY_CMD` kopiert jeden Dump woandershin, der Pfad des Dumps ist `$1`, z. B. mit rclone:

```
30 3 * * * BACKUP_COPY_CMD='rclone copy "$1" remote:budget-backups' /opt/budget-tracker/scripts/backup.sh
```

Das Ziel muss in der EU liegen (Abschnitt 15) und die Kopien dort ebenfalls aufräumen.

## Wiederherstellen

```bash
scripts/restore.sh backups/budget-<Datum>.dump
```

Das Skript stoppt die App, ersetzt alle Daten in der Datenbank durch den Stand des Dumps und startet die App wieder. Weil die App neu startet, müssen sich alle Nutzer neu anmelden.

Getestet am 02.11.2026 mit Docker Desktop und Testdaten: Backup erstellt und kopiert, eine Transaktion gelöscht, wiederhergestellt; die Transaktion war wieder da. Dumps älter als 7 Tage wurden gelöscht, jüngere blieben.

## HTTPS lokal ausprobieren

Mit `DOMAIN=localhost` nutzt Caddy eine eigene lokale Zertifizierungsstelle statt Let's Encrypt (der Browser warnt, `curl -k` geht):

```bash
DOMAIN=localhost docker compose --profile https up -d
curl -k https://localhost/actuator/health
```

So geprüft am 02.11.2026: HTTP leitet auf HTTPS um, das Session-Cookie ist `Secure; HttpOnly; SameSite=Lax`, Einladungslinks beginnen mit `https://`.

## Logs

`docker compose logs app`. Die App schreibt keine Beträge, Beschreibungen, Passwörter oder Tokens ins Log; neuer Code muss das beibehalten.
