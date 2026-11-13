# CLAUDE.md

## Project
Budget tracker web app: set target budgets per category, record income and expenses, see a monthly dashboard with target vs. actual. Started as an 8-hour practice project; it is now being prepared for real use by several people (first privately among friends, maybe public later), with user accounts, shared expenses and an investment part. The source of truth for behavior is `docs/spezifikation.md` (rules R1–R31 and, from AP 27, P1–P11 in section 19, which replace several R rules; user stories US1–US35, work packages in sections 13, 14, 17, 18 and 19, operations in section 15). Read the relevant section before every task.

## Stack
- Backend: Java 21, Spring Boot 4.x, Maven, Spring Web, Spring Data JPA, Spring Security, Flyway, PostgreSQL (production), H2 (tests and local dev), Bean Validation, springdoc-openapi, JUnit 5, AssertJ, MockMvc
- Frontend: Vite, React, TypeScript (strict), TanStack Query, Recharts, Vitest, Testing Library, ESLint
- CI: GitHub Actions, Dependabot
- Operations: Docker image (`Dockerfile`), `docker-compose.yml` with PostgreSQL and optional Caddy, Spring Boot Actuator health (see `docs/betrieb.md`)
- Layout: `/backend`, `/frontend`, `/docs`, `/scripts`, `.github/workflows`

## Commands
- Backend: `cd backend && ./mvnw -B verify` (build and tests), `./mvnw spring-boot:run` (profile `dev`, empty), `./mvnw spring-boot:run -Dspring-boot.run.profiles=sample` (sample data), `scripts/reset-db.sh dev|sample`
- Frontend: `cd frontend && npm ci && npm run lint && npm test && npm run build`, `npm run dev`
- Docker: `docker compose up -d --build` (values from `.env`, template `.env.example`), `scripts/backup.sh`, `scripts/restore.sh <dump>`

## Language
- UI text: German
- Code, identifiers, comments, tests, commit messages, PR titles: English

## Architecture rules
- Layers: controller → service → repository. Controllers contain no business logic.
- All calculations live in plain classes without Spring dependencies. From AP 27 (section 19): `PlanCalculator` (monthly share, plan per category, groups, not planned, saving rate, P2–P4, P9), `TermSchedule` (terms of a planned item with week or month rhythm and next working day, P5), `Fulfillment` (which booking fulfils which term, term status, fix/variable, P5), `DashboardCalculator` (only real bookings, P1, plan per category P3, R5–R9), `ReserveCalculator` (reserve pots per rare item, P8), `AccountBalance` (P11), `MonthClosing` (P10). Before AP 27: from AP 19c `IncomeAverage` (average and minimum income before a month, R23, used by both), from AP 19f `FixedCosts` (monthly share of recurring expenses per category, R25), from AP 26 `CategorySuggester` (category suggestion from the user's history, R29), `OccurrenceMatcher` (which occurrence a booking replaces, R28) and the bank import in plain classes (bank profiles that read a file into the normalized `BankTransaction` with ISO 20022 fields, fingerprint, transfer and duplicate detection, R27, R30, decision 46; `SeriesDetector` for monthly series, R31); later `SplitCalculator` (R17), `BalanceCalculator` (R18) and `InvestmentProjection` (R19, R20). Implement the rules exactly as written in the spec.
- Amounts carry their sign: positive is income, negative is expense (R11). Categories have no type. For a shared expense the amount is the user's own share (R16).
- From AP 26: every transaction and series belongs to a bank account (R26). A transfer between own accounts has no category and counts in no calculation (R27); every calculation must leave transfers out. From AP 27: planned items and their terms never count as actuals (P1); only bookings do. An expense that fulfils a term is fix (P5).
- Money is `BigDecimal` (scale 2, `RoundingMode.HALF_UP`). Never use `double` or `float` for money.
- Expose DTOs (Java records), never JPA entities.
- Errors as RFC 7807 `ProblemDetail`; validate input with Bean Validation.
- From AP 18: every entity belongs to a user (`owner`) and every query is scoped to the signed-in user (R15). Another user's resource in the URL is 404, a reference to it in the body is 400. Never take a user id from the request.
- From AP 17: the schema comes only from Flyway migrations. Never change a migration that is merged.
- Frontend: no business rules, only display and formatting (`Intl.NumberFormat('de-DE')`). API calls only in `src/api/`. The backend only knows euro; the percent input of the planning page is gone from AP 27 (decision 49).
- From AP 19b: build screens from the shared building blocks in `src/components/ui/` and the CSS variables for colors and spacing (light and dark mode); no UI library. Blue for actions and "within budget", orange as the warning color, petrol for income, green for saving and reserves (from AP 27), red only for errors and delete. Icons are inline SVG. Every page has its own address (hash routing).
- Keep it simple. No extra layers, patterns or dependencies unless the spec needs them.

## Workflow
1. One branch per feature: `feat/<short-name>`. Never commit to `main`.
2. Plan first (plan mode), then implement. For the calculation classes, write the tests first.
3. Small commits in Conventional Commits style (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).
4. Open a pull request with: goal, spec reference (e.g. US3), what was generated, what was changed by hand.

## Definition of done
- Acceptance criteria of the user story are met
- Tests written and green, lint green, CI green
- No TODOs left without a note in the PR

## Public repository: do not commit
- Real financial data, names, account details, secrets, `.env` files, local database files
- Production data, database dumps and backups
- Secrets belong in environment variables; only `.env.example` with placeholders is committed
- Seed and test data must be fictional (persons in fixtures are "Person A", "Person B")
