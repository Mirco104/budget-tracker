# Projektstand Budget-Tracker (10.11.2026)

Übergabe für einen neuen Chat ohne Vorwissen. Maßgeblich für das Verhalten der App ist weiterhin `docs/spezifikation.md` (Regeln R1–R31, User Stories US1–US36, Entscheidungen 1–47). Arbeitsregeln stehen in `CLAUDE.md`. Diese Datei fasst nur zusammen, wo wir stehen und was als Nächstes ansteht.

---

## 1. Worum es geht

Web-App für das eigene Budget: Soll je Kategorie planen, Einnahmen und Ausgaben erfassen oder per CSV von der Bank importieren, Monatsübersicht mit Soll gegen Ist, Auswertung mit Rückblick und Rücklagen. Gestartet als 8-Stunden-Übungsprojekt, jetzt im realen Einsatz im Bekanntenkreis (mehrere Nutzer, Einladung durch Admin). Später geplant: Ausgaben teilen (AP 20) und ein Investment-Teil (AP 21–25).

Das Repo ist **öffentlich**: keine echten Finanzdaten, Namen, Kontodaten, `.env`-Dateien oder Dumps committen. Testdaten sind erfunden („Person A", „Person B", IBANs aus der ISO-Doku).

## 2. Technik und Aufbau

