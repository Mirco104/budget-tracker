# Spezifikation MVP: Budget-Tracker (Web-App)

Stand: 17.10.2026 · Basis: Google Sheet "Budget + Investment Planer V1.1.0" (nur Budget-Blätter) · Stack laut Stellenanzeige marcapo: Java + Spring Boot (REST) und TypeScript + React · Ziel: ca. 8 h Arbeitszeit, fertig bis Di 20.10.2026

## 1. Ziel und Scope

**Ziel:** Eine schlanke Web-App, die das Budget-Blatt des Sheets abbildet: Soll-Verteilung festlegen, Ausgaben und Einnahmen erfassen, Dashboard mit Soll/Ist/Differenz. Das Projekt zeigt vor allem den Prozess: Spezifikation → Plan → Umsetzung mit Claude Code → Review → Tests → CI.

**Im MVP (drin):**
- Kategorien mit Soll-Werten (Euro oder Prozent der Einnahmen)
- Transaktionen erfassen (Ausgaben und Einnahmen, mit Datum)
- Jährliche Transaktionen (Betrag/Jahr, wird als Betrag/12 pro Monat angerechnet)
- Dashboard pro Monat: Soll, Ist, Differenz, Überschuss, Einnahmenanteile

**Bewusst draußen (im README als Ausblick):**
- Alle Investment-Blätter (Sparplan, Prognosen, Freistellungsauftrag, Einmalzahlung)
- Import aus dem Investment-Tracker (im Sheet ist B7 der Monatstransaktionen eine Verknüpfung dorthin; im MVP ist "Investitionen" eine normale Kategorie)
- Benutzerverwaltung/Login (Single-User, keine Authentifizierung)
- Mehrere Währungen, Export/Import, Mobile-Optimierung

## 2. Fachliche Regeln (aus den Formeln des Sheets abgeleitet)

Sei `M` der gewählte Monat.

| # | Regel | Quelle im Sheet |
|---|---|---|
| R1 | **Ist je Kategorie** = Summe aller Transaktionen der Kategorie in M + Summe (Jahresbetrag / 12) aller jährlichen Transaktionen der Kategorie, die in M gültig sind (`validFrom ≤ M` und `validTo` leer oder `M ≤ validTo`) | `Dashboard_Budget!E39:E59`, `K39:K46` |
| R2 | **Einnahmen gesamt (Ist)** = Summe der Ist-Werte aller Einnahmen-Kategorien | `K37` |
| R3 | **Soll je Ausgaben-Kategorie:** Maßgeblich ist der Soll-Eintrag der Kategorie mit dem spätesten `validFrom ≤ M` (gilt ab M für alle folgenden Monate, bis ein neuerer Eintrag ihn ersetzt). Gibt es keinen solchen Eintrag oder sind darin beide Werte leer: kein Soll. Innerhalb des Eintrags gilt: Ist ein Euro-Wert gesetzt, gilt er. Sonst, wenn ein Prozentwert gesetzt ist: Einnahmen gesamt × Prozent. Sonst: kein Soll. Der Euro-Wert hat immer Vorrang. | `Einstellungen_Budget!F11:F30` |
| R4 | **Rest-Kategorie "Sonstiges (berechneter Rest)":** Soll = Einnahmen gesamt − Summe aller übrigen Soll-Werte. Darf negativ sein (Überplanung). Diese Kategorie ist systemseitig, nicht löschbar, nicht editierbar. | `F31`, `G31` |
| R5 | **Differenz je Kategorie** = Soll − Ist. Ohne Soll: leer. | `F39:F59` |
| R6 | **Gesamt:** Soll gesamt = Summe aller Soll (inkl. Rest, ergibt damit die Einnahmen); Ist gesamt = Summe aller Ist; Differenz gesamt = Soll − Ist | `D37:F37` |
| R7 | **Überschuss** = Einnahmen (Ist) − Ausgaben (Ist). Bezeichnung: "Monatlicher Überschuss" bei ≥ 0, sonst "Monatlicher Verlust". | `I17`, `I18` |
| R8 | **Veränderung** = Einnahmen / Ausgaben − 1 als Prozent; bei Ausgaben = 0: leer. Bezeichnung: "Anstieg der Gesamteinsparung" bei ≥ 0, sonst "Reduzierung der Gesamteinsparung". | `I15`, `I16` |
| R9 | **Einnahmenanteil** je Einnahmen-Kategorie = Ist / Einnahmen gesamt; bei Einnahmen = 0: 0 % | `L39:L46` |
| R10 | **Gruppen:** Ausgaben-Kategorien gehören zu Fixkosten (empfohlen ca. 50 %), Flexible Ausgaben (ca. 30 %), Sparen & Investieren (ca. 20 %) oder Rest. Gruppensummen für Soll und Ist werden angezeigt. | `Einstellungen_Budget!H11:J31` |
| R11 | Beträge sind positiv. Ob Einnahme oder Ausgabe, bestimmt die Kategorie, nicht das Vorzeichen. | Validierungslisten D/H |
| R12 | **Anteilige Jahresbeträge sichtbar machen:** Je Kategorie wird ausgewiesen, wie viel vom Ist aus jährlichen Transaktionen stammt (der R1-Anteil). "Anteilige Jahreskosten" = Summe dieser Anteile über alle Ausgaben-Kategorien. **Fällige Abbuchung:** Eine jährliche Transaktion mit "Abbuchung regulär am" ist in M fällig, wenn sie in M gültig ist und der Kalendermonat von `dueDate` dem von M entspricht (jährlich wiederkehrend, gleicher Tag; 29.02. wird in Nicht-Schaltjahren zum 28.02.). Fällige Abbuchungen werden mit vollem Jahresbetrag als Hinweis angezeigt; das Ist ändert sich dadurch nicht. | neu, nicht im Sheet |

