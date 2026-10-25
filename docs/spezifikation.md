# Spezifikation MVP: Budget-Tracker (Web-App)

Stand: 23.10.2026 · Basis: Google Sheet "Budget + Investment Planer V1.1.0" (nur Budget-Blätter) · Stack laut Stellenanzeige marcapo: Java + Spring Boot (REST) und TypeScript + React · Ziel: ca. 8 h Arbeitszeit, fertig bis Di 20.10.2026 · Ausbau ab 23.10.2026 siehe Abschnitt 13

## 1. Ziel und Scope

**Ziel:** Eine schlanke Web-App, die das Budget-Blatt des Sheets abbildet: Soll-Verteilung festlegen, Ausgaben und Einnahmen erfassen, Dashboard mit Soll/Ist/Abweichung. Das Projekt zeigt vor allem den Prozess: Spezifikation → Plan → Umsetzung mit Claude Code → Review → Tests → CI.

**Im MVP (drin):**
- Kategorien mit Soll-Werten (Euro oder Prozent der Einnahmen)
- Transaktionen erfassen (Einnahmen positiv, Ausgaben negativ, mit Datum)
- Wiederkehrende Transaktionen (monatlich, vierteljährlich, halbjährlich, jährlich), die im Monat ihrer Abbuchung mit vollem Betrag zählen
- Dashboard pro Monat: Soll, Ist, Abweichung zum Soll, nicht verplante Einnahmen, Überschuss, Einnahmenanteile
- Auswertung: durchschnittliche monatliche Kosten und nötige Rücklagen für wiederkehrende Ausgaben

**Bewusst draußen (im README als Ausblick):**
- Alle Investment-Blätter (Sparplan, Prognosen, Freistellungsauftrag, Einmalzahlung)
- Import aus dem Investment-Tracker (im Sheet ist B7 der Monatstransaktionen eine Verknüpfung dorthin; im MVP ist "Investitionen" eine normale Kategorie)
- Benutzerverwaltung/Login (Single-User, keine Authentifizierung)
- Mehrere Währungen, Export/Import, Mobile-Optimierung

## 2. Fachliche Regeln (aus den Formeln des Sheets abgeleitet)

Sei `M` der gewählte Monat und `N(k)` das Netto der Kategorie `k` in M (R1).