- **Backend** (`/backend`): Java 21, Spring Boot 4, Maven Wrapper, Spring Web/Data JPA/Security, Flyway (Migrationen V1–V11 auf main, V12 kommt mit PR #97), PostgreSQL im Betrieb, H2 lokal und in Tests, springdoc.
- **Frontend** (`/frontend`): Vite, React, TypeScript strict, TanStack Query, Recharts, Vitest, Testing Library, ESLint. Keine UI-Bibliothek; Bausteine in `src/components/ui/`, Farben und Abstände als CSS-Variablen (hell und dunkel), Hash-Routing.
- **Betrieb**: `Dockerfile`, `docker-compose.yml` (App, PostgreSQL, optional Caddy für HTTPS), Skripte `scripts/backup.sh`, `scripts/restore.sh`, `scripts/reset-db.sh`. Details in `docs/betrieb.md`.
- **CI**: GitHub Actions (Jobs backend, backend-postgres, frontend, docker, update-test), Dependabot.

Architekturregeln (Kurzform, vollständig in `CLAUDE.md`):
- Controller → Service → Repository; alle Rechnungen in reinen Klassen ohne Spring (`DashboardCalculator`, `RecurrenceSchedule`, `ReserveCalculator`, `IncomeAverage`, `FixedCosts`, `CategorySuggester`, `OccurrenceMatcher`, `SeriesDetector`, Bankprofile).
- Geld immer `BigDecimal` (Skala 2, HALF_UP). Vorzeichen: positiv = Einnahme, negativ = Ausgabe.
- Jede Entität gehört einem Nutzer (`owner`); fremde Ressource in der URL → 404, im Body → 400.
- DTOs als Records, Fehler als RFC-7807-`ProblemDetail`.
- Frontend ohne Geschäftsregeln, nur Anzeige und Formatierung; API-Aufrufe nur in `src/api/`.
- Gemergte Flyway-Migrationen nie ändern.

## 3. Befehle und lokale Umgebung

```bash
cd backend && ./mvnw -B verify                                         # Build und Tests
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=sample # Beispieldaten
cd frontend && npm ci && npm run lint && npm test && npm run build
cd frontend && npm run dev -- --host                                   # auch im Heimnetz erreichbar
scripts/reset-db.sh sample                                             # Beispieldaten zurücksetzen (Backend vorher stoppen)
```

- Profil `dev` (Standard): leere H2-Datei-DB, nur Systemadmin. Profil `sample`: erfundene Daten für Person A bis D, Januar 2025 bis Oktober 2026. Die Logins stehen in `backend/src/main/resources/application-sample.properties` (nur lokal).
- Ports: Backend 8080, Frontend 5173 (Vite leitet `/api` an 8080 weiter). Andere Worktrees können diese Ports belegen; vorher prüfen, fremde Prozesse nicht beenden.
- Handy-Test: Frontend mit `--host` starten, dann `http://<IP des PCs>:5173` im selben WLAN. Testgerät des Nutzers: **iPhone 16**.
- In Claude Code (Desktop) gibt es `.claude/launch.json` mit den Einträgen `backend`, `backend-sample` und `frontend` (nicht eingecheckt).

## 4. Was fertig ist (auf `main`)

- MVP und Ausbau: Kategorien mit Soll (nur Euro, Prozent als Eingabehilfe), Transaktionen, Serien (wiederkehrende Transaktionen, R12/R13), Übersicht mit Soll/Ist, Planung mit „Nicht verplant" und Fixkosten je Kategorie (R25), Auswertung mit Rückblick, Rücklagen-Topf und Vorschau (R14).
- Benutzersystem: Anmeldung, Einladungslinks, Konto, Nutzerübersicht mit Rollen USER/ADMIN/SYSTEM_ADMIN (US14–US16, US25).
- Betrieb mit Docker, Backup, Health-Check (AP 19).
- Überarbeitung von Bedienung und Design (Abschnitt 17, AP 19a–19k).
- Bank-Import (Abschnitt 18, AP 26a–26h, AP 29): Bankkonten, Umbuchungen zählen nicht (R27), Kategorie-Vorschlag (R29), CSV-Import für C24, Trade Republic, N26 und Sparkasse mit Vorschau, Duplikaterkennung, Rückgängig (R30), Serienvorschlag im Import (R31), „Ist ersetzt Plan" (R28), Filter in der Import-Vorschau (#84), VR-Bank als fünftes Profil (AP 29).
- Zuletzt gemergt:
  - **#92** (Ticket #85): keine Zoom- und Höhenprobleme am iPhone. Eingabefelder mindestens 16 px, `maximum-scale=1` nur auf iOS, Dialog mit `dvh`-Höhe und angehefteter Kopf- und Knopfleiste, Datumsfelder auf iOS ohne eigene Mindestbreite.
  - **#95** (Ticket #80): „Alle Einträge" beim Bearbeiten und Löschen einer Serie. Betrag, Beschreibung, Kategorie und Konto gelten für alle Teile der Serie; Datum, Rhythmus und einzeln geänderte Einträge bleiben.

## 5. Offene Pull Requests

| PR | Inhalt | Stand / Empfehlung |
|---|---|---|
| #93 | Ladeanzeige: Kreisel mit Text und Balken oben (#78) | CI grün, bereit zum Testen und Mergen. Kann mit `App.tsx`/`index.css` aus anderen PRs kollidieren. |
| #94 | Lokalen Modus anzeigen: Abzeichen „Beispieldaten (sample)" bzw. „Entwicklung (dev)" (#82) | CI grün, bereit. `GET /api/health` liefert dafür `mode`. |
| #96 | Buchung zur Serie machen (#76) und Buchung nachträglich einer Serie zuordnen (#91) | **Nicht mergen, bis das Serien-Konzept entschieden ist** (siehe Abschnitt 7). Die Auswahl „Gehört zu einer Serie" ist verwirrend und verschiebt Geld in andere Monate. |
| #97 | Passwort vergessen: Admin erzeugt einen Einmal-Link, 24 h gültig, nur Hash gespeichert, Migration V12 (US36, #81) | CI grün. Offen: andere Sitzungen bleiben angemeldet, für den Systemadmin gibt es keinen Weg zurück. |
| #98 | Nur Doku: Vorschläge zu #79 (wöchentlich), #86 (Fixkosten), #89 (Verschlüsselung) in `docs/vorschlaege-tickets-79-86-89.md` | Diskussionsgrundlage. #79 und #86 hängen am Serien-Konzept. |
| #99–#101 | Dependabot (Caddy, npm-Pakete, jsdom 31) | Prüfen und mergen, wenn die CI grün ist. |

Arbeitsweise bei mehreren PRs: der Reihe nach mergen, nach jedem Merge `main` in die übrigen Branches mergen (Merge, kein Rebase).

## 6. Offene Tickets (GitHub Issues)

| Ticket | Thema | Einschätzung |
|---|---|---|
| #83 | Bug: „Als Serie anlegen" im Import setzt die Kategorie zurück | **Nicht nachstellbar** auf main (Chip, Auswahlliste, neue Kategorie, „Fertige übernehmen" geprüft). Genaue Schritte vom Nutzer nötig. |
| #102 | Bug: Zeitraum 01.01.2000–30.11.2026 lädt keine Transaktionen | Neu, noch nicht untersucht. Vermutlich die Grenze „höchstens 10 Jahre" (`/transactions?from&to` gibt dann 400, Entscheidung zu US26 in Abschnitt 17/19h). Die Oberfläche zeigt den Fehler offenbar nicht. |
| #103 | Filteroption „Gesamter Zeitraum" | Neu. Passt zu #102: Schnellwahl ohne Zeitraumgrenze, dafür muss die 10-Jahres-Grenze überdacht werden (Paging oder Grenze aufheben). |
| #104 | Wiederkehrend: Zyklus anzeigen und ändern | Neu. Hängt am Serien-Konzept (Abschnitt 7). |
| #76, #91 | Buchung ↔ Serie | In PR #96 umgesetzt, aber wegen des Konzepts zurückgestellt. |
| #78, #82, #81 | Ladeanzeige, Modus, Passwort vergessen | In PR #93, #94, #97. |
| #79, #86, #89 | Wöchentlich, Fixkosten/variabel, Verschlüsselung | Vorschläge in PR #98; Entscheidung offen. |

## 7. Wichtigste offene Entscheidung: Plan und Ist trennen

**Problem.** Heute zählt ein geplanter Eintrag einer Serie im Ist mit, auch wenn kein Geld geflossen ist (Entscheidung 6, R1, R12). Das stammt aus der Zeit vor dem Bank-Import. Seit dem Import kommen die echten Buchungen ohnehin, und Plan und Wirklichkeit müssen mit immer mehr Regeln zusammengeführt werden: „Ist ersetzt Plan" (R28), Treffer-Erkennung, „ersetzt"-Häkchen im Import, #91, #76. Beim Test von #96 hat eine Januar-Buchung den Dezember-Eintrag der Gehaltsserie ersetzt, der Januar zeigte dadurch 4.200 € statt 2.200 € Einnahmen.

**Idee des Nutzers.** Für die Ist-Analyse sollen nur echte Transaktionen zählen; Muster wie Fixkosten und Regelmäßigkeiten werden daraus erkannt. Für die Soll-Planung ergibt ein durchschnittlicher Monat Sinn, eventuell mit konkreten geplanten Posten.

**Einschätzung (Claude, 09.11.2026), vom Nutzer noch nicht entschieden:**
- **Ist** sind nur echte Buchungen, ob von Hand oder aus dem Import.
- **Plan:** Eine Serie wird zu einem **erwarteten Posten**. Sie dient für die Soll-Planung (Fixkosten R25, Rücklagen R14), für die Vorschau im laufenden Monat („noch erwartet") und zur Einordnung: Eine echte Buchung, die zu einem erwarteten Posten passt, ist Fixkosten (löst #86).
- Muster in den Buchungen schlagen erwartete Posten vor (`SeriesDetector` gibt es schon); bestätigen muss der Nutzer.
- Ein ausgebliebener erwarteter Posten wird sichtbar („Gehalt nicht eingegangen").
- Für Handeingabe ohne Import steht ein erwarteter Posten als „offen" da; ein Tipp bucht ihn.
- Die Übersicht braucht „Ist + noch erwartet = Prognose", damit „Noch frei" am Monatsanfang stimmt.
- Daten übernehmen: vergangene geplante Einträge ohne echte Buchung einmalig in echte Buchungen umwandeln, dann bleiben alle bisherigen Monatszahlen gleich.
- Der Umbau ist groß. Betroffen sind R1, R10, R12–R14, R25, R28, R31, Übersicht, Transaktionsliste und Import. #79, #86, #96 und #104 sollten danach neu bewertet werden.

**Nächster Schritt:** das Konzept ausarbeiten. Der Nutzer möchte vorher offene Fragen geklärt haben und arbeitet gern mit einem klickbaren Mockup. Danach die Spezifikation anpassen (neue Entscheidung in Abschnitt 11, betroffene Regeln), dann Arbeitspakete planen.

## 8. Weitere offene Punkte aus der Spezifikation (Abschnitt 16)

- HTTPS mit Caddy auf dem echten Server prüfen. Laut Nutzer läuft der Server schon; der Punkt steht aber noch offen in der Spec.
- Plattform für den Betrieb (Entscheidung 21).
- Bank-Import mit echten Dateien zu prüfen: Enthält `amount` bei Trade Republic schon Gebühren und Steuern? Wie exportieren C24 und Sparkasse vorgemerkte Umsätze?
- Später: Serien im ganzen Verlauf erkennen, Umbuchungen nach geänderter Kontozuordnung neu berechnen, Kategorie-Vorschlag über Wortgewichtung verbessern, CAMT.053 bzw. Bank-API als weitere Quellen.
- AP 13 (README „Learnings und Entscheidungen") ist seit Langem offen. Danach folgen AP 20 (Teilen) und AP 21–25 (Investment).

## 9. Zusammenarbeit mit dem Nutzer

- Der Nutzer schreibt Deutsch und erwartet Antworten auf Deutsch. Code, Kommentare, Tests, Commits und PR-Titel sind Englisch (`CLAUDE.md`).
- Bei Oberflächenthemen zuerst ein Mockup anbieten. Unklare Punkte vor dem Planen klären, dann einen Plan vorlegen und auf Freigabe warten.
- Ein Branch je Feature (`feat/<name>`), nie direkt auf `main`, kleine Commits im Stil der Conventional Commits. Ein PR enthält Ziel, Bezug zur Spec, was generiert und was von Hand geändert wurde. Der Nutzer merged selbst.
- Entscheidungen, die ohne Rückfrage getroffen werden, stehen in der Spec (Abschnitt „Entscheidungen beim Umsetzen") und im PR-Text.
- Der Nutzer testet am iPhone 16. iOS-Safari verhält sich bei Zoom, Höhe (`vh` vs. `dvh`) und Datumsfeldern anders als Chrome; die Handyansicht im Browser reicht als Nachweis nicht.

## 10. Stolpersteine aus den letzten Sitzungen

- Windows: Git meldet bei neuen Dateien „LF will be replaced by CRLF". Das ist harmlos, die Diffs bleiben klein.
- Lange Heredocs mit Python-Code in der Bash des Desktop-Tools sind teils fehlgeschlagen. Skripte besser als Datei schreiben und dann ausführen.
- JPA: Über eine träge geladene Verknüpfung (`reset.getUser()`) geänderte Felder wurden im Test nicht gespeichert. Den Nutzer über das Repository laden (siehe `PasswordResetService`).
- `git fetch` meldete einmal „cannot lock ref", weil gleichzeitig ein anderer Worktree abgeholt hat; einfach wiederholen.
- Ein Vitest-Test in `App.test.tsx` („loads the analysis page …") schwankt unter Last; einzeln läuft er grün.
