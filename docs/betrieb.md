# Betrieb

Die App läuft als ein Docker-Image, in dem Spring Boot das gebaute Frontend ausliefert, zusammen mit PostgreSQL und optional Caddy für HTTPS (Entscheidung 21, Spezifikation Abschnitt 15). Voraussetzung auf dem Server: Docker mit Compose und Git.

## Einrichten

```bash
git clone https://github.com/Mirco104/budget-tracker.git
cd budget-tracker
cp .env.example .env
```

In `.env` jeden Platzhalter ersetzen: ein langes Datenbank-Passwort, E-Mail, Passwort (mindestens 12 Zeichen) und Anzeigename des ersten Admins und die Domain. `.env` nie einchecken.

Die Domain muss per DNS auf den Server zeigen, Ports 80 und 443 müssen offen sein. Dann:

```bash
docker compose --profile https up -d --build
```

Caddy holt das Zertifikat von Let's Encrypt automatisch und leitet HTTP auf HTTPS um. Die App selbst lauscht nur auf `127.0.0.1:8080`, von außen ist sie nur über Caddy erreichbar. Beim ersten Start legt sie den Admin aus `.env` an; danach kommen weitere Nutzer nur über Einladungen (US15). Die Admin-Werte können danach aus `.env` entfernt werden.

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
```

Die Flyway-Migrationen laufen beim Start der App automatisch.

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