| # | Regel | Quelle im Sheet |
|---|---|---|
| R1 | **Netto je Kategorie** `N(k)` = Summe der Beträge (mit Vorzeichen, R11) aller Transaktionen der Kategorie in M + Summe der Beträge aller Vorkommen wiederkehrender Transaktionen der Kategorie, die in M liegen (R12). Ein Vorkommen zählt mit seinem vollen Betrag; das Ist bildet den echten Geldfluss ab. | `Dashboard_Budget!E39:E59`, `K39:K46`; bewusste Abweichung: keine Verteilung von Jahresbeträgen auf 12 Monate |
| R2 | **Einnahmen und Ausgaben:** Eine Kategorie mit `N(k) > 0` zählt in M als Einnahme, eine mit `N(k) < 0` als Ausgabe. **Einnahmen gesamt (Ist)** `E` = Summe aller positiven `N(k)`. **Ausgaben-Ist je Kategorie** `I(k)` = `−N(k)`, wenn `N(k) < 0`, sonst 0. **Ausgaben gesamt (Ist)** `A` = Summe aller `I(k)`. Erstattungen (positive Beträge in einer Ausgaben-Kategorie) mindern so das Ist ihrer Kategorie. | `K37` |
| R3 | **Soll je Kategorie der Gruppen Fixkosten, Flexibel und Sparen:** Maßgeblich ist der Soll-Eintrag der Kategorie mit dem spätesten `validFrom ≤ M` (gilt ab M für alle folgenden Monate, bis ein neuerer Eintrag ihn ersetzt). Gibt es keinen solchen Eintrag oder sind darin beide Werte leer: kein Soll. Innerhalb des Eintrags gilt: Ist ein Euro-Wert gesetzt, gilt er. Sonst, wenn ein Prozentwert gesetzt ist: `E` × Prozent. Sonst: kein Soll. Der Euro-Wert hat immer Vorrang. Ein Soll ist immer ein Ausgabenbudget (Betrag ≥ 0). | `Einstellungen_Budget!F11:F30` |
| R4 | **Nicht verplant** = `E` − `P`, wobei `P` die Summe aller Soll-Werte (R3) ist. Zeigt, welcher Teil der Einnahmen kein Budget hat. Darf negativ sein (Überplanung). Eine reine Rechengröße im Dashboard, keine Kategorie. | `F31`, `G31`; bewusste Abweichung: im Sheet eine berechnete Rest-Kategorie, die im Soll gesamt mitzählte |
| R5 | **Abweichung zum Soll je Kategorie** = Soll − `I(k)`. Positiv heißt unter Budget, negativ über Budget. Ohne Soll: leer. | `F39:F59` |
| R6 | **Gesamt:** Soll gesamt = `P` (ohne nicht verplante Einnahmen); Ist gesamt = `A`; Abweichung zum Soll gesamt = `P` − `A` | `D37:F37`; bewusste Abweichung: im Sheet war Soll gesamt immer `E` und die Differenz damit gleich dem Überschuss |
| R7 | **Überschuss** = `E` − `A` (gleich der Summe aller `N(k)`, und gleich Nicht verplant + Abweichung zum Soll gesamt). Bezeichnung: "Monatlicher Überschuss" bei ≥ 0, sonst "Monatlicher Verlust". | `I17`, `I18` |
| R8 | **Sparquote** = Überschuss (R7) / `E` als Prozent; bei `E` = 0: leer. Bei Verlust negativ. Wird im Dashboard beim monatlichen Überschuss mit angezeigt. | bewusste Abweichung vom Sheet (`I15`, `I16`: Einnahmen / Ausgaben − 1, wächst bei kleinen Ausgaben unbegrenzt) |
| R9 | **Einnahmenanteil** je Kategorie mit `N(k) > 0` = `N(k)` / `E`. Die Einnahmen-Liste zeigt alle Kategorien ohne Gruppe (auch mit 0,00) sowie jede Kategorie mit Gruppe, deren `N(k) > 0` ist; Anteil bei `N(k) ≤ 0`: 0 %. | `L39:L46` |
| R10 | **Gruppen:** Kategorien gehören zu Fixkosten, Flexible Ausgaben, Sparen & Investieren oder zu keiner Gruppe (typisch für Einnahmen). Zu den drei Gruppen zeigt das Dashboard je eine Empfehlung als Anteil an den Einnahmen: Fixkosten ca. 50 %, Flexible Ausgaben ca. 30 %, Sparen & Investieren ca. 20 % (50-30-20-Regel). Alle drei Werte sind reine Empfehlungen: Sie werden nur angezeigt, nicht berechnet, nicht geprüft und nicht als Soll verwendet. Gruppensummen für Soll und Ist (`I(k)`) werden angezeigt. Haben Kategorien ohne Gruppe Ausgaben, erscheint zusätzlich die Zeile "Ohne Gruppe" (Soll 0, Abweichung = −Ist), damit die Gruppensummen `P` und `A` ergeben. Die Ausgaben-Liste zeigt alle Kategorien mit Gruppe sowie jede Kategorie ohne Gruppe, deren `N(k) < 0` ist. | `Einstellungen_Budget!H11:J31` |
| R11 | **Vorzeichen:** Das Vorzeichen des Betrags entscheidet: positiv = Einnahme, negativ = Ausgabe. Ein Betrag von 0 ist ungültig. Kategorien haben keinen Typ. Im Formular wird der Betrag immer positiv eingegeben; ein Umschalter "Ausgabe / Einnahme" (Vorgabe: Ausgabe) setzt das Vorzeichen. | bewusste Abweichung vom Sheet (dort entschied die Kategorie) |
| R12 | **Vorkommen wiederkehrender Transaktionen:** Eine wiederkehrende Transaktion hat ein Intervall (monatlich = 1, vierteljährlich = 3, halbjährlich = 6, jährlich = 12 Monate), ein Startdatum und optional ein Enddatum (einschließlich). Ihre Vorkommen liegen am Startdatum plus k × Intervall Monate (k = 0, 1, 2, …), jeweils am Tag des Startdatums; hat ein Monat diesen Tag nicht, am letzten Tag des Monats (31.01. → 28.02. bzw. 29.02., 30.04.; 29.02. → 28.02. in Nicht-Schaltjahren). Ein Vorkommen zählt, wenn es nicht nach dem Enddatum liegt und für sein Datum keine Ausnahme (R13) existiert. Vorkommen werden berechnet, nicht als Transaktionen gespeichert. | neu, nicht im Sheet |
| R13 | **Vorkommen bearbeiten oder löschen:** Der Nutzer wählt "Nur dieser Eintrag" oder "Dieser und alle folgenden". Sei `d` das gewählte Vorkommen. **Nur dieser Eintrag:** Für `d` wird eine Ausnahme gespeichert, das Vorkommen zählt nicht mehr. Beim Bearbeiten wird zusätzlich eine normale Transaktion mit den geänderten Werten angelegt (Datum frei wählbar, Vorschlag `d`). **Dieser und alle folgenden:** Ist `d` das erste Vorkommen, wird die Serie selbst geändert bzw. gelöscht (beim Löschen samt ihrer Ausnahmen). Sonst wird das Enddatum der Serie auf den Tag vor `d` gesetzt, Ausnahmen ab `d` entfallen, und beim Bearbeiten entsteht eine neue Serie mit den geänderten Werten und derselben `seriesId`; ihr Startdatum (Vorschlag `d`) muss nach dem vorherigen Vorkommen liegen. Vergangene Vorkommen werden nie über die Serie rückwirkend geändert; eine Option "Alle Einträge" gibt es nicht. | neu, nicht im Sheet |
| R14 | **Auswertung Rücklagen für M:** Eine Serie ist in M aktiv, wenn ihr Startdatum nicht nach dem letzten Tag von M und ihr Enddatum (falls gesetzt) nicht vor dem ersten Tag von M liegt. **Ø monatliche Kosten** = Summe von \|Betrag\| / Intervall in Monaten über alle in M aktiven Serien mit negativem Betrag. **Davon Rücklage** = derselbe Wert nur für Serien mit Intervall > 1 Monat; das ist der Betrag, der monatlich zurückgelegt werden muss. **Vorschau:** Für M und die folgenden 11 Monate je Monat die Summe von \|Betrag\| aller Vorkommen von Serien mit negativem Betrag (R12, Ausnahmen beachtet), mit Liste der Vorkommen. Einzelne Transaktionen und Serien mit positivem Betrag zählen nicht. | neu, nicht im Sheet |