**Nicht übernehmen:** Die Hilfsblätter `IGNORE…` (Diagrammdaten, rechnet das Frontend selbst) sowie veraltete Formeln in `Dashboard_Budget` (Zeilen 38 und 48–55 der Einnahmenspalte verweisen auf ein nicht existierendes Blatt `Monatliche_Transaktionen`). Als Referenzwerte gelten die Formeln, nicht diese Reste.

**Geldbeträge:** `BigDecimal` im Backend (Skala 2, Rundung HALF_UP), im JSON als String oder Zahl mit 2 Nachkommastellen. Kein `double`.

## 3. User Stories und Akzeptanzkriterien

### Must
- **US1 Kategorien ansehen:** Als Nutzer sehe ich alle Kategorien gruppiert (Fixkosten, Flexible Ausgaben, Sparen & Investieren, Einnahmen). *AK:* Beim ersten Start sind die Kategorien des Sheets als Seed vorhanden.
- **US2 Soll festlegen:** Als Nutzer lege ich je Ausgaben-Kategorie ab einem gewählten Monat ein Soll in Euro oder in Prozent fest. Es gilt für diesen und alle folgenden Monate, bis ein neuerer Eintrag es ersetzt; frühere Monate bleiben unverändert. *AK:* R3 und R4 gelten; Prozent zwischen 0 und 100; negativer Euro-Wert wird abgelehnt (400 mit Fehlermeldung); erneutes Speichern für denselben Monat überschreibt den Eintrag dieses Monats; beide Werte leer heißt "kein Soll ab diesem Monat".
- **US3 Transaktion erfassen:** Als Nutzer erfasse ich Betrag, Beschreibung, Datum und Kategorie. *AK:* Betrag > 0, Beschreibung höchstens 100 Zeichen, Kategorie muss existieren, Datum Pflicht.
- **US4 Transaktion ändern/löschen.**
- **US5 Dashboard:** Als Nutzer wähle ich einen Monat und sehe Soll, Ist, Differenz je Kategorie und Gesamt sowie Überschuss. *AK:* R1 bis R9; Werte entsprechen den Beispieldaten des Sheets (siehe Testfälle).

### Should
- **US6 Jährliche Transaktionen:** Erfassen, ändern, löschen; Anrechnung nach R1 (Jahresbetrag / 12 pro Monat) in jedem Monat ihres Gültigkeitszeitraums. *AK:* Betrag > 0, Beschreibung höchstens 100 Zeichen, Kategorie muss existieren; "gültig ab" (Monat) ist Pflicht, "gültig bis" (Monat, einschließlich) ist optional und darf nicht vor "gültig ab" liegen (sonst 400); frühere Monate bleiben unverändert.
- **US7 Gruppensummen** nach R10 im Dashboard.
- **US6a Jährliche Transaktionen im Dashboard:** Als Nutzer sehe ich im Dashboard, welcher Teil des Ist je Kategorie aus jährlichen Transaktionen stammt, die Summe der anteiligen Jahreskosten und einen Hinweis auf in M fällige Abbuchungen. *AK:* R12.
- **US8 Monatsnavigation** (vor/zurück) und Anzeige leerer Monate ohne Fehler.

