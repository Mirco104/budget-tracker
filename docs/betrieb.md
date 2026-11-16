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
scripts/update.sh
```

Das Skript startet die App mit `docker compose --profile https up -d --build` (Details unter *Update*).

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

und ohne Caddy starten: `scripts/update.sh --no-https`. Die App ist dann unter `http://<IP>:8080` erreichbar. Das Session-Cookie geht so unverschlüsselt über das Netz, deshalb nur mit Testdaten.

## Update

```bash
scripts/update.sh             # mit Caddy und HTTPS
scripts/update.sh --no-https  # Test im Heimnetz ohne Caddy
```

Das Skript macht nacheinander:

1. die Datenbank starten, falls sie nicht läuft (`docker compose up -d --wait db`; läuft sie schon, passiert nichts), und immer ein Backup mit `scripts/backup.sh` (beim ersten Start von der leeren Datenbank),
2. `git pull --ff-only`,
3. `APP_COMMIT=$(git rev-parse --short HEAD) docker compose --profile https up -d --build`.

Bricht ein Schritt ab, laufen die folgenden nicht. Die Flyway-Migrationen laufen beim Start der App automatisch. `APP_COMMIT` sorgt dafür, dass die Fußzeile neben der Version auch den Commit kennt (das Image selbst hat kein `.git`); wer `docker compose` von Hand startet, sieht nur die Version.

## Version

Die App hat eine Version nach SemVer mit Vorabkennung (Ticket #129, Entscheidung 69): `0.1.0-alpha` ist die erste. Solange nur der Bekanntenkreis testet, bleibt die Null vorne und die Kennung `-alpha`, später `-beta`; `1.0.0` erst für eine öffentliche Nutzung. Die Stufe steht neben dem Namen der App auf jeder Seite (Alpha, Beta), die Version in der Fußzeile (der Commit beim Darüberfahren); angemeldet liefert `/actuator/info` sie auch.

Eine neue Version:

1. In `CHANGELOG.md` einen neuen Abschnitt `## <Version> (<Datum>)` oben anlegen (Ticket #159); die App zeigt ihn beim Klick auf die Version. Bis dahin kommt jede für Nutzer sichtbare Änderung in den Abschnitt der aktuellen Version.
2. Die Version in `frontend/package.json` (dazu `npm install --package-lock-only` für `package-lock.json`) und in `backend/pom.xml` gleich setzen; die CI prüft das (`node scripts/check-version.mjs`).
3. Nach dem Merge auf `main` setzt die GitHub Action *Release tag* (`.github/workflows/release-tag.yml`) das Tag `v<Version>` auf den Merge-Commit, wenn es noch nicht existiert. Sie läuft bei jeder Änderung von `frontend/package.json` (oder der Action selbst) auf `main` und lässt sich unter *Actions* auch von Hand starten.

Neue Funktionen erhöhen die mittlere Zahl, reine Fehlerbehebungen die letzte.

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

## Lokale H2-Datenbank nach einer neuen Migration

Mit H2 2.4.240 (Profile `dev` und `sample`) kann direkt nach einer Migration, die eine Tabelle mit einer `check`-Bedingung ändert (z. B. V20 an `bank_accounts`), jedes Speichern in dieser Tabelle mit „Check constraint invalid“ scheitern; die App zeigt „Unbekannter Fehler“. Ein Neustart des Backends behebt es. PostgreSQL ist nicht betroffen.

## Logs

`docker compose logs app`. Die App schreibt keine Beträge, Beschreibungen, Passwörter oder Tokens ins Log; neuer Code muss das beibehalten.