**Nicht übernehmen:** Die Hilfsblätter `IGNORE…` (Diagrammdaten, rechnet das Frontend selbst) sowie veraltete Formeln in `Dashboard_Budget` (Zeilen 38 und 48–55 der Einnahmenspalte verweisen auf ein nicht existierendes Blatt `Monatliche_Transaktionen`). Als Referenzwerte gelten die Formeln, nicht diese Reste.

**Geldbeträge:** `BigDecimal` im Backend (Skala 2, Rundung HALF_UP), im JSON als String oder Zahl mit 2 Nachkommastellen. Kein `double`.

## 3. User Stories und Akzeptanzkriterien

### Must
- **US1 Kategorien ansehen:** Als Nutzer sehe ich alle Kategorien gruppiert (Fixkosten, Flexible Ausgaben, Sparen & Investieren, Ohne Gruppe). *AK:* Beim ersten Start sind die Kategorien des Sheets als Seed vorhanden; jeder Kategoriename ist eindeutig.
- **US2 Soll festlegen:** Als Nutzer lege ich je Kategorie der Gruppen Fixkosten, Flexibel und Sparen ab einem gewählten Monat ein Soll in Euro oder in Prozent fest. Es gilt für diesen und alle folgenden Monate, bis ein neuerer Eintrag es ersetzt; frühere Monate bleiben unverändert. *AK:* R3 gilt; Prozent zwischen 0 und 100; negativer Euro-Wert wird abgelehnt (400 mit Fehlermeldung); erneutes Speichern für denselben Monat überschreibt den Eintrag dieses Monats; beide Werte leer heißt "kein Soll ab diesem Monat"; für Kategorien ohne Gruppe wird ein Soll abgelehnt (409).
- **US3 Transaktion erfassen:** Als Nutzer erfasse ich Betrag, Beschreibung, Datum und Kategorie und wähle per Umschalter "Ausgabe" oder "Einnahme". *AK:* R11; eingegebener Betrag > 0, gespeichert mit Vorzeichen (API: Betrag ≠ 0); Beschreibung höchstens 100 Zeichen; Kategorie muss existieren (sonst 400); Datum Pflicht.
- **US4 Transaktion ändern/löschen.**
- **US5 Dashboard:** Als Nutzer wähle ich einen Monat und sehe Soll, Ist, Abweichung zum Soll je Kategorie und Gesamt, die nicht verplanten Einnahmen sowie den Überschuss. *AK:* R1 bis R10; Werte entsprechen der Testfixture F1 (Abschnitt 12).

### Should
- **US6 Wiederkehrende Transaktionen erfassen:** Als Nutzer lege ich eine wiederkehrende Transaktion an: Betrag mit Umschalter "Ausgabe / Einnahme", Beschreibung, Kategorie, Intervall (monatlich, vierteljährlich, halbjährlich, jährlich), Startdatum (= erste Abbuchung), optional Enddatum. *AK:* R11, R12; Betrag > 0 eingegeben, Beschreibung höchstens 100 Zeichen, Kategorie muss existieren (sonst 400), Startdatum Pflicht, Enddatum nicht vor dem Startdatum (sonst 400); die Vorkommen erscheinen in der Transaktionsliste ihres Monats, als "wiederkehrend" gekennzeichnet, und zählen im Dashboard mit vollem Betrag (R1).
- **US6a Wiederkehrende Einträge bearbeiten/löschen:** Als Nutzer bearbeite oder lösche ich ein Vorkommen und wähle "Nur dieser Eintrag" oder "Dieser und alle folgenden". *AK:* R13; frühere Vorkommen bleiben unverändert; ein Datum, das kein Vorkommen der Serie ist, ergibt 404.
- **US7 Gruppensummen** nach R10 im Dashboard.
- **US8 Monatsnavigation** (vor/zurück) und Anzeige leerer Monate ohne Fehler.
- **US13 Auswertung Rücklagen:** Als Nutzer sehe ich für den gewählten Monat die durchschnittlichen monatlichen Kosten meiner wiederkehrenden Ausgaben, davon die nötige monatliche Rücklage, und eine Vorschau der nächsten 12 Monate, die teure Monate sichtbar macht. *AK:* R14.

### Nice
- **US9 Diagramm** (Soll gegen Ist je Gruppe) mit Recharts.
- **US10 Kategorien anlegen/umbenennen/löschen** (Name eindeutig, Gruppe optional; löschen nur ohne Transaktionen und wiederkehrende Transaktionen).
- **US11 Flyway-Migrationen** statt Seeder.
- **US12 Kategorie aufklappen (vorgemerkt, später):** Im Dashboard zeigt ein Klick auf eine Kategorie ihre Buchungen und Vorkommen wiederkehrender Transaktionen im Monat (R1, R12). Passt gut zusammen mit US9.