### Nice
- **US9 Diagramm** (Soll gegen Ist je Gruppe) mit Recharts.
- **US10 Kategorien anlegen/umbenennen/löschen** (löschen nur ohne Transaktionen).
- **US11 Flyway-Migrationen** statt `data.sql`.
- **US12 Kategorie aufklappen (vorgemerkt, später):** Im Dashboard zeigt ein Klick auf eine Kategorie ihre Buchungen im Monat und die anteiligen Jahresbeträge (R1, R12). Passt gut zusammen mit US9.

## 4. Datenmodell

```
Category
  id            Long, PK
  name          String, eindeutig, nicht leer
  type          EXPENSE | INCOME
  group         FIXED | FLEXIBLE | SAVINGS | REMAINDER | null   (null bei INCOME)
  sortOrder     int
  system        boolean                (true nur bei Rest-Kategorie)

CategoryTarget                         (Soll ab einem Monat, siehe R3; nur EXPENSE, nicht Rest)
  id            Long, PK
  category      → Category
  validFrom     LocalDate              (immer der 1. des Monats)
  targetAmount  BigDecimal, nullable
  targetPercent BigDecimal, nullable   (0..1)
  eindeutig: (category, validFrom)

Transaction
  id            Long, PK
  amount        BigDecimal > 0
  description   String, 1..100
  date          LocalDate
  category      → Category

YearlyTransaction
  id            Long, PK
  amountPerYear BigDecimal > 0
  description   String, 1..100
  dueDate       LocalDate, nullable  (optionale Info, "Abbuchung regulär am")
  validFrom     LocalDate            (immer der 1. des Monats, siehe R1)
  validTo       LocalDate, nullable  (1. des Monats, einschließlich; leer = unbefristet)
  category      → Category
```

Seed (aus `Einstellungen_Budget`): Fixkosten: Wohnen, Nebenkosten, Versicherungen, Mobilität, Kredite, Spenden. Flexibel: Lebensmittel, Freizeit, Urlaub, Persönlicher Bedarf, Geschenke, Gesundheit. Sparen: Investitionen, Selbstständigkeit. Rest: Sonstiges. Einnahmen: Gehalt/Lohn (netto), Nebenjob, Selbstständigkeit, Kapitalerträge, Staatliche Leistungen, Sonstiges. Die Platzhalter-Zeilen des Sheets entfallen, weil die App Kategorien frei anlegen lässt (US10).

**Seed-Beträge:** ausschließlich erfundene Werte, keine echten Zahlen (Repo ist öffentlich einsehbar).

## 5. REST-API (Skizze für OpenAPI)

Basis: `/api`, JSON. Fehler als RFC 7807 (`ProblemDetail`), Validierung per Bean Validation.

| Methode | Pfad | Zweck | Erfolg |
|---|---|---|---|
| GET | `/categories?month=2026-10` | Alle Kategorien, sortiert, mit dem in diesem Monat gültigen Soll (`targetAmount`, `targetPercent`, `targetValidFrom`) | 200, 400 (Monat fehlt/ungültig) |
| PUT | `/categories/{id}/target?month=2026-11` | Soll ab diesem Monat, Body `{targetAmount?, targetPercent?}` | 200, 404, 400 (auch Monat fehlt/ungültig), 409 (Rest- und Einnahmen-Kategorie) |
| GET | `/transactions?month=2026-10` | Transaktionen eines Monats | 200 |
| POST | `/transactions` | Anlegen | 201 + Location |
| PUT | `/transactions/{id}` | Ändern | 200, 404, 400 |
| DELETE | `/transactions/{id}` | Löschen | 204, 404 |
| GET | `/yearly-transactions?month=2026-10` | Jährliche Transaktionen, die in diesem Monat gültig sind (Should), je mit `amountPerMonth` (Jahresbetrag / 12, auf Cent gerundet, R1) | 200, 400 (Monat fehlt/ungültig) |
| POST | `/yearly-transactions` | Anlegen, Body `{amountPerYear, description, validFrom: "2026-10", validTo?, dueDate?, categoryId}` | 201 + Location, 400 |
| PUT | `/yearly-transactions/{id}` | Ändern | 200, 404, 400 |
| DELETE | `/yearly-transactions/{id}` | Löschen | 204, 404 |
| GET | `/dashboard?month=2026-10` | Berechnetes Dashboard | 200, 400 (ungültiger Monat) |

