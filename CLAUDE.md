# CLAUDE.md

## Project
Budget tracker web app: set target budgets per category, record income and expenses, see a monthly dashboard with target vs. actual. Started as an 8-hour practice project; it is now being prepared for real use by several people (first privately among friends, maybe public later), with user accounts, shared expenses and an investment part. The source of truth for behavior is `docs/spezifikation.md` (rules R1–R22, user stories US1–US24, work packages in sections 13 and 14, operations in section 15). Read the relevant section before every task.

## Stack
- Backend: Java 21, Spring Boot 4.x, Maven, Spring Web, Spring Data JPA, H2 (file DB), Bean Validation, springdoc-openapi, JUnit 5, AssertJ, MockMvc
- Frontend: Vite, React, TypeScript (strict), TanStack Query, Vitest, Testing Library, ESLint
- CI: GitHub Actions
- Layout: `/backend`, `/frontend`, `/docs`, `.github/workflows`
- Planned (add only in the named work package): Flyway and PostgreSQL for production, H2 for tests and local dev (AP 17); Spring Security (AP 18); Docker image and `docker-compose.yml` (AP 19); Recharts (AP 16)

## Commands
- Backend: `cd backend && ./mvnw -B verify` (build and tests), `./mvnw spring-boot:run`
- Frontend: `cd frontend && npm ci && npm run lint && npm test && npm run build`, `npm run dev`

## Language
- UI text: German
- Code, identifiers, comments, tests, commit messages, PR titles: English

## Architecture rules
- Layers: controller → service → repository. Controllers contain no business logic.
- All calculations live in plain classes without Spring dependencies: `RecurrenceSchedule` (occurrences of recurring transactions, R12), `DashboardCalculator` (categories, transactions, occurrences and a month in, dashboard DTO out, R1–R10) and `ReserveCalculator` (reserve analysis, R14); later `SplitCalculator` (R17), `BalanceCalculator` (R18) and `InvestmentProjection` (R19, R20). Implement the rules exactly as written in the spec.
- Amounts carry their sign: positive is income, negative is expense (R11). Categories have no type. For a shared expense the amount is the user's own share (R16).
- Money is `BigDecimal` (scale 2, `RoundingMode.HALF_UP`). Never use `double` or `float` for money.
- Expose DTOs (Java records), never JPA entities.
- Errors as RFC 7807 `ProblemDetail`; validate input with Bean Validation.
- From AP 18: every entity belongs to a user (`owner`) and every query is scoped to the signed-in user (R15). Another user's resource in the URL is 404, a reference to it in the body is 400. Never take a user id from the request.
- From AP 17: the schema comes only from Flyway migrations. Never change a migration that is merged.
- Frontend: no business rules, only display and formatting (`Intl.NumberFormat('de-DE')`). API calls only in `src/api/`.
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