## 4. Datenmodell

```
Category
  id            Long, PK
  name          String, eindeutig, nicht leer
  group         FIXED | FLEXIBLE | SAVINGS | null   (null = ohne Gruppe, typisch Einnahmen)
  sortOrder     int

CategoryTarget                         (Soll ab einem Monat, siehe R3; nur Gruppen FIXED, FLEXIBLE, SAVINGS)
  id            Long, PK
  category      → Category
  validFrom     LocalDate              (immer der 1. des Monats)
  targetAmount  BigDecimal, nullable
  targetPercent BigDecimal, nullable   (0..1)
  eindeutig: (category, validFrom)

Transaction
  id            Long, PK
  amount        BigDecimal ≠ 0         (positiv = Einnahme, negativ = Ausgabe, R11)
  description   String, 1..100
  date          LocalDate
  category      → Category

RecurringTransaction                   (eine Serie, siehe R12)
  id            Long, PK
  seriesId      UUID                   (gleich für alle Teilstücke nach "Dieser und alle folgenden", R13)
  amount        BigDecimal ≠ 0         (je Vorkommen, mit Vorzeichen)
  description   String, 1..100
  category      → Category
  interval      MONTHLY | QUARTERLY | HALF_YEARLY | YEARLY
  startDate     LocalDate              (erstes Vorkommen)
  endDate       LocalDate, nullable    (einschließlich; leer = unbefristet)

RecurringException                     (ausgesetztes Vorkommen, siehe R13)
  id             Long, PK
  recurring      → RecurringTransaction
  occurrenceDate LocalDate
  eindeutig: (recurring, occurrenceDate)
```

`YearlyTransaction` entfällt; jährliche Transaktionen sind wiederkehrende Transaktionen mit Intervall `YEARLY`.

Seed (aus `Einstellungen_Budget`): Fixkosten: Wohnen, Nebenkosten, Versicherungen, Mobilität, Kredite, Spenden. Flexibel: Lebensmittel, Freizeit, Urlaub, Persönlicher Bedarf, Geschenke, Gesundheit. Sparen: Investitionen, Selbstständigkeit. Ohne Gruppe: Gehalt/Lohn (netto), Nebenjob, Einnahmen Selbstständigkeit, Kapitalerträge, Staatliche Leistungen, Sonstige Einnahmen. Die Platzhalter-Zeilen des Sheets entfallen, weil die App Kategorien frei anlegen lässt (US10). Der im Sheet doppelte Name "Selbstständigkeit" ist umbenannt, weil Namen eindeutig sind. Die berechnete Rest-Kategorie des Sheets entfällt (R4).

**Seed-Beträge:** ausschließlich erfundene Werte, keine echten Zahlen (Repo ist öffentlich einsehbar).

## 5. REST-API (Skizze für OpenAPI)

Basis: `/api`, JSON. Fehler als RFC 7807 (`ProblemDetail`), Validierung per Bean Validation. Faustregel: Unbekannte Ressource in der URL → 404; ungültiger Inhalt im Body oder in Parametern (auch eine unbekannte `categoryId`) → 400.

| Methode | Pfad | Zweck | Erfolg |
|---|---|---|---|
| GET | `/categories?month=2026-10` | Alle Kategorien, sortiert, mit dem in diesem Monat gültigen Soll (`targetAmount`, `targetPercent`, `targetValidFrom`) | 200, 400 (Monat fehlt/ungültig) |
| PUT | `/categories/{id}/target?month=2026-11` | Soll ab diesem Monat, Body `{targetAmount?, targetPercent?}` | 200, 404, 400 (auch Monat fehlt/ungültig), 409 (Kategorien ohne Gruppe) |
| GET | `/transactions?month=2026-10` | Einträge eines Monats: Transaktionen (`kind: "SINGLE"`, `id`) und Vorkommen wiederkehrender Transaktionen (`kind: "RECURRING"`, `recurringId`, `occurrenceDate`), je mit `amount` (mit Vorzeichen), `description`, `date`, Kategorie | 200, 400 (Monat fehlt/ungültig) |
| POST | `/transactions` | Anlegen, Body `{amount, description, date, categoryId}` | 201 + Location, 400 |
| PUT | `/transactions/{id}` | Ändern | 200, 404, 400 |
| DELETE | `/transactions/{id}` | Löschen | 204, 404 |
| GET | `/recurring-transactions?month=2026-10` | Serien, die in diesem Monat aktiv sind (R14), je mit `monthlyEquivalent` (\|Betrag\| / Intervall in Monaten, auf Cent gerundet) | 200, 400 (Monat fehlt/ungültig) |
| POST | `/recurring-transactions` | Anlegen, Body `{amount, description, categoryId, interval, startDate, endDate?}` | 201 + Location, 400 |
| PUT | `/recurring-transactions/{id}/occurrences/{date}?scope=THIS` | Nur dieses Vorkommen ändern (R13), Body wie `POST /transactions` | 200, 404 (Serie oder Vorkommen unbekannt), 400 |
| PUT | `/recurring-transactions/{id}/occurrences/{date}?scope=FOLLOWING` | Dieses und alle folgenden ändern (R13), Body wie `POST /recurring-transactions` | 200, 404, 400 |
| DELETE | `/recurring-transactions/{id}/occurrences/{date}?scope=THIS\|FOLLOWING` | Löschen (R13) | 204, 404, 400 (Scope fehlt/ungültig) |
| GET | `/dashboard?month=2026-10` | Berechnetes Dashboard | 200, 400 (ungültiger Monat) |
| GET | `/reserves?month=2026-10` | Auswertung Rücklagen (R14) | 200, 400 (ungültiger Monat) |

