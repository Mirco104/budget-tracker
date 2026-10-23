# CLAUDE.md

## Project
Budget tracker web app: set target budgets per category, record income and expenses, see a monthly dashboard with target vs. actual. Practice project, built in about 8 hours. The source of truth for behavior is `docs/spezifikation.md` (rules R1–R14, user stories US1–US13). Read the relevant section before every task.

## Stack
- Backend: Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, H2 (file DB), Bean Validation, springdoc-openapi, JUnit 5, AssertJ, MockMvc
- Frontend: Vite, React, TypeScript (strict), TanStack Query, Vitest, Testing Library, ESLint
- CI: GitHub Actions
- Layout: `/backend`, `/frontend`, `/docs`, `.github/workflows`

## Commands
- Backend: `cd backend && ./mvnw -B verify` (build and tests), `./mvnw spring-boot:run`
- Frontend: `cd frontend && npm ci && npm run lint && npm test && npm run build`, `npm run dev`

## Language
- UI text: German
- Code, identifiers, comments, tests, commit messages, PR titles: English

## Architecture rules
- Layers: controller → service → repository. Controllers contain no business logic.
- All calculations live in plain classes without Spring dependencies: `RecurrenceSchedule` (occurrences of recurring transactions, R12), `DashboardCalculator` (categories, transactions, occurrences and a month in, dashboard DTO out, R1–R10) and `ReserveCalculator` (reserve analysis, R14). Implement the rules exactly as written in the spec.
- Amounts carry their sign: positive is income, negative is expense (R11). Categories have no type.
- Money is `BigDecimal` (scale 2, `RoundingMode.HALF_UP`). Never use `double` or `float` for money.
- Expose DTOs (Java records), never JPA entities.
- Errors as RFC 7807 `ProblemDetail`; validate input with Bean Validation.
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
- Seed data must be fictional
