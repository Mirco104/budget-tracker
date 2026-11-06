# budget-tracker

Ein kleines Übungsprojekt für Java, Spring Boot, Maven, Vite, React und TypeScript: Soll-Budgets je Kategorie festlegen, Einnahmen und Ausgaben erfassen und im Monats-Dashboard Soll und Ist vergleichen.

Die fachliche Grundlage ist die [Spezifikation](docs/spezifikation.md).

## Voraussetzungen

- JDK 21 oder neuer
- Node.js 22.12 oder neuer (empfohlen: aktuelle LTS)

Maven muss nicht installiert sein, das Backend bringt den Maven Wrapper mit.

## Schnellstart

Ein Skript startet Backend und Frontend und öffnet die App im Browser (http://localhost:5173). Beim ersten Start installiert es die Frontend-Abhängigkeiten.

- Windows: `start.cmd` doppelklicken. Backend und Frontend laufen in eigenen Fenstern; zum Beenden beide Fenster schließen.
- macOS/Linux: `./start.sh` ausführen. Beenden mit Strg+C.

## Backend starten

```bash
cd backend
./mvnw spring-boot:run
```

Das Backend läuft auf http://localhost:8080. API-Doku: http://localhost:8080/swagger-ui.html.

### Lokale Datenbanken `dev` und `sample`

Lokal gibt es zwei H2-Datenbanken mit festen, erfundenen Logins (Entscheidung 35). Sie liegen unter `backend/data/` und werden nicht eingecheckt.

| Profil | Datei | Inhalt |
|---|---|---|
| `dev` (Standard ohne Profil) | `backend/data/dev.mv.db` | nur der Systemadmin *Admin*, keine Buchungen |
| `sample` | `backend/data/sample.mv.db` | *Admin* und Person A bis D mit erfundenen Daten von Januar 2025 bis Oktober 2026 |

```bash
cd backend
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.profiles=sample
```

Logins (Passwörter in `application-dev.properties` bzw. `application-sample.properties`):

- `admin@example.test`: Systemadmin, in beiden Datenbanken
- `person-a@example.test`: Admin, viele Daten über alle Monate, Soll-Werte, Serien und Rücklagen
- `person-b@example.test`: wenige Daten, schwankende Einnahmen aus Selbstständigkeit
- `person-c@example.test`: erst ab Juli 2025, Prozent-Soll, Kredit bis Juni 2026
- `person-d@example.test`: fast keine Daten

Die Dateien bleiben zwischen zwei Starts erhalten, eigene Änderungen also auch; neue Migrationen laufen wie im Betrieb auf die vorhandenen Daten. Die Beispieldaten entstehen nur, wenn die Datenbank `sample` leer ist. Zurücksetzen (Backend vorher beenden, löscht nur die genannte Datenbank):

```bash
scripts/reset-db.sh sample
```

Eine ältere `backend/data/budget.mv.db` wird nicht mehr benutzt und kann gelöscht werden.

### Anmeldung und erster Admin

Die App ist nur angemeldet nutzbar (US14). Der Systemadmin ist immer das Konto mit der E-Mail aus `ADMIN_EMAIL` (US25); in den lokalen Profilen `dev` und `sample` ist das fest `admin@example.test`. Das Backend prüft das bei jedem Start: Fehlt das Konto, legt es dieses mit `ADMIN_PASSWORD` und den Standard-Kategorien an; gibt es das Konto schon, wird es Systemadmin und behält sein Passwort; ein bisheriger Systemadmin mit anderer E-Mail wird Admin. Für den Betrieb (Profil `postgres`) stehen die Werte nie im Repo, sondern in `.env` bzw. in Umgebungsvariablen (siehe `docs/betrieb.md`). Fehlt `ADMIN_EMAIL`, ändert sich nichts; gibt es dann keinen Systemadmin, meldet das Log eine Warnung. Die E-Mail des Systemadmins lässt sich in der App nicht ändern, nur über `ADMIN_EMAIL` und einen Neustart. Nach 5 falschen Passwörtern in 15 Minuten ist die Anmeldung für diese E-Mail 15 Minuten gesperrt; ein Neustart hebt die Sperre auf.

Die Migration `V2` löscht beim ersten Start alle Daten von vor dem Benutzersystem, weil sie keinem Nutzer gehören.

Zum Testen im Heimnetz, etwa am Handy über `http://<IP-des-PCs>:5173`, ist das Session-Cookie in der lokalen Entwicklung ohne `Secure`-Flag, sonst verwirft der Browser es über HTTP. Im Betrieb (Profil `postgres`, Docker) ist es an. Lokal nur Testdaten verwenden.

Weitere Nutzer kommen nur über Einladungen (US15): Ein Admin erzeugt unter *Einstellungen › Einladungen* einen Link und schickt ihn selbst weiter, die App versendet keine E-Mails. Der Link ist einmal nutzbar und 7 Tage gültig. Unter *Einstellungen › Konto* ändert jeder Nutzer Passwort und E-Mail-Adresse, exportiert seine Daten als JSON und löscht sein Konto samt aller Daten (US16). Admins sehen unter *Einstellungen › Nutzer* alle Konten mit letzter Anmeldung (ohne Finanzdaten) und machen andere zu Admins oder Usern (US25). Den Systemadmin kann niemand herabstufen, und er kann sein Konto nicht löschen.

### Datenbank und Migrationen

Das Schema entsteht nur aus den Flyway-Migrationen in `backend/src/main/resources/db/migration` (US11). Sie laufen beim Start automatisch; Hibernate prüft das Schema nur (`ddl-auto=validate`). Eine Migration, die schon in `main` ist, wird nie geändert; jede Änderung am Schema ist eine neue Datei `V<n>__<beschreibung>.sql`.

Mit PostgreSQL statt H2 (Profil `postgres`, Verbindung und Admin nur über Umgebungsvariablen):

```bash
cd backend
SPRING_PROFILES_ACTIVE=postgres ADMIN_EMAIL=<e-mail> ADMIN_PASSWORD=<mindestens-12-zeichen> DB_URL=jdbc:postgresql://localhost:5432/budget DB_USERNAME=budget DB_PASSWORD=<passwort> ./mvnw spring-boot:run
```

Build und Tests:

```bash
cd backend
./mvnw -B verify
```

## Frontend starten

```bash
cd frontend
npm ci
npm run dev
```

Das Frontend läuft auf http://localhost:5173 und leitet `/api` an das Backend weiter.

Lint, Tests und Build:

```bash
cd frontend
npm run lint
npm test
npm run build
```

## Betrieb mit Docker

Für den Betrieb gibt es ein Docker-Image mit Backend und Frontend und eine `docker-compose.yml` mit PostgreSQL und optional Caddy für HTTPS. Einrichten, Update, Backup und Wiederherstellung: [docs/betrieb.md](docs/betrieb.md).

```bash
cp .env.example .env
docker compose --profile https up -d --build
```

## CI

GitHub Actions (`.github/workflows/ci.yml`) baut und testet Backend und Frontend bei jedem Push auf `main` und bei jedem Pull Request. Die Backend-Tests laufen zweimal: mit H2 und mit PostgreSQL; zusätzlich baut die CI das Docker-Image. Bei jedem Pull Request spielt der Job `update-test` ein Update durch: Er startet `main` mit erfundenen Testdaten, aktualisiert mit demselben Datenbank-Volume auf den Stand des PRs und prüft, dass alle Daten noch stimmen. Lokal geht das genauso mit `node scripts/update-test.mjs seed|check <datei>` gegen eine laufende App.

Dependabot hält Maven, npm, GitHub Actions und die Docker-Images aktuell. Kleine Updates kommen gebündelt als ein PR je Ökosystem; Major-Versionen von Java, Node und PostgreSQL schlägt er nicht vor, weil sie bewusst umgestellt werden.