**Dashboard-Antwort (Kern):**
```json
{
  "month": "2026-10",
  "incomeTotal": 2000.00,
  "expenses": { "target": 1800.00, "actual": 1635.00, "difference": 165.00 },
  "unplanned": 200.00,
  "surplus": 365.00, "surplusLabel": "Monatlicher Überschuss",
  "savingsRate": 0.1825,
  "groups":    [ { "group": "FIXED", "target": 0, "actual": 0, "difference": 0 } ],
  "categories":[ { "id": 1, "name": "…", "group": "FIXED", "target": 500.00, "actual": 500.00, "difference": 0.00 } ],
  "income":    [ { "id": 20, "name": "…", "actual": 2000.00, "share": 1.0 } ]
}
```
`difference` ist die Abweichung zum Soll (R5, R6), `unplanned` sind die nicht verplanten Einnahmen (R4), `groups[].group` ist `null` für die Zeile "Ohne Gruppe" (R10). `categories[].actual` ist `I(k)`, `income[].actual` ist `N(k)` (R2, R9). (Zahlen im Beispiel nur zur Form.)

**Rücklagen-Antwort (Kern):**
```json
{
  "month": "2026-10",
  "averageMonthlyCosts": 935.00,
  "reserve": 20.00,
  "series":   [ { "id": 3, "description": "…", "categoryName": "…", "amount": -240.00, "interval": "YEARLY", "monthlyEquivalent": 20.00 } ],
  "forecast": [ { "month": "2026-10", "total": 1140.00,
                  "occurrences": [ { "recurringId": 3, "description": "…", "date": "2026-10-15", "amount": 240.00 } ] } ]
}
```
`forecast` hat genau 12 Einträge (M bis M+11). Beträge in `forecast` sind Kosten und daher positiv.

## 6. Architektur

**Repo (Monorepo):** `/backend` (Maven, Java 21, Spring Boot 3), `/frontend` (Vite, React, TypeScript), `/docs` (diese Spezifikation, ADRs), `.github/workflows`, `CLAUDE.md`, `README.md`.

**Backend:** Schichten Controller → Service → Repository (Spring Data JPA, H2 als Datei-DB). **Die Berechnungen liegen in reinen Klassen** ohne Spring-Abhängigkeit: `RecurrenceSchedule` berechnet die Vorkommen einer Serie in einem Zeitraum (R12), `DashboardCalculator` nimmt Kategorien, Transaktionen, Vorkommen und Monat und liefert das Dashboard-DTO (R1 bis R10), `ReserveCalculator` liefert die Auswertung (R14). Die Aufteilung von Serien (R13) liegt im Service. Das hält die Regeln isoliert testbar (Single Responsibility, Dependency Inversion). DTOs als Java-Records, Entities nie direkt nach außen. `springdoc-openapi` erzeugt die API-Doku aus dem Code.

**Frontend:** Seiten *Dashboard*, *Transaktionen* (inkl. Vorkommen wiederkehrender Transaktionen; beim Bearbeiten oder Löschen eines Vorkommens fragt ein Dialog "Nur dieser Eintrag" oder "Dieser und alle folgenden"), *Wiederkehrend* (Serien anlegen und ansehen, ersetzt *Jährliche Transaktionen*), *Auswertung* (R14), *Einstellungen*. Datenzugriff mit TanStack Query, API-Typen in `src/api/`, keine Fachlogik im Frontend (nur Anzeige und Formatierung, `Intl.NumberFormat('de-DE')`). Formulare mit einfacher Validierung (Betrag > 0, Pflichtfelder); der Umschalter "Ausgabe / Einnahme" setzt nur das Vorzeichen.

## 7. Teststrategie

- **Unit (Pflicht):** `DashboardCalculator` mit mindestens diesen Fällen:
  1. Nur Euro-Soll, keine Prozente
  2. Nur Prozent-Soll, Einnahmen > 0
  3. Euro und Prozent gleichzeitig → Euro gewinnt (R3)
  4. Nicht verplant = `E` − `P`, negativ bei Überplanung; Soll gesamt enthält es nicht (R4, R6)
  5. Einnahmen = 0 → keine Division durch null (R8, R9)
  6. Vorkommen einer wiederkehrenden Transaktion zählt mit vollem Betrag im Monat des Vorkommens, in anderen Monaten nicht (R1)
  7. Überschuss negativ → Bezeichnung "Monatlicher Verlust" (R7)
  8. Soll ab M gilt nicht für M−1, aber für M und M+1; ein neuerer Eintrag ersetzt den älteren (R3, Auflösung in `TargetResolver`)
  9. Vorzeichen (R2, R9, R10, R11): Erstattung mindert das Ist ihrer Kategorie; eine Kategorie mit Gruppe und positivem Netto zählt als Einnahme; Ausgaben einer Kategorie ohne Gruppe zählen zur Zeile "Ohne Gruppe"; Gruppensummen ergeben `P` und `A`