**Dashboard-Antwort (Kern):**
```json
{
  "month": "2026-10",
  "incomeTotal": 2000.00,
  "expenses": { "target": 2000.00, "actual": 1635.00, "difference": 365.00 },
  "surplus": 365.00, "surplusLabel": "Monatlicher Überschuss",
  "changePercent": 0.2232,
  "groups":    [ { "group": "FIXED", "target": 0, "actual": 0, "difference": 0 } ],
  "categories":[ { "id": 1, "name": "…", "group": "FIXED", "target": 500.00, "actual": 500.00, "actualYearly": 50.00, "difference": 0.00 } ],
  "income":    [ { "id": 20, "name": "…", "actual": 2000.00, "actualYearly": 0.00, "share": 1.0 } ],
  "yearlyExpensesShare": 50.00,
  "dueYearly": [ { "id": 3, "description": "…", "categoryName": "…", "amountPerYear": 600.00, "dueDate": "2026-10-15" } ]
}
```
`actualYearly` ist der R1-Anteil aus jährlichen Transaktionen (im `actual` bereits enthalten), `yearlyExpensesShare` und `dueYearly` siehe R12.
(Zahlen im Beispiel nur zur Form.)

## 6. Architektur

**Repo (Monorepo):** `/backend` (Maven, Java 21, Spring Boot 3), `/frontend` (Vite, React, TypeScript), `/docs` (diese Spezifikation, ADRs), `.github/workflows`, `CLAUDE.md`, `README.md`.

**Backend:** Schichten Controller → Service → Repository (Spring Data JPA, H2 als Datei-DB). **Die Berechnung liegt in einer reinen Klasse `DashboardCalculator`** ohne Spring-Abhängigkeit: Eingabe sind Kategorien, Transaktionen, Jahrestransaktionen und Monat; Ausgabe ist ein DTO. Das hält die Regeln R1 bis R9 isoliert testbar (Single Responsibility, Dependency Inversion). DTOs als Java-Records, Entities nie direkt nach außen. `springdoc-openapi` erzeugt die API-Doku aus dem Code.

**Frontend:** Seiten *Dashboard*, *Transaktionen*, *Einstellungen*. Datenzugriff mit TanStack Query, API-Typen in `src/api/`, keine Fachlogik im Frontend (nur Anzeige und Formatierung, `Intl.NumberFormat('de-DE')`). Formulare mit einfacher Validierung (Betrag > 0, Pflichtfelder).

## 7. Teststrategie

- **Unit (Pflicht):** `DashboardCalculator` mit mindestens diesen Fällen:
  1. Nur Euro-Soll, keine Prozente
  2. Nur Prozent-Soll, Einnahmen > 0
  3. Euro und Prozent gleichzeitig → Euro gewinnt (R3)
  4. Rest-Kategorie wird negativ bei Überplanung (R4)
  5. Einnahmen = 0 → keine Division durch null (R8, R9)
  6. Jahrestransaktion wird als Betrag/12 angerechnet (R1), und zwar im ersten und letzten Monat ihrer Gültigkeit, aber nicht davor oder danach
  7. Überschuss negativ → Bezeichnung "Monatlicher Verlust" (R7)
  8. Soll ab M gilt nicht für M−1, aber für M und M+1; ein neuerer Eintrag ersetzt den älteren (R3, Auflösung in `TargetResolver`)
  9. Jahresanteil je Kategorie und Summe der anteiligen Jahreskosten; fällige Abbuchung nur im Kalendermonat von `dueDate` und nur innerhalb der Gültigkeit, 29.02. im Nicht-Schaltjahr (R12)
- **Integration:** `@SpringBootTest` + MockMvc für Transaktion anlegen → Dashboard spiegelt es wider; Validierungsfehler (400); Rest-Kategorie nicht änderbar (409).
- **Frontend:** 2 bis 3 Vitest/Testing-Library-Tests (Formular-Validierung, Dashboard rendert Werte).
- **Referenzabgleich:** Eine Testfixture mit erfundenen Zahlen, deren Erwartungswerte du einmal im Sheet nachrechnest.

