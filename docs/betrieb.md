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

## Passkeys

Passkeys (US44, Entscheidung 96) funktionieren nur unter der Domain aus `DOMAIN` über HTTPS; `docker-compose.yml` setzt daraus `PASSKEY_RP_ID` (die Domain) und `PASSKEY_ORIGINS` (`https://<DOMAIN>`). Läuft die App unter einer anderen Adresse, etwa hinter einem eigenen Proxy, beide in `.env` setzen. Ein Passkey gehört zu genau dieser Domain: Wechselt sie, gelten alle Passkeys nicht mehr, und alle melden sich wieder mit dem Passwort an und legen ihre Passkeys neu an. Lokal (`npm run dev` oder `./mvnw spring-boot:run`) gehen Passkeys unter `http://localhost:5173` und `http://localhost:8080`, nicht unter `127.0.0.1`.

## Test im Heimnetz ohne HTTPS

Ohne Domain, etwa auf einem PC im Heimnetz, in `.env` setzen:

```
SESSION_COOKIE_SECURE=false
APP_BIND=0.0.0.0
```

und ohne Caddy starten: `scripts/update.sh --no-https`. Die App ist dann unter `http://<IP>:8080` erreichbar. Das Session-Cookie geht so unverschlüsselt über das Netz, deshalb nur mit Testdaten. Passkeys gibt es so nicht, der Browser erlaubt sie nur über HTTPS oder auf `localhost`.

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

1. In `CHANGELOG.md` einen neuen Abschnitt `## <Version> (<Datum>)` oben anlegen (Ticket #159); die App zeigt ihn beim Klick auf die Version. Bis dahin kommt jede für Nutzer sichtbare Änderung in den Abschnitt der aktuellen Version. Jeder Abschnitt teilt seine Punkte in `### Neu` (neue Funktionen) und `### Änderungen` (Änderungen und Korrekturen an Bestehendem), in dieser Reihenfolge; eine Gruppe ohne Punkte entfällt. Die App zeigt die beiden Gruppen mit eigener Überschrift.
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

Mit H2 2.4.240 (Profile `dev` und `sample`) scheiterte direkt nach einer Migration jedes Speichern in einer Tabelle mit `check`-Bedingung mit „Check constraint invalid“, bis das Backend neu gestartet wurde (Ticket #147). Seitdem ist H2 in `backend/pom.xml` auf 2.3.232 festgelegt, die Version, mit der Flyway geprüft ist; ein Neustart nach einer Migration ist nicht mehr nötig. Der Test `H2MigrationTest` schlägt fehl, wenn eine spätere H2-Version (z. B. ein Update von Dependabot) den Fehler zurückbringt. PostgreSQL war nie betroffen.

## Logs

`docker compose logs app`. Die App schreibt keine Beträge, Beschreibungen, Passwörter oder Tokens ins Log; neuer Code muss das beibehalten.