- **Unit:** `RecurrenceSchedule` (R12): je Intervall die richtigen Vorkommen; Tag 31 und 29.02. auf den Monatsletzten; Enddatum einschließlich; Ausnahmen werden ausgelassen; kein Vorkommen vor dem Startdatum.
- **Unit:** `ReserveCalculator` (R14): Ø-Kosten über alle Intervalle, Rücklage nur für Intervall > 1, Einnahmen-Serien und inaktive Serien zählen nicht, Vorschau über 12 Monate mit Ausnahmen.
- **Integration:** `@SpringBootTest` + MockMvc für Transaktion anlegen → Dashboard spiegelt es wider; Validierungsfehler (400, auch Betrag 0); Soll für Kategorien ohne Gruppe abgelehnt (409); Serie anlegen → Vorkommen in `/transactions` und `/dashboard`; R13 "Nur dieser Eintrag" (Ausnahme + Ersatz-Transaktion) und "Dieser und alle folgenden" (erstes Vorkommen: Serie geändert; späteres Vorkommen: Serie geteilt, frühere Monate unverändert); unbekanntes Vorkommen → 404.
- **Frontend:** 2 bis 3 Vitest/Testing-Library-Tests (Formular-Validierung mit Umschalter, Dashboard rendert Werte, Dialog für Bearbeiten eines Vorkommens).
- **Referenzabgleich:** Eine Testfixture mit erfundenen Zahlen (Abschnitt 12).

## 8. CI/CD und Git

- GitHub Actions bei Push und PR: Backend `mvn -B verify`; Frontend `npm ci`, `npm run lint`, `npm test`, `npm run build`.
- `main` ist geschützt (PR nötig, Checks `backend` und `frontend` grün, kein Force-Push, kein Löschen). Je Feature ein Branch `feat/<name>` und ein PR mit Kurzbeschreibung: Ziel, Spec-Bezug (z. B. US3), was die KI erzeugt hat, was ich geändert habe.
- Commits in kleinen Schritten, aussagekräftig (Conventional Commits).

## 9. Arbeitsweise mit Claude Code

`CLAUDE.md` im Repo-Root enthält: Projektziel in 3 Sätzen, Stack und Versionen, Befehle (build/test/lint), Architekturregeln (Rechenlogik nur in den reinen Rechenklassen, kein `double` für Geld, DTOs statt Entities), Verweis auf `docs/spezifikation.md`, Definition of Done (Tests grün, Lint grün, PR-Text ausgefüllt).

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