## 8. CI/CD und Git

- GitHub Actions bei Push und PR: Backend `mvn -B verify`; Frontend `npm ci`, `npm run lint`, `npm test`, `npm run build`.
- `main` ist geschützt (PR nötig, Pipeline grün). Je Feature ein Branch `feat/<name>` und ein PR mit Kurzbeschreibung: Ziel, Spec-Bezug (z. B. US3), was die KI erzeugt hat, was ich geändert habe.
- Commits in kleinen Schritten, aussagekräftig (Conventional Commits).

## 9. Arbeitsweise mit Claude Code

`CLAUDE.md` im Repo-Root enthält: Projektziel in 3 Sätzen, Stack und Versionen, Befehle (build/test/lint), Architekturregeln (Rechenlogik nur im `DashboardCalculator`, kein `double` für Geld, DTOs statt Entities), Verweis auf `docs/spezifikation.md`, Definition of Done (Tests grün, Lint grün, PR-Text ausgefüllt).

**Ablauf je Feature:** (1) Spec-Abschnitt und Akzeptanzkriterien als Kontext geben → (2) Plan-Modus: Plan prüfen und korrigieren → (3) Umsetzung, Tests zuerst bei der Berechnung → (4) eigenes Review des Diffs (Fachlichkeit, Technik, Architektur) → (5) PR.

## 10. Arbeitspakete (Reihenfolge und Zeit)

| AP | Inhalt | Zeit | Ergebnis |
|---|---|---|---|
| 0 | Umgebung prüfen (JDK 21, Node, Git, Claude Code, GitHub-Repo) | 0,25 h | Hello-World läuft |
| 1 | Repo, `CLAUDE.md`, Spezifikation einchecken, Backend- und Frontend-Gerüst, CI-Pipeline grün | 0,75 h | Pipeline grün |
| 2 | Datenmodell, Seed, Kategorien-API (US1, US2) | 1,0 h | `/categories` + Tests |
| 3 | Transaktions-API (US3, US4) | 0,75 h | CRUD + Validierung |
| 4 | `DashboardCalculator` + `/dashboard` (US5), Unit-Tests zuerst | 1,5 h | Referenzfälle grün |
| 5 | Frontend: Dashboard und Transaktionen (US5, US3) | 1,5 h | Durchgehender Ablauf |
| 6 | Frontend: Einstellungen/Soll (US2), Monatsnavigation (US8) | 0,75 h | |
| 7 | Jahrestransaktionen (US6), Gruppensummen (US7), falls Zeit | 0,75 h | Should-Features |
| 8 | Tests ergänzen, Review der KI-Ergebnisse, README mit "Learnings und Entscheidungen" und Interview-Stichpunkten | 1,0 h | |
| | **Summe** | **8,25 h** | |

**Kürzungsregel:** Wird es knapp, entfällt AP 7 vollständig (Should), danach US8. Nicht kürzen: AP 4 (Berechnung mit Tests), CI und README.

**Der erste vertikale Schnitt** (Transaktion erfassen → im Dashboard sehen) soll nach AP 5 stehen, damit früh etwas Vorzeigbares existiert.

## 11. Entscheidungen (geklärt am 17.10.2026)

1. **Sprache:** UI auf Deutsch; Code, Bezeichner, Kommentare und Commit-Messages auf Englisch.
2. **Jahrestransaktionen:** bleiben "Should" (AP 7). Im Zweifel entfallen sie, ohne dass der MVP leidet.
3. **Monatszuordnung:** Eine Transaktion zählt nach ihrem Datum, nicht nach Blatt wie im Sheet.
4. **Repo:** öffentlich auf GitHub. Daher: nur erfundene Seed-Daten, keine echten Beträge, keine Zugangsdaten, keine persönlichen Daten in Commits, Issues oder README.
5. **Gültigkeit jährlicher Transaktionen** (18.10.2026): Wie das Soll (R3) gilt eine jährliche Transaktion ab einem Monat und optional bis zu einem Monat. So verändert ein neu erfasster Vertrag keine vergangenen Monate. Im Sheet gab es diese Unterscheidung nicht.
