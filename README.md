# budget-tracker

Ein kleines Übungsprojekt für Java, Spring Boot, Maven, Vite, React und TypeScript: Soll-Budgets je Kategorie festlegen, Einnahmen und Ausgaben erfassen und im Monats-Dashboard Soll und Ist vergleichen.

Die fachliche Grundlage ist die [Spezifikation](docs/spezifikation.md).

## Voraussetzungen

- JDK 21 oder neuer
- Node.js 22.12 oder neuer (empfohlen: aktuelle LTS)

Maven muss nicht installiert sein, das Backend bringt den Maven Wrapper mit.

## Backend starten

```bash
cd backend
./mvnw spring-boot:run
```

Das Backend läuft auf http://localhost:8080. API-Doku: http://localhost:8080/swagger-ui.html. Die H2-Datenbank liegt lokal unter `backend/data/` und wird nicht eingecheckt.

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

GitHub Actions (`.github/workflows/ci.yml`) baut und testet Backend und Frontend bei jedem Push auf `main` und bei jedem Pull Request.