**Stand 23.10.2026:** AP 0 bis 7 erledigt (PR #1 bis #10). AP 8 offen: das README-Kapitel folgt nach dem Ausbau (Abschnitt 13), damit es die neuen Entscheidungen enthält.

**Kürzungsregel:** Wird es knapp, entfällt AP 7 vollständig (Should), danach US8. Nicht kürzen: AP 4 (Berechnung mit Tests), CI und README.

**Der erste vertikale Schnitt** (Transaktion erfassen → im Dashboard sehen) soll nach AP 5 stehen, damit früh etwas Vorzeigbares existiert.

## 11. Entscheidungen

Geklärt am 17.10.2026:

1. **Sprache:** UI auf Deutsch; Code, Bezeichner, Kommentare und Commit-Messages auf Englisch.
2. **Jahrestransaktionen:** bleiben "Should" (AP 7). Im Zweifel entfallen sie, ohne dass der MVP leidet. *Ersetzt durch Entscheidung 6.*
3. **Monatszuordnung:** Eine Transaktion zählt nach ihrem Datum, nicht nach Blatt wie im Sheet.
4. **Repo:** öffentlich auf GitHub. Daher: nur erfundene Seed-Daten, keine echten Beträge, keine Zugangsdaten, keine persönlichen Daten in Commits, Issues oder README.
5. **Gültigkeit jährlicher Transaktionen** (18.10.2026): Wie das Soll (R3) gilt eine jährliche Transaktion ab einem Monat und optional bis zu einem Monat. So verändert ein neu erfasster Vertrag keine vergangenen Monate. Im Sheet gab es diese Unterscheidung nicht. *Ersetzt durch Entscheidung 6 (Start- und Enddatum je Serie).*

Geklärt am 23.10.2026:

6. **Wiederkehrende Transaktionen statt jährlicher, Ist = echter Geldfluss:** Statt Jahresbeträge auf 12 Monate zu verteilen, gibt es wiederkehrende Transaktionen mit Intervall (monatlich bis jährlich). Ein Vorkommen zählt mit vollem Betrag im Monat seiner Abbuchung (R1, R12). So stimmt das Ist mit dem Kontoauszug überein, und regelmäßige Buchungen wie die Miete müssen nicht jeden Monat neu erfasst werden. Die alte Regel R12 (anteilige Jahresbeträge und Hinweis auf fällige Abbuchungen) und das alte US6a entfallen.
7. **Rücklagen als Auswertung:** Die geglättete Sicht (was muss ich monatlich zurücklegen, um teure Monate zu decken?) gehört in eine eigene Auswertung (R14, US13), nicht ins Ist.
8. **Bearbeiten von Serien durch Teilen:** "Dieser und alle folgenden" beendet die alte Serie vor dem gewählten Vorkommen und legt eine neue an; "Nur dieser Eintrag" speichert eine Ausnahme und bei Bedarf eine normale Transaktion (R13). Vorkommen werden berechnet, nicht gespeichert; `Transaction` (Buchung) und `RecurringTransaction` (Regel) bleiben bewusst getrennte Tabellen (geprüft am 25.10.2026: eine gemeinsame Tabelle oder gespeicherte Vorkommen hätten mehr leere Spalten bzw. einen Erzeugungsmechanismus gebraucht). Rückwirkendes Ändern aller Vorkommen ist bewusst nicht möglich, damit vergangene Monate stimmen.
9. **Vorzeichen statt Kategorietyp:** Das Vorzeichen des Betrags entscheidet über Einnahme oder Ausgabe (R11); Kategorien haben keinen Typ mehr und eindeutige Namen. Einnahmen und Ausgaben werden je Kategorie saldiert (R2), damit Erstattungen das Ist ihrer Kategorie mindern und die Gruppensummen die Ausgaben ergeben. Gegen vergessene Minuszeichen setzt im Formular ein Umschalter das Vorzeichen.
10. **Keine Datenmigration:** Daten gibt es nur in lokalen Entwicklungsdatenbanken mit erfundenen Werten. Nach dem Umbau wird `backend/data/` gelöscht und neu befüllt.
11. **Rest-Kategorie nicht buchbar:** "Sonstiges (berechneter Rest)" ist eine reine Rechengröße (R4): Sie zeigt, welcher Teil der Einnahmen nicht verplant ist. Buchungen darauf werden abgelehnt (400), damit Ausgaben immer in einer echten Kategorie landen. *Ersetzt durch Entscheidung 12.*

Geklärt am 25.10.2026:

12. **Nicht verplant statt Rest-Kategorie:** Zählte der Rest im Soll gesamt mit, war Soll gesamt immer `E` und die Differenz gesamt immer gleich dem Überschuss. Eine unvollständige Soll-Verteilung fiel so nicht auf. Jetzt ist Soll gesamt nur die Summe der Soll-Werte (R6), und "Nicht verplant" (R4) ist ein eigener Wert im Dashboard. Die drei Werte beantworten verschiedene Fragen: Nicht verplant (Wie viel der Einnahmen hat kein Budget?), Abweichung zum Soll (Halte ich mein Budget ein?) und Überschuss (Was bleibt übrig?); es gilt Überschuss = Nicht verplant + Abweichung. Die System-Kategorie, die Gruppe Rest und das Feld `system` entfallen; Ausgaben ohne Gruppe erscheinen als Gruppenzeile "Ohne Gruppe" (R10). "Differenz" heißt in der UI jetzt "Abweichung zum Soll". Keine Datenmigration (Entscheidung 10).

## 12. Testfixture F1 und Sonderfall Einnahmen = 0

Alle Zahlen sind erfunden und durchgerechnet. Sie dienen als End-to-End-Test für `DashboardCalculator` und `ReserveCalculator` (ergänzend zu den Fällen aus Abschnitt 7).

**Monat:** 2026-10 · **Einnahmen:** Gehalt/Lohn (netto) +3000,00 · Nebenjob +200,00 → `E` = 3200,00

**Soll-Einstellungen (gültig ab 2026-10, R3):**

| Kategorie | Gruppe | Einstellung |
|---|---|---|
| Wohnen | FIXED | 900,00 € |
| Nebenkosten | FIXED | 10 % |
| Versicherungen | FIXED | 100,00 € |
| Mobilität | FIXED | 60,00 € **und** 50 % (Euro gewinnt, R3) |
| Lebensmittel | FLEXIBLE | 400,00 € |
| Freizeit | FLEXIBLE | 5 % |
| Investitionen | SAVINGS | 20 % |

**Transaktionen im Oktober:** Gehalt/Lohn (netto) +3000,00 · Nebenjob +200,00 · Nebenkosten −200,00 und −140,00 · Lebensmittel −120,30, −200,20 und −130,00 · Erstattung Lebensmittel +30,00 · Mobilität −45,99 · Investitionen −640,00 · Geschenke −20,00 (ohne Soll)

**Wiederkehrende Transaktionen:**
- Wohnen −900,00, monatlich ab 2026-01-01, unbefristet (Vorkommen 2026-10-01)
- Versicherungen −240,00, jährlich ab 2026-10-15, unbefristet (Vorkommen 2026-10-15, nächstes 2027-10-15)
- Freizeit −15,00, monatlich ab 2026-01-05, unbefristet, mit Ausnahme für 2026-10-05 ("Nur dieser Eintrag" gelöscht)

**Muss ignoriert werden:** Lebensmittel −999,00 am 2026-09-30

**Erwartete Ergebnisse Dashboard:**

| Kategorie | Soll | Ist | Abweichung |
|---|---|---|---|
| Wohnen | 900,00 | 900,00 | 0,00 |
| Nebenkosten | 320,00 | 340,00 | -20,00 |
| Versicherungen | 100,00 | 240,00 | -140,00 |
| Mobilität | 60,00 | 45,99 | 14,01 |
| Lebensmittel | 400,00 | 420,50 | -20,50 |
| Freizeit | 160,00 | 0,00 | 160,00 |
| Geschenke | – | 20,00 | – |
| Investitionen | 640,00 | 640,00 | 0,00 |
| **Gesamt Ausgaben** | **2580,00** | **2606,49** | **-26,49** |

- Nicht verplant (R4): 3200,00 − 2580,00 = 620,00
- Überschuss: 593,51 ("Monatlicher Überschuss") = 620,00 + (−26,49) · Sparquote (R8): 593,51 / 3200,00 = 0,1855
- Einnahmenanteile: Gehalt/Lohn (netto) 0,9375 · Nebenjob 0,0625
- Gruppen (Soll / Ist): FIXED 1380,00 / 1525,99 · FLEXIBLE 560,00 / 440,50 · SAVINGS 640,00 / 640,00 · keine Zeile "Ohne Gruppe" (keine Ausgaben ohne Gruppe)
- Lebensmittel: Netto −420,50, die Erstattung zählt nicht als Einnahme (R2)
- Kategorien ohne Soll (z. B. Geschenke, Kredite): Soll und Abweichung sind `null`, Ist ist ihr Ausgaben-Ist (Geschenke 20,00, Kredite 0,00). Deshalb ergibt die Summe der Abweichungen je Kategorie (−6,49) nicht die Abweichung gesamt (−26,49); maßgeblich ist R6.

**Erwartete Ergebnisse Auswertung (R14):**
- Aktive Ausgaben-Serien: Wohnen 900,00 / 1 = 900,00 · Versicherungen 240,00 / 12 = 20,00 · Freizeit 15,00 / 1 = 15,00
- Ø monatliche Kosten: 935,00 · davon Rücklage: 20,00
- Vorschau: 2026-10: 1140,00 (Wohnen 900,00, Versicherungen 240,00; Freizeit ausgesetzt) · 2026-11 bis 2027-09: je 915,00

**Rundung:** Geld mit Skala 2 (HALF_UP), Prozentwerte und Anteile mit Skala 4; nur im Endergebnis runden, nicht in Zwischensummen.

**Sonderfall Einnahmen = 0 (Entscheidung):** Prozent-Soll ergibt 0,00; Euro-Soll bleibt erhalten; Nicht verplant wird negativ (0 minus Summe der Euro-Soll). Überschuss = 0 minus Ausgaben ("Monatlicher Verlust"); Sparquote (R8) ist leer, Einnahmenanteile (R9) sind 0. Keine Division durch null, keine Exception. Das Sheet gibt hier Fehlerwerte aus, die App fängt den Fall sauber ab.

## 13. Ausbau ab 23.10.2026

Umsetzung der Entscheidungen 6 bis 12. Jedes Arbeitspaket ist ein eigener Branch und PR; Berechnungen mit Tests zuerst.

| AP | Inhalt | Ergebnis |
|---|---|---|
| 9 | Spezifikation und `CLAUDE.md` anpassen (dieser Stand) | Spec-PR |
| 10 | Vorzeichen statt Kategorietyp (R2, R4 bis R11, US1 bis US3): Datenmodell, Seed, Validierung inkl. nicht buchbarer Rest-Kategorie, `DashboardCalculator`, Formular-Umschalter; `YearlyTransaction` entfernen (vorgezogen am 25.10.2026, weil ihre Beträge am Kategorietyp hingen) | Fixture F1 ohne wiederkehrende Transaktionen grün |
| 11 | Wiederkehrende Transaktionen (R12, R13, US6, US6a): `RecurrenceSchedule`, Serien-API, Teilen und Ausnahmen, Vorkommen in `/transactions` und `/dashboard`, Seiten *Transaktionen* und *Wiederkehrend* | Fixture F1 Dashboard grün |
| 12 | Auswertung Rücklagen (R14, US13): `ReserveCalculator`, `/reserves`, Seite *Auswertung* | Fixture F1 Auswertung grün |
| 12a | Nicht verplant statt Rest-Kategorie (Entscheidung 12, R4 bis R7, R10): Rest-Kategorie, Gruppe REMAINDER und `system` entfernen, `DashboardCalculator`, Dashboard-Bezeichnungen | Fixture F1 grün |
| 13 | README "Learnings und Entscheidungen" und Interview-Stichpunkte (AP 8) | README |

Getrennt davon und ohne fachliche Änderung: Upgrade auf Spring Boot 4.
