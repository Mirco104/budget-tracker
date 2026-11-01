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

Das Backend läuft auf http://localhost:8080. API-Doku: http://localhost:8080/swagger-ui.html. Die H2-Datenbank liegt lokal unter `backend/data/` und wird nicht eingecheckt.

### Datenbank und Migrationen

Das Schema entsteht nur aus den Flyway-Migrationen in `backend/src/main/resources/db/migration` (US11). Sie laufen beim Start automatisch; Hibernate prüft das Schema nur (`ddl-auto=validate`). Eine Migration, die schon in `main` ist, wird nie geändert; jede Änderung am Schema ist eine neue Datei `V<n>__<beschreibung>.sql`.

Eine lokale H2-Datenbank von vor der Umstellung auf Flyway lässt sich nicht weiterverwenden: einmal `backend/data/` löschen, beim nächsten Start entsteht sie neu.

Mit PostgreSQL statt H2 (Profil `postgres`, Verbindung nur über Umgebungsvariablen):

```bash
cd backend
SPRING_PROFILES_ACTIVE=postgres DB_URL=jdbc:postgresql://localhost:5432/budget DB_USERNAME=budget DB_PASSWORD=<passwort> ./mvnw spring-boot:run
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

## CI

GitHub Actions (`.github/workflows/ci.yml`) baut und testet Backend und Frontend bei jedem Push auf `main` und bei jedem Pull Request. Die Backend-Tests laufen zweimal: mit H2 und mit PostgreSQL.
