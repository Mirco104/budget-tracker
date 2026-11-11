# Konzept: Plan und Ist trennen, Konten, „Verfügbar" und Prognose

Stand 11.11.2026 · Entwurf zur Freigabe · danach wandert das Beschlossene in `docs/spezifikation.md` (neue Entscheidungen in Abschnitt 11, geänderte Regeln, neuer Abschnitt 19 mit Arbeitspaketen).

Punkte, die ich ohne Rückfrage festgelegt habe, sind mit **[prüfen]** markiert.

---

## 1. Ziele

Die App soll:
1. **Kostenpunkte erkennen** und Hebel zum Kostensenken zeigen.
2. **Überblick über alle Finanzen** geben, über mehrere Konten hinweg.
3. **Vorausschauen:** was bis Monatsende und in den nächsten Monaten passiert.
4. Die Leistbarkeitsfrage mit **einer Zahl „Verfügbar"** beantworten.
5. **Wenig Pflege** brauchen.
6. **Zahlen liefern, denen man vertraut.**
7. **Abweichungen früh** melden.
8. **Sparziele und Puffer** unterstützen.

## 2. Grundprinzipien

1. **Ist ist nur, was wirklich passiert ist.** Ist sind echte Buchungen, von Hand erfasst oder importiert. Ein geplanter Betrag zählt nie als Ist.
2. **Plan ist, was erwartet oder gewollt ist:** erwartete Posten (heute „Serien"), Budgets, Rücklagentöpfe und Sparziele.
3. **Prognose = Ist bis heute + Plan ab heute.** Sie ist immer als Prognose gekennzeichnet, nie mit dem Ist vermischt.
4. **Jede Kennzahl lässt sich aufklappen** und zeigt, woraus sie besteht.
5. **Vorschlagen statt erfinden.** Die App erkennt Muster und schlägt sie vor, der Nutzer bestätigt. Nichts entsteht still im Hintergrund.
6. **Ohne Buchungen nutzbar:** Wer nur planen will, kann ein Budget mit erwartetem Einkommen aufstellen, ohne je zu buchen. Der Ist-Vergleich erscheint, sobald Buchungen da sind.

## 3. Begriffe

### 3.1 Konto
Ein Bankkonto, ein Tagesgeld, ein Pocket oder ein Depot. Neu: jedes Konto hat eine **Rolle**:

| Rolle | Beispiele | Zählt zu „Verfügbar" | Bedeutung |
|---|---|---|---|
| **Alltag** | Girokonto, Gemeinschaftskonto | ja | Geld für laufende Ausgaben |
| **Sparen** | Tagesgeld, Rücklagen-Pocket, Notgroschen | nein | Geld, das zurückgelegt ist |
| **Depot** | Trade Republic, Broker | nein | angelegtes Geld |

Die Rolle wählt der Nutzer. Ein neues Konto ist zunächst „Alltag". Das Standardkonto ist immer ein Alltagskonto.

### 3.2 Kontostand
Ein Betrag mit Stichtag, z. B. „Girokonto: 2.431,17 € am 10.11.2026". Vorerst trägt der Nutzer ihn von Hand ein; später kommt er aus CAMT.053. Der **App-Saldo** ist der letzte eingetragene Stand plus alle Buchungen danach.

### 3.3 Buchung (Ist)
Eine echte Bewegung auf einem Konto. Neu: jede Buchung hat eine **Art**, die sich aus Vorzeichen und Konten ergibt:

| Art | Wann | Zählt in |
|---|---|---|
| **Einnahme** | positiver Betrag, keine Umbuchung | Einnahmen |
| **Ausgabe** | negativer Betrag, keine Umbuchung | Ausgaben (Konsum) |
| **Sparen** | Umbuchung von einem Alltagskonto auf ein Spar- oder Depotkonto, oder direkt als „Sparen" gebucht, wenn das Zielkonto nicht in der App ist (z. B. Bausparvertrag) | Sparen |
| **Entsparen** | Umbuchung von einem Spar- oder Depotkonto zurück auf ein Alltagskonto | Sparen (negativ) |
| **Umbuchung** | zwischen zwei Alltagskonten oder zwischen zwei Spar- bzw. Depotkonten | nirgends |

Bewegungen innerhalb eines Depots, etwa ein ETF-Kauf vom Verrechnungskonto, sind ein Tausch von Geld in Wertpapiere und zählen nicht. Das ersetzt Entscheidung 44 („Wertpapiere als Ausgabe").

**Kategorie:** Nur Einnahmen und Ausgaben haben eine Kategorie. Sparen hat optional ein Sparziel oder einen Topf.

### 3.4 Erwarteter Posten (bisher „Serie")
Eine regelmäßig erwartete Einnahme, Ausgabe oder Sparrate: Miete, Gehalt, Versicherung, Abo, Sparplan.
- Er hat Betrag, Beschreibung, Kategorie (bzw. bei Sparen ein Ziel), Konto, **Rhythmus** (wöchentlich, alle 2 Wochen, monatlich, vierteljährlich, halbjährlich, jährlich), erstes Datum und optional ein Enddatum.
- **Optional mit Vertragsdaten:** Anbieter, Vertragsende, Kündigungsfrist. Daraus berechnet die App „kündbar bis" und erinnert 30 Tage vorher **[prüfen]**.
- Jeder **Termin** eines Postens hat einen Status:

| Status | Bedeutung |
|---|---|
| **erwartet** | liegt in der Zukunft oder innerhalb der Toleranz |
| **erfüllt** | eine echte Buchung passt dazu (Abschnitt 4.2) |
| **ausgeblieben** | 5 Tage nach dem Termin ohne passende Buchung **[prüfen]** |
| **übersprungen** | vom Nutzer für diesen Termin ausgesetzt |

- Ein erwarteter Posten **zählt nie im Ist**. Er zählt in Planung, Prognose und „Verfügbar".
- **Bestätigen:** Ohne Import (Barzahlung, Konto ohne CSV) bucht ein Tipp auf „Gebucht" den Termin als echte Buchung mit dem erwarteten Betrag. Betrag und Datum lassen sich davor ändern.

### 3.5 Budget
Ein Soll je Kategorie und Monat, gültig ab einem Monat wie heute (R3). Es gilt **nur für den variablen Teil** einer Kategorie; erwartete Posten haben ihren eigenen Betrag. Reste **verfallen am Monatsende**, damit sich Fehler nicht durchziehen.

### 3.6 Rücklagentopf
Geld, das für eine seltene oder unregelmäßige Ausgabe zurückgelegt wird.
- **Automatisch** je erwartetem Posten mit Rhythmus über einem Monat (Kfz 1.200 € jährlich → 100 € im Monat), **von Hand** für Unregelmäßiges (z. B. „Reparaturen 50 €/Monat, Ziel 600 €").
- **Ohne verknüpftes Konto:** rein rechnerisch. Sein Soll-Stand wird von „Verfügbar" abgezogen (das Geld liegt auf dem Alltagskonto, ist aber verplant).
- **Mit verknüpftem Konto** (Rolle Sparen, z. B. ein Rücklagen-Pocket): Der Topf zeigt den echten Stand des Kontos gegen sein Soll. Weil das Konto nicht zu „Verfügbar" gehört, wird nichts abgezogen; nichts zählt doppelt. Mehrere Töpfe können sich ein Konto teilen; ihr gemeinsames Soll wird mit dem Kontostand verglichen.
- Ein Alltagskonto lässt sich nicht verknüpfen.

### 3.7 Sparziel
Betrag, Zieldatum, optional ein Konto (Rolle Sparen oder Depot).
- **Monatsrate** = (Zielbetrag − aktueller Stand) ÷ verbleibende Monate, aufgerundet auf volle Euro **[prüfen]**.
- **Notgroschen** ist ein Sparziel mit dem Ziel „N Monatsausgaben", Vorgabe 3 **[prüfen]**. Gerechnet wird mit den Ø Ausgaben der letzten 12 Monate.
- Die Rate steht in der Planung im Block „Sparen" und wird als ausstehend von „Verfügbar" abgezogen, bis sie im Monat gespart ist.

### 3.8 Regel
„Wenn die Gegenseite oder Beschreibung *enthält* X (und optional der Betrag zwischen A und B liegt), dann Kategorie K." Regeln greifen beim Import und beim Erfassen vor dem bisherigen Vorschlag aus dem Verlauf (R29). Eine Regel entsteht mit einem Klick aus einer Buchung („Immer so einordnen").

### 3.9 Hinweis
Eine kurze Meldung in der Übersicht mit Sprung zur Ursache:
- Budget wird überschritten (Hochrechnung)
- erwarteter Posten ausgeblieben
- Preis geändert
- Konto rutscht ins Minus
- Saldo weicht vom echten Kontostand ab
- Kündigungsfrist naht

Hinweise lassen sich ausblenden.

## 4. Rechenregeln (neu bzw. geändert)

Die Nummern K1–K14 sind vorläufig, in der Spezifikation werden sie Teil der R-Regeln.

**K1 Ist eines Monats.** Einnahmen, Ausgaben und Sparen eines Monats sind die Summen der echten Buchungen dieser Art im Monat. Erwartete Posten zählen nicht. *(Ersetzt R1 und R12 in Bezug auf das Ist.)*

**K2 Erfüllung.** Eine echte Buchung erfüllt einen Termin eines erwarteten Postens, wenn sie auf ihn passt. Das ist die heutige Treffer-Regel aus R28: gleiches Vorzeichen, Datum höchstens ±5 Tage vom Termin (bei wöchentlichem Rhythmus ±3 Tage), Betrag höchstens ±20 % und gemeinsames Wort bzw. gleiche Kategorie. Import und Erfassen schlagen die Erfüllung vor; ist sie eindeutig, wird sie gesetzt, sonst wählt der Nutzer **[prüfen]**. Eine Buchung erfüllt höchstens einen Termin. Liegt der Betrag außerhalb von ±20 %, aber Text, Datum und Konto passen, entsteht der Hinweis „Preis geändert?" mit „Erwarteten Betrag anpassen".

**K3 Fix und variabel.** Eine Ausgabe, die einen Termin erfüllt, ist **fix**, alle anderen sind **variabel**. Das ersetzt R10, bei dem „wiederkehrend" aus den Serien kam.

**K4 Ausgeblieben.** Ein Termin ohne Erfüllung gilt 5 Tage nach seinem Datum als ausgeblieben. Bei einer Einnahme erscheint immer ein Hinweis, bei einer Ausgabe nur in der Liste der Termine.

**K5 App-Saldo.** Saldo(Konto, Tag) = letzter Kontostand mit Stichtag ≤ Tag + Summe aller Buchungen des Kontos nach dem Stichtag bis einschließlich Tag. Ohne eingetragenen Kontostand zeigt die App keinen Saldo, nur Bewegungen.

**K6 Abgleich.** Trägt der Nutzer einen neuen Kontostand ein, zeigt die App die Differenz zum App-Saldo an diesem Tag („App: 2.418,77 €, Bank: 2.431,17 €, Differenz +12,40 €: fehlt eine Buchung?"). Die Differenz wird nicht gebucht; der neue Stand gilt ab seinem Stichtag.

**K7 Prognose je Konto.** Für jeden Tag ab heute bis zum Ende des Horizonts (Vorgabe: Ende des übernächsten Monats **[prüfen]**) gilt: Saldo heute + alle noch erwarteten Termine des Kontos bis zu diesem Tag (Einnahmen, Ausgaben, Sparraten) + der noch offene Budgetrest des laufenden Monats, gleichmäßig auf die restlichen Tage verteilt, und für jeden folgenden Monat dessen volles Budget, gleichmäßig auf seine Tage verteilt **[prüfen]**. Budgets und Sparziel-Raten ohne eigenes Konto fallen auf das Standardkonto.

**K8 Unterdeckung.** Fällt die Prognose eines Alltagskontos unter 0 € oder unter einen Mindestbetrag je Konto (Vorgabe 0), entsteht ein Hinweis: „Girokonto: am 28.11. voraussichtlich −140 €, weil Kfz-Versicherung 1.200 € fällig".

**K9 Verfügbar.** Für den laufenden Monat, über alle Alltagskonten:

```
Verfügbar = Σ App-Saldo der Alltagskonten heute
          − noch erwartete Ausgaben und Sparraten bis Monatsende (Termine im Status „erwartet")
          − Soll-Stand der Rücklagentöpfe ohne verknüpftes Konto
          − offene Budgetreste: Σ über Kategorien max(0, Budget − variables Ist im Monat)
          − offene Sparziel-Raten des Monats (noch nicht gespart)
```

- **Erwartete Einnahmen bis Monatsende zählen nicht** (vorsichtig). Die Herleitung zeigt sie getrennt als „+ noch erwartet: Gehalt 2.200 € am 30.11." **[prüfen]**.
- Ist die Zahl negativ, steht „Fehlbetrag" in Orange.
- Ohne Kontostand eines Alltagskontos zeigt die App statt „Verfügbar" den Hinweis „Kontostand eintragen".

**K10 Hochrechnung variabler Ausgaben.** Ab dem 5. des Monats **[prüfen]**: Hochrechnung(Kategorie) = variables Ist bis heute ÷ vergangene Tage × Tage im Monat. Fixe Ausgaben werden nicht hochgerechnet, sie zählen mit dem erwarteten Betrag. Liegt die Hochrechnung über dem Budget: „Bei dem Tempo landest du bei 480 € statt 400 €."

**K11 Vergleich mit dem Durchschnitt.** Je Kategorie der Ø der variablen Ausgaben über die letzten 12 abgeschlossenen Monate ab dem ersten Eintrag (wie R23), angezeigt neben Ist und Budget.

**K12 Sparquote.** Sparquote(Monat) = Sparen ÷ Einnahmen, in Prozent; ohne Einnahmen „–". In der Auswertung zusätzlich als Ø über den Zeitraum.

**K13 Rücklagen-Soll-Stand.** Für einen automatischen Topf: Monatsrate × Monate seit der letzten Fälligkeit (heutige Logik aus R14). Für einen Topf von Hand: Rate × Monate seit Anlage, höchstens das Ziel. Mit verknüpftem Konto steht daneben der echte Stand.

**K14 Nicht verplant (Planung).** Basis − Σ erwartete Ausgaben (Monatsanteil) − Σ Budgets − Σ Sparraten (Ziele und Töpfe von Hand). Automatische Töpfe zählen hier nicht noch einmal, ihr Monatsanteil steckt schon in den erwarteten Ausgaben (die Kfz-Versicherung mit 1.200 € im Jahr zählt als 100 € Fixkosten). Basis ist wählbar: **geplantes Einkommen** (Summe der erwarteten Einnahmen, neu, macht die App ohne Buchungen nutzbar), Ø Einnahmen oder Mindesteinnahmen (wie heute, R23).

## 5. Seiten

Die Navigation bekommt einen fünften Bereich **Konten**. Am Handy hat die untere Leiste fünf Symbole.

### 5.1 Übersicht
1. **Verfügbar** als große Zahl mit „bis 30.11." und aufklappbarer Herleitung (K9).
2. **Hinweise** (Abschnitt 3.9), höchstens drei, dazu „alle anzeigen".
3. **Monat in Zahlen:** Einnahmen, Ausgaben (fix und variabel) und Sparen, je mit Ø.
4. **Budgets:** je Kategorie variables Ist von Budget, Hochrechnung als Markierung auf dem Balken, Ø daneben.
5. **Noch erwartet diesen Monat:** offene Termine mit Datum und Betrag, jeder mit „Gebucht".

### 5.2 Transaktionen
- Nur echte Buchungen, wie heute mit Zeitraum und Filtern. Neue Filter: Art (Einnahme, Ausgabe, Sparen, Umbuchung) und fix/variabel.
- Eine Buchung, die einen Termin erfüllt, zeigt „erfüllt: Miete".
- Erwartete Termine erscheinen **nicht** in dieser Liste. Bei einem Zeitraum, der in die Zukunft reicht, stehen sie in einem eigenen, abgesetzten Abschnitt „Erwartet" **[prüfen]**.
- Im Buchungsdialog: Art (Ausgabe, Einnahme, Sparen, Umbuchung), Vorschlag der Erfüllung („erfüllt Miete vom 01.11.?") und „Immer so einordnen" (Regel).

### 5.3 Planung
Ein durchschnittlicher Monat in vier Blöcken, oben die Leiste „Nicht verplant" (K14):
1. **Einnahmen:** erwartete Einnahmen (Gehalt …), dazu die Wahl der Basis.
2. **Fixkosten:** erwartete Ausgaben, gruppiert nach Kategorie, mit Monatsanteil. Ein Posten öffnet sich mit Rhythmus, Vertragsdaten und den Terminen samt Status.
3. **Budgets:** Soll je Kategorie für den variablen Teil, mit Ø und Vormonat.
4. **Sparen:** Sparziele (mit Rate und Fortschritt), Rücklagentöpfe (mit Rate und Soll-Stand) und Notgroschen.

„Gilt ab" bleibt wie heute.

### 5.4 Konten (neu)
- Liste der Konten mit Rolle, App-Saldo, Datum des letzten Kontostands und Abgleich-Status (✓ oder Differenz).
- Je Alltagskonto die **Prognose-Kurve** bis zum Horizont mit Markierung der Unterdeckung (K7, K8).
- „Kontostand eintragen" je Konto.
- Summe nach Rolle: Alltag, Sparen, Depot.
- Die Verwaltung (anlegen, Rolle, IBAN, archivieren) zieht von *Einstellungen › Bankkonten* hierher.

### 5.5 Auswertung
- **Rückblick** wie heute, dazu die Sparquote, und die Ausgaben getrennt nach fix und variabel.
- **Kostenhebel (neu):**
  - größte Kategorien,
  - Kategorien mit steigendem Trend,
  - alle Fixkosten und Abos mit **Jahressumme**,
  - Verträge mit „kündbar bis",
  - Preisänderungen der letzten 12 Monate.
- Die **Vorausschau** wird zur Prognose: Ausgaben der nächsten 12 Monate aus den erwarteten Posten, die Rücklagentöpfe und die Salden je Konto (K7).

## 6. Erkennen statt pflegen
- **Import:** Regeln vor Vorschlag; Erfüllung erwarteter Termine (K2); erkennt eine Gruppe von Buchungen als regelmäßig, schlägt die App einen erwarteten Posten vor (heute R31, künftig für alle Rhythmen).
- **Im Verlauf:** Eine Karte in der Planung schlägt vor „Diese 6 Buchungen sehen nach einem monatlichen Abo aus: Spotify 10,99 €" mit „Als erwarteten Posten anlegen".
- **Preisänderung:** Weicht eine passende Buchung über 20 % ab, gibt es einen Hinweis mit „Betrag anpassen ab …".

## 7. Übernahme der bestehenden Daten
1. **Serien → erwartete Posten.** Rhythmus, Datum und Konto bleiben; Ausnahmen werden zu „übersprungen".
2. **Ersetzte Vorkommen (R28) → erfüllte Termine.** Die Verknüpfung bleibt erhalten.
3. **Vergangene Vorkommen ohne Buchung → echte Buchungen**, einmalig beim Umstieg. So bleiben alle bisherigen Monatszahlen gleich. Die App zeigt vorher an, wie viele es sind, und der Nutzer bestätigt. Alternativ verwirft er sie; dann fehlen in alten Monaten Beträge, die nie gebucht wurden **[prüfen]**.
4. **Konten:** Alle bestehenden Konten werden „Alltag". Der Nutzer stellt Tagesgeld und Depot selbst um; dabei werden frühere Umbuchungen zwischen diesen Konten nachträglich zu Sparen bzw. Entsparen.
5. **Kategorie „Investitionen":** Ausgaben dieser Kategorie bleiben Ausgaben, bis der Nutzer das Depot anlegt und zuordnet. Dann schlägt die App vor, sie in „Sparen" umzuwandeln **[prüfen]**.
6. **Kontostände** gibt es noch nicht. Die Übersicht bittet beim ersten Öffnen darum („Kontostand eintragen, damit „Verfügbar" rechnen kann").

## 8. Was sich an der Spezifikation ändert

| Bestehend | Änderung |
|---|---|
| R1, R12 (Vorkommen zählen im Ist) | Vorkommen zählen nie im Ist (K1); R12 beschreibt nur noch die Termine |
| R7 „Noch frei" | ersetzt durch „Verfügbar" (K9); im Budget bleibt „noch X" je Kategorie |
| R10 wiederkehrend/einmalig | fix/variabel über Erfüllung (K3) |
| R13 Bearbeiten von Vorkommen | gilt für Termine erwarteter Posten (überspringen, Betrag ändern ab …); „Nur dieser Eintrag" erzeugt keine Ersatzbuchung mehr |
| R14 Rücklagen | Töpfe mit optionalem Konto (Abschnitt 3.6, K13) |
| R25 Fixkosten | bleibt, Basis sind die erwarteten Posten |
| R26 Bankkonten | dazu Rolle und Kontostand |
| R27 Umbuchung | zählt nur zwischen Konten gleicher Gruppe nicht; Alltag ↔ Sparen/Depot ist Sparen |
| R28 Ist ersetzt Plan | wird zu „Buchung erfüllt Termin" (K2) |
| R31 Serien im Import | schlägt erwartete Posten vor, alle Rhythmen |
| Entscheidung 6 | wird abgelöst (Ist = echter Geldfluss bleibt, aber ohne geplante Einträge) |
| Entscheidung 41, 44 | durch Arten und Kontorollen ersetzt |
| Tickets #76, #91 (PR #96) | entfallen; „Buchung erfüllt Termin" deckt #91 ab, „Als erwarteten Posten anlegen" aus einer Buchung deckt #76 ab |
| Ticket #79 | wöchentlich und alle 2 Wochen als Rhythmus |
| Ticket #86 | fix/variabel (K3) |
| Ticket #104 | Rhythmus anzeigen und ändern in der Planung |

## 9. Arbeitspakete (Reihenfolge wie besprochen)

| AP | Inhalt | Umfang |
|---|---|---|
| 27a | Spezifikation, Entscheidungen, `CLAUDE.md` | S |
| 27b | **Plan und Ist trennen:** erwartete Posten mit Terminen und Status, Erfüllung (K1–K4), Übernahme der Daten (Abschnitt 7), Transaktionsliste nur Ist, „Gebucht" | L |
| 27c | **Kontorollen und Arten:** Sparen und Entsparen, Umbuchung neu (Abschnitt 3.1, 3.3), Sparquote (K12) | M |
| 27d | **Kontostände und Abgleich** (K5, K6), Seite *Konten* | M |
| 27e | **„Verfügbar" und Prognose** je Konto mit Unterdeckung (K7–K9), Übersicht neu | L |
| 27f | **Hochrechnung und Ø je Kategorie** (K10, K11), Hinweise (Abschnitt 3.9) | M |
| 27g | **Sparziele und Notgroschen**, Rücklagentöpfe mit Konto (Abschnitt 3.6, 3.7, K13) | M |
| 27h | **Regeln** zur Auto-Kategorisierung (Abschnitt 3.8) | S–M |
| 27i | **CAMT.053** mit Kontostand | M |
| 27j | **Verträge, Abo-Erkennung im Verlauf, Preisänderungen**, Kostenhebel in der Auswertung | M |

Rechenklassen ohne Spring: `Fulfillment` (K2–K4), `AccountBalance` (K5, K6), `CashflowForecast` (K7, K8), `Available` (K9), `RunRate` (K10), `SavingsGoal` (K12, K13, Sparziel-Rate). Wie bisher gilt: Tests zuerst.

## 10. Offen für später
- Bank-API (PSD2) statt Datei-Import.
- „Verfügbar" mit eigenem Horizont (z. B. bis zum nächsten Gehalt statt bis Monatsende).
- Teilen (AP 20) und Investment (AP 21–25) bauen auf Kontorollen und Sparzielen auf; ihre Spec-Runden folgen danach.
