# Spezifikation: Budget-Tracker (Web-App)

Stand: 27.10.2026 · Basis: Google Sheet "Budget + Investment Planer V1.1.0" · Stack: Java + Spring Boot (REST) und TypeScript + React · MVP: ca. 8 h Arbeitszeit, fertig bis Di 20.10.2026 · Ausbau ab 23.10.2026 siehe Abschnitt 13 · Reale Nutzung mit mehreren Nutzern ab 27.10.2026 siehe Abschnitte 14 bis 16

## 1. Ziel und Scope

**Ziel:** Eine schlanke Web-App, die das Budget-Blatt des Sheets abbildet: Soll-Verteilung festlegen, Ausgaben und Einnahmen erfassen, Dashboard mit Soll/Ist/Abweichung. Das Projekt zeigt vor allem den Prozess: Spezifikation → Plan → Umsetzung mit Claude Code → Review → Tests → CI.

**Ziel ab 27.10.2026 (Entscheidung 18):** Die App wird real genutzt, zuerst privat und im Bekanntenkreis, später eventuell öffentlich. Dafür kommen ein Benutzersystem, der Betrieb (Abschnitt 15), die Verwaltung von Kategorien, das Teilen von Ausgaben und der Investment-Teil dazu (Abschnitt 14).

**Im MVP (drin):**
- Kategorien mit Soll-Werten (Euro oder Prozent der Einnahmen)
- Transaktionen erfassen (Einnahmen positiv, Ausgaben negativ, mit Datum)
- Wiederkehrende Transaktionen (monatlich, vierteljährlich, halbjährlich, jährlich), die im Monat ihrer Abbuchung mit vollem Betrag zählen
- Dashboard pro Monat: Einnahmen, Ausgaben (davon wiederkehrend und einmalig), Überschuss; Budget-Tabelle mit Soll, Ist und Abweichung zum Soll je Kategorie; Umschalter € / % (Anteil an den Einnahmen)
- Die Seite *Planung* zeigt, welcher Teil der Einnahmen nicht verplant ist
- Bedienbar auch auf schmalen Bildschirmen (ab ca. 360 px Breite)
- Auswertung: durchschnittliche Ausgaben der letzten 12 Monate, Rücklagen-Topf für seltene wiederkehrende Ausgaben und Vorschau der Ausgaben der nächsten 12 Monate

**Ausbau (Abschnitt 14):**
- Kategorien anlegen, umbenennen, sortieren, archivieren und löschen (US10)
- Auswertung über einen wählbaren Zeitraum mit Diagramm (R14, US13)
- Benutzersystem mit Einladung und getrennten Daten je Nutzer (R15, US14 bis US16)
- Ausgaben teilen wie in Splitwise, zuerst ohne, später mit Verknüpfung zu anderen Nutzern (R16 bis R18, US17 bis US19)
- Investment: Sparplan und Prognose, Einmalzahlungen, Freistellungsauftrag, Depot mit Positionen (R19 bis R22, US20 bis US23)

**Bewusst vorerst draußen:**
- Mehrere Währungen, Anbindung an Banken und Import von Kontoauszügen, eigene Mobile-App (langfristig geplant; das Web-Layout ist schon jetzt für schmale Bildschirme ausgelegt)

## 2. Fachliche Regeln (aus den Formeln des Sheets abgeleitet)

Sei `M` der gewählte Monat und `N(k)` das Netto der Kategorie `k` in M (R1).

| # | Regel | Quelle im Sheet |
|---|---|---|
| R1 | **Netto je Kategorie** `N(k)` = Summe der Beträge (mit Vorzeichen, R11) aller Transaktionen der Kategorie in M + Summe der Beträge aller Vorkommen wiederkehrender Transaktionen der Kategorie, die in M liegen (R12). Ein Vorkommen zählt mit seinem vollen Betrag; das Ist bildet den echten Geldfluss ab. | `Dashboard_Budget!E39:E59`, `K39:K46`; bewusste Abweichung: keine Verteilung von Jahresbeträgen auf 12 Monate |
| R2 | **Einnahmen und Ausgaben:** Eine Kategorie mit `N(k) > 0` zählt in M als Einnahme, eine mit `N(k) < 0` als Ausgabe. **Einnahmen gesamt (Ist)** `E` = Summe aller positiven `N(k)`. **Ausgaben-Ist je Kategorie** `I(k)` = `−N(k)`, wenn `N(k) < 0`, sonst 0. **Ausgaben gesamt (Ist)** `A` = Summe aller `I(k)`. Erstattungen (positive Beträge in einer Ausgaben-Kategorie) mindern so das Ist ihrer Kategorie. | `K37` |
| R3 | **Soll je Kategorie:** Jede Kategorie kann ein Soll haben. Maßgeblich ist der Soll-Eintrag der Kategorie mit dem spätesten `validFrom ≤ M` (gilt ab M für alle folgenden Monate, bis ein neuerer Eintrag ihn ersetzt). Gibt es keinen solchen Eintrag oder sind darin beide Werte leer: kein Soll. Innerhalb des Eintrags gilt: Ist ein Euro-Wert gesetzt, gilt er. Sonst, wenn ein Prozentwert gesetzt ist: `E` × Prozent. Sonst: kein Soll. Der Euro-Wert hat immer Vorrang. Ein Soll ist immer ein Ausgabenbudget (Betrag ≥ 0). | `Einstellungen_Budget!F11:F30` |
| R4 | **Nicht verplant** = `E` − `P`, wobei `P` die Summe aller Soll-Werte (R3) ist. Zeigt, welcher Teil der Einnahmen kein Budget hat. Darf negativ sein (Überplanung). Bezeichnung: "Nicht verplant" bei ≥ 0, sonst "Überplant"; bei "Überplant" wird der Betrag ohne Minuszeichen und rot angezeigt. Eine reine Rechengröße, keine Kategorie. Angezeigt auf der Seite *Planung* für den gewählten Monat (Einnahmen, verplant, nicht verplant); sind in dem Monat noch keine Einnahmen gebucht, steht dort ein Hinweis. | `F31`, `G31`; bewusste Abweichung: im Sheet eine berechnete Rest-Kategorie, die im Soll gesamt mitzählte |
| R5 | **Abweichung zum Soll je Kategorie** = Soll − `I(k)`. Positiv heißt unter Budget, negativ über Budget. Ohne Soll: leer. | `F39:F59` |
| R6 | **Gesamt:** Soll gesamt = `P` (ohne nicht verplante Einnahmen); Ist gesamt = `A`; Abweichung zum Soll gesamt = `P` − `A` | `D37:F37`; bewusste Abweichung: im Sheet war Soll gesamt immer `E` und die Differenz damit gleich dem Überschuss |
| R7 | **Überschuss** = `E` − `A` (gleich der Summe aller `N(k)`, und gleich Nicht verplant + Abweichung zum Soll gesamt). Bezeichnung: "Monatlicher Überschuss" bei ≥ 0, sonst "Monatlicher Verlust". | `I17`, `I18` |
| R8 | **Anteile (Umschalter € / %):** Jeder Betrag im Dashboard (Einnahmen gesamt, Soll, Ist und Abweichung je Kategorie und Gesamt, wiederkehrende und einmalige Ausgaben, Überschuss, Einnahmen je Kategorie) wird zusätzlich als Anteil an `E` geliefert (Skala 4). Bei `E` = 0 oder ohne Betrag (kein Soll): leer. Der Umschalter im Dashboard wählt nur, welcher Wert angezeigt wird. Der Überschuss in Prozent ersetzt die frühere Sparquote. | bewusste Abweichung vom Sheet (`I15`, `I16`: Sparquote als Einnahmen / Ausgaben − 1) |
| R9 | **Einnahmen-Liste:** zeigt jede Kategorie mit `N(k) > 0`, mit `N(k)` und Anteil `N(k)` / `E` (R8). | `L39:L46` |
| R10 | **Budget-Tabelle und wiederkehrende Ausgaben:** Kategorien haben keine Gruppen. Die Budget-Tabelle zeigt jede Kategorie, die ein Soll oder ein `I(k) > 0` hat, in der Reihenfolge `sortOrder`; Kategorien ohne Soll und ohne Ausgaben entfallen. Es gibt keine Zwischensummen, nur die Gesamtzeile (R6). Fixkosten sind keine Eigenschaft einer Kategorie, sondern einer Transaktion: Was regelmäßig abgebucht wird, ist eine wiederkehrende Transaktion (R12). Sei `V(k)` die Summe von \|Betrag\| aller Vorkommen wiederkehrender Transaktionen der Kategorie mit negativem Betrag, die in M liegen (Ausnahmen beachtet). **Wiederkehrendes Ist je Kategorie** `W(k)` = min(`I(k)`, `V(k)`). **Wiederkehrende Ausgaben** `W` = Summe aller `W(k)`; **einmalige Ausgaben** = `A` − `W`. Erstattungen mindern so zuerst den einmaligen Teil, und es gilt immer 0 ≤ `W` ≤ `A`. Vorkommen mit positivem Betrag zählen nicht. | bewusste Abweichung vom Sheet (`Einstellungen_Budget!H11:J31`: Gruppen Fixkosten, Flexibel, Sparen je Kategorie) |
| R11 | **Vorzeichen:** Das Vorzeichen des Betrags entscheidet: positiv = Einnahme, negativ = Ausgabe. Ein Betrag von 0 ist ungültig. Kategorien haben keinen Typ. Im Formular wird der Betrag immer positiv eingegeben; ein Umschalter "Ausgabe / Einnahme" (Vorgabe: Ausgabe) setzt das Vorzeichen. | bewusste Abweichung vom Sheet (dort entschied die Kategorie) |
| R12 | **Vorkommen wiederkehrender Transaktionen:** Eine wiederkehrende Transaktion hat ein Intervall (monatlich = 1, vierteljährlich = 3, halbjährlich = 6, jährlich = 12 Monate), ein Startdatum und optional ein Enddatum (einschließlich). Ihre Vorkommen liegen am Startdatum plus k × Intervall Monate (k = 0, 1, 2, …), jeweils am Tag des Startdatums; hat ein Monat diesen Tag nicht, am letzten Tag des Monats (31.01. → 28.02. bzw. 29.02., 30.04.; 29.02. → 28.02. in Nicht-Schaltjahren). Ein Vorkommen zählt, wenn es nicht nach dem Enddatum liegt und für sein Datum keine Ausnahme (R13) existiert. Vorkommen werden berechnet, nicht als Transaktionen gespeichert. | neu, nicht im Sheet |
| R13 | **Vorkommen bearbeiten oder löschen:** Der Nutzer wählt "Nur dieser Eintrag" oder "Dieser und alle folgenden". Sei `d` das gewählte Vorkommen. **Nur dieser Eintrag:** Für `d` wird eine Ausnahme gespeichert, das Vorkommen zählt nicht mehr. Beim Bearbeiten wird zusätzlich eine normale Transaktion mit den geänderten Werten angelegt (Datum frei wählbar, Vorschlag `d`). **Dieser und alle folgenden:** Ist `d` das erste Vorkommen, wird die Serie selbst geändert bzw. gelöscht (beim Löschen samt ihrer Ausnahmen). Sonst wird das Enddatum der Serie auf den Tag vor `d` gesetzt, Ausnahmen ab `d` entfallen, und beim Bearbeiten entsteht eine neue Serie mit den geänderten Werten und derselben `seriesId`; ihr Startdatum (Vorschlag `d`) muss nach dem vorherigen Vorkommen liegen. Vergangene Vorkommen werden nie über die Serie rückwirkend geändert; eine Option "Alle Einträge" gibt es nicht. | neu, nicht im Sheet |
| R14 | **Auswertung für M:** **Ø Ausgaben:** Sei `x` der gewählte Zeitraum in Monaten (1 bis 120; Vorgabe 12; die Oberfläche bietet 3, 6, 12 und 24 an). Zeitraum sind die Monate M−`x` bis M−1, frühestens ab dem Monat des ersten Eintrags (frühestes Datum einer Transaktion oder frühestes Startdatum einer Serie, beliebiges Vorzeichen). Sei `n` die Anzahl dieser Monate (0 bis `x`). Ø Ausgaben = Summe von `A` (R2) dieser Monate / `n`; ebenso Ø Einnahmen (`E`) und Ø Überschuss (`E` − `A`, R7); bei `n` = 0 leer. **Rückblick:** Für jeden dieser `n` Monate (aufsteigend) Einnahmen `E(m)`, Ausgaben `A(m)` und Überschuss `E(m)` − `A(m)`, dazu die Einnahmen je Kategorie (`N(k)` > 0) und die Ausgaben je Kategorie (`I(k)` > 0); ein Monat ohne Einträge hat überall 0. Jeder dieser Werte wird zusätzlich als Anteil an `E(m)` geliefert (Skala 4, wie R8), bei `E(m)` = 0 leer. **Gesamt:** Summe von `E`, `A` und `E` − `A` über die `n` Monate, bei `n` = 0 leer; Ausgaben und Überschuss zusätzlich als Anteil an der Summe von `E` (gleich dem Anteil der Ø-Werte an Ø Einnahmen), bei 0 leer. **Rücklagen-Topf:** Für jede Serie mit negativem Betrag, Intervall `i` > 1 Monat und einem nächsten Vorkommen `d` in M bis M+11 (R12, Ausnahmen beachtet) sei `k` die Anzahl Monate von M bis zum Monat von `d` (0, wenn `d` in M liegt). **Pro Monat** = \|Betrag\| / `i`; **Angespart** = \|Betrag\| × max(0, `i` − `k`) / `i` (im Fälligkeitsmonat ist der Topf voll, danach beginnt er bei einer Monatsrate). **Monatlich zurücklegen** = Summe aller Pro-Monat-Werte; **Sollte jetzt angespart sein** = Summe aller Angespart-Werte. **Vorschau:** Für M und die folgenden 11 Monate je Monat alle Vorkommen von Serien mit negativem Betrag (R12, Ausnahmen beachtet) und alle einzelnen Transaktionen mit negativem Betrag, je mit \|Betrag\|, und ihre Summe. Einnahmen zählen nicht. Rücklagen-Topf und Vorschau schauen immer 12 Monate nach vorn, unabhängig von `x`. | neu, nicht im Sheet |

**Regeln für den Ausbau ab 27.10.2026 (Abschnitt 14):**

| # | Regel | Quelle |
|---|---|---|
| R15 | **Mandantentrennung:** Jede fachliche Entität (Kategorie, Soll, Transaktion, Serie, Ausnahme, Person, Ausgleich, Depot, …) gehört genau einem Nutzer (`owner`). Jede Abfrage und jede Berechnung (R1 bis R14) sieht nur die Daten des angemeldeten Nutzers. Eine Ressource eines anderen Nutzers in der URL ergibt 404 (wie eine unbekannte, damit ihre Existenz nicht erkennbar ist); ein Verweis im Body auf eine fremde Ressource (z. B. `categoryId`) ergibt 400. Ohne Anmeldung ergibt jeder Aufruf außer Anmeldung und Einladung annehmen 401. | neu |
| R16 | **Geteilte Ausgaben, eigener Anteil:** Eine Ausgabe (Betrag < 0) kann mit Personen geteilt werden; Einnahmen können nicht geteilt werden (400). Der Betrag der Transaktion bzw. der Serie ist **mein Anteil** mit Vorzeichen; nur er zählt im Budget (R1 bis R14 bleiben unverändert). Das gilt auch, wenn eine andere Person bezahlt hat: Ich habe konsumiert und schulde ihr meinen Anteil. Gesamtbetrag, Zahler und Anteile der Personen stehen in der Aufteilung. | bewusste Abweichung von Entscheidung 6 (Entscheidung 22) |
| R17 | **Aufteilung:** Gesamtbetrag `G` > 0, Zahler (ich oder eine Person), Beteiligte (ich und mindestens eine Person). Alle Anteile ≥ 0, mein Anteil > 0 (R11), zusammen genau `G` (sonst 400). Lege ich nur Geld für eine Person aus, ohne selbst einen Anteil zu haben, ist das ein Ausgleich (R18), keine Transaktion. Eingabearten: **Beträge** (je Beteiligten ein Betrag), **gleichmäßig** (je Beteiligten `G` / Anzahl) und **Prozent** (je Beteiligten `G` × Prozent, Prozente zusammen 100). Gleichmäßig und Prozent runden den Anteil jeder Person auf Cent (HALF_UP); mein Anteil ist `G` minus die Summe dieser Anteile und nimmt so den Rundungsrest auf. Die Anteile rechnet das Backend (keine Fachlogik im Frontend, wie R8); gespeichert werden nur die Beträge, die Eingabeart nicht. Bei einer Serie gilt die Aufteilung für jedes Vorkommen. | Splitwise |
| R18 | **Saldo je Person `p`:** Es zählen geteilte Transaktionen und Vorkommen geteilter Serien (R12, Ausnahmen beachtet) mit Datum bis einschließlich heute. Saldo(`p`) = Summe der Anteile von `p` an Einträgen, die ich bezahlt habe − Summe meiner Anteile an Einträgen, die `p` bezahlt hat − Summe der Ausgleichszahlungen mit `p`. Ein Ausgleich hat einen Betrag mit Vorzeichen: positiv, wenn `p` mir zahlt, negativ, wenn ich `p` zahle. Saldo > 0: `p` schuldet mir; < 0: ich schulde `p`. Anteile anderer Personen an Einträgen, die `p` bezahlt hat, gehen nur `p` etwas an und zählen nicht. Ausgleichszahlungen zählen nicht im Budget. R13 gilt mit Aufteilung: "Nur dieser Eintrag" übernimmt die Aufteilung des Vorkommens in die Ersatz-Transaktion (änderbar), "Dieser und alle folgenden" in die neue Serie. | Splitwise |
| R19 | **Sparplan und Prognose (Rahmen, vor AP 21 aus den Formeln des Investment-Blatts präzisieren):** Ein Depot hat einen Startwert `V(0)` zu einem Startmonat, einen Prognosezeitraum in Jahren und drei vom Nutzer eingegebene Renditen p. a. (pessimistisch, realistisch, optimistisch), optional eine Inflationsrate. Ein Sparplan ist eine Serie mit negativem Betrag, die mit dem Depot verknüpft ist (Einzahlung je Vorkommen = \|Betrag\|). Je Szenario mit Monatszins `r_m` = (1 + `r`)^(1/12) − 1: `V(t+1)` = `V(t)` × (1 + `r_m`) + Einzahlungen im Monat `t+1` (Vorkommen von Sparplänen nach R12 und Einmalzahlungen nach R20). Ergebnis je Jahr: Wert, Summe der Einzahlungen, Ertrag (Wert − Startwert − Einzahlungen), optional inflationsbereinigt (geteilt durch (1 + Inflation)^Jahre). Zwischenwerte mit Skala 10, Ergebnis mit Skala 2. Die App gibt keine Renditen vor und keine Anlageempfehlung. | Investment-Blätter "Sparplan", "Prognosen" |
| R20 | **Einmalzahlungen:** Eine Transaktion kann mit einem Depot verknüpft werden, mit der Art Einzahlung (Betrag < 0), Auszahlung (Betrag > 0) oder Ertrag (Betrag > 0, z. B. Dividende). Einzahlungen erhöhen, Auszahlungen mindern die Prognose (R19) ab ihrem Monat; Erträge zählen für R21. Im Budget zählen sie wie jede Transaktion (R1). | Investment-Blatt "Einmalzahlung" |
| R21 | **Freistellungsauftrag:** Der Sparerpauschbetrag je Jahr ist eine Einstellung des Nutzers, kein fester Wert im Code. Je Bank (Feld des Depots) und Jahr ein Freistellungsauftrag in Euro. **Ausgeschöpft** je Bank = Summe der Erträge (R20) des Jahres aus Depots dieser Bank; **Rest** = Auftrag − Ausgeschöpft. Hinweise: Summe der Aufträge > Pauschbetrag; Ausgeschöpft > Auftrag. Keine Steuerberechnung. | Investment-Blatt "Freistellungsauftrag" |
| R22 | **Depot mit Positionen (Rahmen, eigene Spec-Runde vor AP 25):** Positionen (ISIN, Name) mit Käufen und Verkäufen (Datum, Stück, Kurs, Gebühren); Bestand = Summe der Stück; Einstandswert nach Durchschnittskosten; aktueller Wert = Bestand × zuletzt eingegebener Kurs; Gewinn/Verlust = Wert − Einstandswert. Kurse zuerst manuell; eine externe Kursquelle ist eine eigene Entscheidung. | Investment-Tracker |

**Nicht übernehmen:** Die Hilfsblätter `IGNORE…` (Diagrammdaten, rechnet das Frontend selbst) sowie veraltete Formeln in `Dashboard_Budget` (Zeilen 38 und 48–55 der Einnahmenspalte verweisen auf ein nicht existierendes Blatt `Monatliche_Transaktionen`). Als Referenzwerte gelten die Formeln, nicht diese Reste.

**Geldbeträge:** `BigDecimal` im Backend (Skala 2, Rundung HALF_UP), im JSON als String oder Zahl mit 2 Nachkommastellen. Kein `double`.

## 3. User Stories und Akzeptanzkriterien

### Must
- **US1 Kategorien ansehen:** Als Nutzer sehe ich alle Kategorien als eine Liste in fester Reihenfolge (`sortOrder`), ohne Gruppen. *AK:* Beim ersten Start sind die Kategorien des Sheets als Seed vorhanden; jeder Kategoriename ist eindeutig.
- **US2 Soll festlegen:** Als Nutzer lege ich je Kategorie ab einem gewählten Monat ein Soll in Euro oder in Prozent fest. Es gilt für diesen und alle folgenden Monate, bis ein neuerer Eintrag es ersetzt; frühere Monate bleiben unverändert. *AK:* R3 gilt; Prozent zwischen 0 und 100; negativer Euro-Wert wird abgelehnt (400 mit Fehlermeldung); erneutes Speichern für denselben Monat überschreibt den Eintrag dieses Monats; beide Werte leer heißt "kein Soll ab diesem Monat"; es gibt keinen Speichern-Button: eine Änderung wird gespeichert, sobald der Fokus die Zeile verlässt oder Enter gedrückt wird.
- **US3 Transaktion erfassen:** Als Nutzer erfasse ich Betrag, Beschreibung, Datum und Kategorie und wähle per Umschalter "Ausgabe" oder "Einnahme". *AK:* R11; eingegebener Betrag > 0, gespeichert mit Vorzeichen (API: Betrag ≠ 0); Beschreibung höchstens 100 Zeichen; Kategorie muss existieren (sonst 400); Datum Pflicht.
- **US4 Transaktion ändern/löschen.**
- **US5 Dashboard:** Als Nutzer wähle ich einen Monat und sehe Einnahmen, Ausgaben (davon wiederkehrend und einmalig) und Überschuss sowie eine Budget-Tabelle mit Soll, Ist und Abweichung zum Soll je Kategorie und Gesamt. Ein Umschalter zeigt alle Werte in € oder in % der Einnahmen. *AK:* R1 bis R10; Werte entsprechen der Testfixture F1 (Abschnitt 12); bei Einnahmen = 0 zeigen die Prozentwerte "–"; auf schmalen Bildschirmen entfällt die Spalte Soll.

### Should
- **US6 Wiederkehrende Transaktionen erfassen:** Als Nutzer erfasse ich eine wiederkehrende Transaktion im selben Formular wie eine einmalige: Betrag mit Umschalter "Ausgabe / Einnahme", Beschreibung, Datum, Kategorie und "Wiederholen" (nie, monatlich, vierteljährlich, halbjährlich, jährlich). Bei einer Wiederholung ist das Datum die erste Abbuchung (Startdatum), und ein optionales Enddatum erscheint. Eine Serie wird über ihre Vorkommen in der Monatsliste geändert oder beendet (R13); eine eigene Serienliste gibt es nicht. *AK:* R11, R12; Betrag > 0 eingegeben, Beschreibung höchstens 100 Zeichen, Kategorie muss existieren (sonst 400), Startdatum Pflicht, Enddatum nicht vor dem Startdatum (sonst 400); die Vorkommen erscheinen in der Transaktionsliste ihres Monats, als "wiederkehrend" gekennzeichnet, und zählen im Dashboard mit vollem Betrag (R1).
- **US6a Wiederkehrende Einträge bearbeiten/löschen:** Als Nutzer bearbeite oder lösche ich ein Vorkommen und wähle "Nur dieser Eintrag" oder "Dieser und alle folgenden". *AK:* R13; frühere Vorkommen bleiben unverändert; ein Datum, das kein Vorkommen der Serie ist, ergibt 404.
- **US7 Wiederkehrende und einmalige Ausgaben** (ersetzt die Gruppensummen): Als Nutzer sehe ich in der Kachel Ausgaben, wie viel davon wiederkehrend (meine Fixkosten) und wie viel einmalig war. *AK:* R10; die beiden Werte ergeben zusammen die Ausgaben; Umschalter € / % gilt auch hier (R8).
- **US7a Nicht verplant:** Als Nutzer sehe ich auf der Seite *Planung* für den gewählten Monat Einnahmen, verplantes Soll und den nicht verplanten Teil (R4).
- **US8 Monatsnavigation** (vor/zurück) und Anzeige leerer Monate ohne Fehler.
- **US13 Auswertung:** Als Nutzer sehe ich für den gewählten Monat, wie viel ich in den letzten 12 Monaten durchschnittlich ausgegeben habe, mit einem Rückblick auf Einnahmen, Ausgaben und Überschuss je Monat, wie viel ich monatlich für seltene wiederkehrende Ausgaben zurücklegen sollte und wie viel dafür jetzt angespart sein sollte, sowie eine Vorschau aller gebuchten Ausgaben der nächsten 12 Monate, die teure Monate sichtbar macht. *AK:* R14; gibt es noch keine 12 abgeschlossenen Monate mit Einträgen, nennt die Seite die Anzahl der berücksichtigten Monate; ohne abgeschlossenen Monat zeigt sie "–" und einen Hinweis. *Erweiterung (AP 16):* Der Zeitraum für Ø und Rückblick ist wählbar (Auswahl 3, 6, 12 oder 24 Monate, Vorgabe 12); über der Rückblick-Tabelle zeigt ein Balkendiagramm (Recharts) Einnahmen und Ausgaben je Monat (ersetzt US9); ein Zeitraum außerhalb von 1 bis 120 Monaten in der API → 400. *Erweiterung (AP 15a):* Ein Umschalter am Rückblick zeigt seine Werte in € oder in % der Einnahmen des jeweiligen Monats (wie US5); unter der Ø-Zeile steht eine Zeile Gesamt mit den Summen des Zeitraums; Rückblick und Vorschau lassen sich einklappen, der Browser merkt sich den Zustand (Vorgabe offen). *AK:* bei Einnahmen = 0 zeigen die Prozentwerte "–"; Rücklagen, Vorschau und Ø Ausgaben bleiben in €.

### Ausbau ab 27.10.2026: Must (Voraussetzung für die reale Nutzung)
- **US10 Kategorien verwalten** (früher Nice): Als Nutzer lege ich unter *Einstellungen › Kategorien* Kategorien an, benenne sie um, ändere ihre Reihenfolge, archiviere oder lösche sie. *AK:* Name 1 bis 50 Zeichen, eindeutig je Nutzer (sonst 409); Löschen nur ohne Transaktionen, Serien und Soll-Einträge, sonst 409 mit dem Hinweis, die Kategorie zu archivieren; eine archivierte Kategorie fehlt in den Formularen und auf der Seite *Planung*, vergangene Monate in *Übersicht* und *Auswertung* zeigen sie weiter korrekt; eine neue Kategorie steht am Ende der Reihenfolge. Buchungen auf einer archivierten Kategorie bleiben erlaubt, damit bestehende Einträge bearbeitet werden können; das Formular zeigt sie nur, wenn sie schon gewählt ist. Gleiche Namen werden ohne Beachtung der Groß- und Kleinschreibung erkannt.
- **US11 Flyway-Migrationen** statt Seeder (früher Nice). *AK:* Das Schema entsteht nur aus Migrationen; eine gemergte Migration wird nie geändert; die Standard-Kategorien legt der Service beim Anlegen eines Nutzers an (US15), nicht eine Migration.
- **US14 Anmelden und Abmelden:** Als Nutzer melde ich mich mit E-Mail und Passwort an und wieder ab. *AK:* R15; falsche Angaben ergeben eine allgemeine Fehlermeldung (ohne Hinweis, ob die E-Mail existiert); nach 5 Fehlversuchen in 15 Minuten wird die Anmeldung für diese E-Mail 15 Minuten gesperrt; die Sitzung läuft nach 30 Tagen ohne Nutzung ab.
- **US15 Einladung:** Als Admin erzeuge ich einen Einladungslink und schicke ihn selbst weiter (kein Mailversand durch die App). Wer den Link öffnet, legt E-Mail, Anzeigename und Passwort fest und erhält die Standard-Kategorien (Seed, Abschnitt 4) ohne Beträge. *AK:* Link einmalig nutzbar und 7 Tage gültig (sonst 410); E-Mail eindeutig (409); Passwort mindestens 12 Zeichen. Der erste Admin wird beim ersten Start aus Umgebungsvariablen angelegt.
- **US16 Konto:** Als Nutzer ändere ich mein Passwort (altes Passwort nötig), exportiere alle meine Daten (JSON) und lösche mein Konto samt allen Daten. *AK:* Löschen erfordert die Eingabe des Passworts; danach existieren keine Daten des Nutzers mehr.

### Ausbau ab 27.10.2026: Should
- **US17 Personen:** Als Nutzer lege ich unter *Einstellungen › Personen* Personen an, mit denen ich Ausgaben teile (nur Name, eindeutig je Nutzer), und benenne sie um. *AK:* Löschen nur bei Saldo 0 und ohne geteilte Einträge, sonst 409.
- **US18 Ausgabe teilen:** Als Nutzer teile ich im Transaktionsformular eine einmalige oder wiederkehrende Ausgabe: Gesamtbetrag, wer bezahlt hat, Beteiligte und Aufteilung (Beträge, gleichmäßig oder Prozent), z. B. "200 € meines Anteils von 500 €". *AK:* R16, R17; das Formular zeigt meinen Anteil, bevor gespeichert wird; im Budget und in der Monatsliste steht mein Anteil, die Liste kennzeichnet geteilte Einträge ("geteilt, gesamt 500,00"); R13 behält die Aufteilung (R18).
- **US19 Salden und Ausgleich:** Als Nutzer sehe ich auf der Seite *Teilen* je Person den Saldo ("schuldet dir" / "du schuldest") und die geteilten Einträge und erfasse einen Ausgleich (Betrag, Richtung, Datum). *AK:* R18; Werte entsprechen der Testfixture F2 (Abschnitt 12); ein Ausgleich erscheint nicht im Dashboard.
- **US20 Sparplan und Prognose:** Als Nutzer lege ich Depots an, verknüpfe Serien als Sparpläne und sehe je Depot die Prognose in drei Szenarien als Tabelle je Jahr und als Diagramm. *AK:* R19; nur Serien mit negativem Betrag (sonst 400); eine Serie gehört zu höchstens einem Depot.
- **US21 Einmalzahlungen:** Als Nutzer verknüpfe ich eine Transaktion mit einem Depot (Einzahlung, Auszahlung, Ertrag). *AK:* R20; Vorzeichen passend zur Art (sonst 400).
- **US22 Freistellungsauftrag:** Als Nutzer trage ich den Sparerpauschbetrag des Jahres und je Bank meinen Freistellungsauftrag ein und sehe, wie viel ausgeschöpft ist. *AK:* R21.

### Nice
- **US9 Diagramm** (Soll gegen Ist je Kategorie) mit Recharts. *Ersetzt durch das Diagramm in US13 (Erweiterung).*
- **US23 Depot mit Positionen (vorgemerkt, eigene Spec-Runde):** R22.
- **US24 Teilen mit verknüpften Nutzern (vorgemerkt, eigene Spec-Runde):** Eine Person kann mit einem registrierten Nutzer verknüpft werden (Anfrage und Bestätigung). Eine geteilte Ausgabe erscheint beim anderen Nutzer im Posteingang; erst wenn er eine eigene Kategorie wählt, wird sein Anteil bei ihm gebucht (Kategorien sind je Nutzer verschieden). Beide sehen denselben Saldo mit umgekehrtem Vorzeichen; ändert der Ersteller den Eintrag, erscheint die Änderung erneut im Posteingang.
- **US12 Kategorie aufklappen (vorgemerkt, später):** Im Dashboard zeigt ein Klick auf eine Kategorie ihre Buchungen und Vorkommen wiederkehrender Transaktionen im Monat (R1, R12). Passt gut zusammen mit US9.

## 4. Datenmodell

```
Category
  id            Long, PK
  name          String, eindeutig, nicht leer
  sortOrder     int

CategoryTarget                         (Soll ab einem Monat, siehe R3)
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

**Ausbau ab 27.10.2026:** `Category`, `CategoryTarget`, `Transaction` und `RecurringTransaction` erhalten `owner → User` (R15; `CategoryTarget` und `RecurringException` hängen über ihre Kategorie bzw. Serie am Nutzer und brauchen kein eigenes Feld). Der Kategoriename ist eindeutig **je Nutzer**: eindeutig `(owner, name)`. Neu an `Category`: `archived boolean` (US10).

```
User
  id            Long, PK
  email         String, eindeutig (klein geschrieben gespeichert)
  passwordHash  String                 (BCrypt oder Argon2, nie das Passwort)
  displayName   String, 1..50
  role          USER | ADMIN
  createdAt     Instant

Invitation                             (US15)
  id            Long, PK
  token         String, eindeutig      (zufällig, mindestens 128 Bit)
  createdBy     → User
  expiresAt     Instant                (7 Tage nach Erzeugung)
  usedAt        Instant, nullable

Person                                 (US17)
  id            Long, PK
  owner         → User
  name          String, 1..50, eindeutig je owner
  linkedUser    → User, nullable       (erst US24)

Split                                  (Aufteilung, R16, R17; gehört zu genau einer Transaktion oder Serie)
  id            Long, PK
  transaction   → Transaction, nullable
  recurring     → RecurringTransaction, nullable    (genau eins von beiden gesetzt)
  totalAmount   BigDecimal > 0         (Gesamtbetrag G, je Vorkommen bei Serien)
  paidBy        → Person, nullable     (null = ich)

SplitShare                             (Anteil einer Person; mein Anteil ist −amount der Transaktion bzw. Serie)
  id            Long, PK
  split         → Split
  person        → Person
  amount        BigDecimal ≥ 0
  eindeutig: (split, person)

Settlement                             (Ausgleich, R18; zählt nicht im Budget)
  id            Long, PK
  owner         → User
  person        → Person
  amount        BigDecimal ≠ 0         (positiv: Person zahlt mir; negativ: ich zahle der Person)
  date          LocalDate
  note          String, 0..100

Depot                                  (R19 bis R21)
  id            Long, PK
  owner         → User
  name          String, 1..50
  bank          String, 1..50          (für den Freistellungsauftrag, R21)
  startValue    BigDecimal ≥ 0
  startMonth    LocalDate              (1. des Monats)
  horizonYears  int, 1..50
  ratePessimistic, rateRealistic, rateOptimistic   BigDecimal (Anteil p. a., z. B. 0.05)
  inflationRate BigDecimal, nullable

DepotLink                              (Sparplan bzw. Einmalzahlung, R19, R20)
  id            Long, PK
  depot         → Depot
  transaction   → Transaction, nullable
  recurring     → RecurringTransaction, nullable    (genau eins von beiden; eine Serie oder Transaktion höchstens einmal)
  kind          CONTRIBUTION | WITHDRAWAL | INCOME  (Serien nur CONTRIBUTION)

TaxAllowance                           (Sparerpauschbetrag je Jahr, R21)
  owner, year, amount                  eindeutig: (owner, year)

ExemptionOrder                         (Freistellungsauftrag je Bank und Jahr, R21)
  owner, bank, year, amount            eindeutig: (owner, bank, year)
```

Die Positionen des Depots (R22, US23) werden in ihrer eigenen Spec-Runde modelliert.

Seed (aus `Einstellungen_Budget`, ohne Gruppen, in dieser Reihenfolge `sortOrder`; ab AP 18 für jeden neuen Nutzer, siehe US15): Wohnen, Nebenkosten, Versicherungen, Mobilität, Kredite, Spenden, Lebensmittel, Freizeit, Urlaub, Persönlicher Bedarf, Geschenke, Gesundheit, Investitionen, Selbstständigkeit, Gehalt/Lohn (netto), Nebenjob, Einnahmen Selbstständigkeit, Kapitalerträge, Staatliche Leistungen, Sonstige Einnahmen. Sparen und Investieren sind normale Kategorien. Die Platzhalter-Zeilen des Sheets entfallen, weil die App Kategorien frei anlegen lässt (US10). Der im Sheet doppelte Name "Selbstständigkeit" ist umbenannt, weil Namen eindeutig sind. Die berechnete Rest-Kategorie des Sheets entfällt (R4).

**Seed-Beträge:** ausschließlich erfundene Werte, keine echten Zahlen (Repo ist öffentlich einsehbar).

## 5. REST-API (Skizze für OpenAPI)

Basis: `/api`, JSON. Fehler als RFC 7807 (`ProblemDetail`), Validierung per Bean Validation. Faustregel: Unbekannte Ressource in der URL → 404; ungültiger Inhalt im Body oder in Parametern (auch eine unbekannte `categoryId`) → 400.

| Methode | Pfad | Zweck | Erfolg |
|---|---|---|---|
| GET | `/categories?month=2026-10` | Alle Kategorien, sortiert, mit dem in diesem Monat gültigen Soll (`targetAmount`, `targetPercent`, `targetValidFrom`) und `archived` (ab AP 15) | 200, 400 (Monat fehlt/ungültig) |
| PUT | `/categories/{id}/target?month=2026-11` | Soll ab diesem Monat, Body `{targetAmount?, targetPercent?}` | 200, 404, 400 (auch Monat fehlt/ungültig) |
| GET | `/transactions?month=2026-10` | Einträge eines Monats: Transaktionen (`kind: "SINGLE"`, `id`) und Vorkommen wiederkehrender Transaktionen (`kind: "RECURRING"`, `recurringId`, `occurrenceDate`), je mit `amount` (mit Vorzeichen), `description`, `date`, Kategorie | 200, 400 (Monat fehlt/ungültig) |
| POST | `/transactions` | Anlegen, Body `{amount, description, date, categoryId}` | 201 + Location, 400 |
| PUT | `/transactions/{id}` | Ändern | 200, 404, 400 |
| DELETE | `/transactions/{id}` | Löschen | 204, 404 |
| GET | `/recurring-transactions?month=2026-10` | Serien, die in diesem Monat aktiv sind (Startdatum nicht nach dem letzten Tag, Enddatum nicht vor dem ersten Tag des Monats), je mit `monthlyEquivalent` (\|Betrag\| / Intervall in Monaten, auf Cent gerundet) | 200, 400 (Monat fehlt/ungültig) |
| POST | `/recurring-transactions` | Anlegen, Body `{amount, description, categoryId, interval, startDate, endDate?}` | 201 + Location, 400 |
| PUT | `/recurring-transactions/{id}/occurrences/{date}?scope=THIS` | Nur dieses Vorkommen ändern (R13), Body wie `POST /transactions` | 200, 404 (Serie oder Vorkommen unbekannt), 400 |
| PUT | `/recurring-transactions/{id}/occurrences/{date}?scope=FOLLOWING` | Dieses und alle folgenden ändern (R13), Body wie `POST /recurring-transactions` | 200, 404, 400 |
| DELETE | `/recurring-transactions/{id}/occurrences/{date}?scope=THIS\|FOLLOWING` | Löschen (R13) | 204, 404, 400 (Scope fehlt/ungültig) |
| GET | `/dashboard?month=2026-10` | Berechnetes Dashboard | 200, 400 (ungültiger Monat) |
| GET | `/reserves?month=2026-10&months=12` | Auswertung (R14); `months` optional (1 bis 120; Vorgabe 12) | 200, 400 (ungültiger Monat oder Zeitraum) |

**Ausbau ab 27.10.2026:** Alle Endpunkte oben gelten nur für die Daten des angemeldeten Nutzers (R15). Zusätzliche Faustregeln: nicht angemeldet → 401; Ressource eines anderen Nutzers in der URL → 404; Verweis auf eine fremde Ressource im Body → 400; Name schon vergeben → 409. Schreibende Aufrufe brauchen ein CSRF-Token (Abschnitt 15).

| Methode | Pfad | Zweck | Erfolg |
|---|---|---|---|
| POST | `/auth/login` | Anmelden, Body `{email, password}`, setzt das Session-Cookie (US14) | 204, 401, 429 (gesperrt) |
| POST | `/auth/logout` | Abmelden | 204 |
| GET | `/auth/me` | Angemeldeter Nutzer `{id, email, displayName, role}` | 200, 401 |
| POST | `/invitations` | Einladung erzeugen, nur `ADMIN` (US15); Antwort enthält den Link | 201, 403 |
| POST | `/invitations/{token}/accept` | Einladung annehmen, Body `{email, displayName, password}` | 201, 404, 410 (abgelaufen/benutzt), 409, 400 |
| PUT | `/account/password` | Passwort ändern, Body `{currentPassword, newPassword}` (US16) | 204, 400 |
| GET | `/account/export` | Alle eigenen Daten als JSON (US16) | 200 |
| DELETE | `/account` | Konto samt Daten löschen, Body `{password}` (US16) | 204, 400 |
| POST | `/categories` | Kategorie anlegen, Body `{name}` (US10) | 201 + Location, 400, 409 |
| PUT | `/categories/{id}` | Ändern, Body `{name, sortOrder, archived}`; hat eine andere Kategorie schon diese `sortOrder`, nimmt die Kategorie ihren Platz ein (verschieben um eine Position); danach werden alle Kategorien neu durchnummeriert (10, 20, …), was auch gleiche `sortOrder` repariert | 200, 404, 400, 409 |
| DELETE | `/categories/{id}` | Löschen (nur unbenutzt, US10) | 204, 404, 409 |
| GET/POST | `/persons` | Personen lesen/anlegen (US17) | 200 / 201, 400, 409 |
| PUT/DELETE | `/persons/{id}` | Umbenennen/Löschen | 200 / 204, 404, 409 |
| POST | `/splits/preview` | Anteile berechnen ohne zu speichern, Body `{totalAmount, paidByPersonId?, mode: EXACT\|EQUAL\|PERCENT, shares: [{personId?, value}]}` (`personId` leer = ich); Antwort `{myShare, shares: [{personId, amount}]}` (R17) | 200, 400 |
| POST/PUT | `/transactions`, `/recurring-transactions` | Body zusätzlich optional `split: {totalAmount, paidByPersonId?, mode, shares}`; `amount` entfällt dann und ist mein Anteil (R16, R17) | wie oben |
| GET | `/balances` | Saldo je Person mit geteilten Einträgen und Ausgleichszahlungen (R18) | 200 |
| POST | `/settlements` | Ausgleich erfassen, Body `{personId, amount, date, note?}` (US19) | 201, 400 |
| DELETE | `/settlements/{id}` | Ausgleich löschen | 204, 404 |
| GET/POST | `/depots` | Depots lesen/anlegen (US20) | 200 / 201, 400 |
| PUT/DELETE | `/depots/{id}` | Ändern/Löschen (Verknüpfungen entfallen, Buchungen bleiben) | 200 / 204, 404 |
| PUT/DELETE | `/depots/{id}/links` | Serie oder Transaktion verknüpfen bzw. lösen, Body `{recurringId?, transactionId?, kind}` (R19, R20) | 200 / 204, 404, 400, 409 (schon verknüpft) |
| GET | `/depots/{id}/projection` | Prognose je Szenario und Jahr (R19) | 200, 404 |
| GET/PUT | `/tax-allowance/{year}` | Sparerpauschbetrag und Freistellungsaufträge je Bank, mit Ausschöpfung (R21) | 200, 400 |

**Dashboard-Antwort (Kern):**
```json
{
  "month": "2026-10",
  "incomeTotal": 2000.00,
  "incomeTotalShare": 1.0,
  "expenses": { "target": 1800.00, "actual": 1635.00, "difference": 165.00,
                "targetShare": 0.9, "actualShare": 0.8175, "differenceShare": 0.0825,
                "recurring": 900.00, "oneOff": 735.00,
                "recurringShare": 0.45, "oneOffShare": 0.3675 },
  "unplanned": 200.00, "unplannedLabel": "Nicht verplant",
  "surplus": 365.00, "surplusLabel": "Monatlicher Überschuss", "surplusShare": 0.1825,
  "categories":[ { "id": 1, "name": "…", "target": 500.00, "actual": 500.00, "difference": 0.00,
                   "targetShare": 0.25, "actualShare": 0.25, "differenceShare": 0.0 } ],
  "income":    [ { "id": 20, "name": "…", "actual": 2000.00, "share": 1.0 } ]
}
```
`difference` ist die Abweichung zum Soll (R5, R6), `unplanned` sind die nicht verplanten Einnahmen (R4), `…Share` und `share` sind Anteile an `E` (R8), `expenses.recurring` ist `W` und `expenses.oneOff` ist `A` − `W` (R10). `categories[].actual` ist `I(k)`, `income[].actual` ist `N(k)` (R2, R9). (Zahlen im Beispiel nur zur Form.)

**Rücklagen-Antwort (Kern):**
```json
{
  "month": "2026-10",
  "averageIncome": 0.00, "averageExpenses": 1026.00, "averageSurplus": -1026.00, "averageMonths": 9,
  "history":  [ { "month": "2026-09", "income": 0.00, "expenses": 1914.00, "surplus": -1914.00,
                  "incomeCategories": [], "expenseCategories": [ { "name": "Wohnen", "amount": 900.00 }, … ] } ],
  "monthlyReserve": 20.00, "reserveBalance": 240.00,
  "reserves": [ { "recurringId": 3, "description": "…", "categoryName": "…", "amount": 240.00, "interval": "YEARLY",
                  "nextDate": "2026-10-15", "monthly": 20.00, "saved": 240.00 } ],
  "forecast": [ { "month": "2026-10", "total": 2636.49,
                  "entries": [ { "kind": "RECURRING", "id": null, "recurringId": 3, "description": "…", "date": "2026-10-15", "amount": 240.00 },
                               { "kind": "SINGLE", "id": 12, "recurringId": null, "description": "…", "date": "2026-10-02", "amount": 200.00 } ] } ]
}
```
`averageIncome`, `averageExpenses` und `averageSurplus` sind `null`, wenn `averageMonths` 0 ist; `history` hat dann keine Einträge, sonst genau `averageMonths`. `forecast` hat genau 12 Einträge (M bis M+11). Beträge in `reserves` und `forecast` sind Kosten und daher positiv. Mit `months` = `x` hat `history` höchstens `x` Einträge (R14).

**Salden-Antwort (Kern):**
```json
{
  "persons": [ { "id": 1, "name": "Person A", "balance": 503.33,
                 "entries": [ { "kind": "SINGLE", "id": 7, "recurringId": null, "date": "2026-10-03", "description": "…",
                                "totalAmount": 500.00, "paidBy": null, "myShare": 200.00, "personShare": 300.00, "effect": 300.00 } ],
                 "settlements": [ { "id": 2, "date": "2026-10-15", "amount": 1000.00 } ] } ]
}
```
`balance` > 0 heißt "schuldet dir", < 0 "du schuldest" (R18). `effect` ist der Beitrag des Eintrags zum Saldo. `paidBy` ist `null`, wenn ich bezahlt habe, sonst die `id` der Person.

## 6. Architektur

**Repo (Monorepo):** `/backend` (Maven, Java 21, Spring Boot 4), `/frontend` (Vite, React, TypeScript), `/docs` (diese Spezifikation, ADRs), `.github/workflows`, `CLAUDE.md`, `README.md`.

**Backend:** Schichten Controller → Service → Repository (Spring Data JPA, H2 als Datei-DB). **Die Berechnungen liegen in reinen Klassen** ohne Spring-Abhängigkeit: `RecurrenceSchedule` berechnet die Vorkommen einer Serie in einem Zeitraum (R12), `DashboardCalculator` nimmt Kategorien, Buchungen und Monat und liefert das Dashboard-DTO (R1 bis R10); Vorkommen wiederkehrender Transaktionen gehen dabei wie Transaktionen ein, weil sie mit vollem Betrag zählen (R1), bleiben aber als Vorkommen erkennbar, damit der Rechner die wiederkehrenden Ausgaben (R10) bestimmen kann, `ReserveCalculator` liefert die Auswertung (R14). Die Aufteilung von Serien (R13) liegt im Service. Das hält die Regeln isoliert testbar (Single Responsibility, Dependency Inversion). DTOs als Java-Records, Entities nie direkt nach außen. `springdoc-openapi` erzeugt die API-Doku aus dem Code.

**Frontend:** Reiter *Übersicht* · *Transaktionen* · *Planung* · *Auswertung*. *Übersicht* ist das Dashboard (Kacheln Einnahmen, Ausgaben mit "davon wiederkehrend" und "davon einmalig", Überschuss; Budget-Tabelle je Kategorie mit Gesamtzeile; Einnahmen-Liste; Umschalter € / %), *Transaktionen* (ein Formular für einmalige und wiederkehrende Transaktionen mit "Wiederholen"; Monatsliste inkl. Vorkommen wiederkehrender Transaktionen; beim Bearbeiten oder Löschen eines Vorkommens fragt ein Dialog "Nur dieser Eintrag" oder "Dieser und alle folgenden"; Serien werden nur über ihre Vorkommen bearbeitet), *Planung* (Soll je Kategorie, mit "Nicht verplant", R4), *Auswertung* (Kachel Ø Ausgaben der letzten 12 Monate, darunter der Rückblick: Tabelle mit Einnahmen, Ausgaben und Überschuss je Monat, aufklappbar je Kategorie, mit Zeile Ø; Rücklagen-Topf mit den Kacheln "Monatlich zurücklegen" und "Sollte jetzt angespart sein" und einer Tabelle der seltenen Ausgaben; Vorschau aller Ausgaben über 12 Monate mit Balken je Monat, R14); *Transaktionen* zeigt zusätzlich eine Zeile mit dem monatlich zurückzulegenden und dem angesparten Betrag (Entscheidungen 15 und 16). Das Layout funktioniert ab ca. 360 px Breite: Kacheln untereinander, in Tabellen Beträge ohne Währungszeichen (Einheit im Spaltenkopf), unter 480 px ohne Spalte Soll; unter 600 px wird jede Transaktion als Karte gezeigt (Beschreibung und Betrag, darunter Datum und Kategorie, dann die Aktionen). Datenzugriff mit TanStack Query, API-Typen in `src/api/`, keine Fachlogik im Frontend (nur Anzeige und Formatierung, `Intl.NumberFormat('de-DE')`). Formulare mit einfacher Validierung (Betrag > 0, Pflichtfelder); der Umschalter "Ausgabe / Einnahme" setzt nur das Vorzeichen.

**Ausbau ab 27.10.2026:**
- **Backend:** Spring Security mit Session-Cookie und CSRF-Schutz (Abschnitt 15). Der angemeldete Nutzer kommt im Service aus dem Security-Kontext; jedes Repository fragt mit `owner` ab (R15), Controller reichen keine Nutzer-IDs aus der URL durch. Datenbank: PostgreSQL im Betrieb, H2 für Tests und lokale Entwicklung; Schema nur über Flyway (US11). Weitere reine Rechenklassen ohne Spring: `SplitCalculator` (Anteile nach R17), `BalanceCalculator` (Salden nach R18, nutzt `RecurrenceSchedule` für Vorkommen), `InvestmentProjection` (Prognose nach R19, R20). Die Ausschöpfung des Freistellungsauftrags (R21) ist eine Summe im Service. Auslieferung als ein Docker-Image, in dem Spring Boot das gebaute Frontend ausliefert (Abschnitt 15).
- **Frontend:** Anmeldeseite und Seite für das Annehmen einer Einladung; alle anderen Seiten nur angemeldet (bei 401 zurück zur Anmeldung). Reiter *Übersicht* · *Transaktionen* · *Planung* · *Auswertung* · *Teilen* · *Investment*, dazu ein Menü *Einstellungen* (Kategorien, Personen, Konto, für Admins Einladungen). Im Transaktionsformular öffnet "Teilen" die Aufteilung (US18); die Anteile zeigt das Formular über `/splits/preview`. Diagramme mit Recharts (US13, US20).

## 7. Teststrategie

- **Unit (Pflicht):** `DashboardCalculator` mit mindestens diesen Fällen:
  1. Nur Euro-Soll, keine Prozente
  2. Nur Prozent-Soll, Einnahmen > 0
  3. Euro und Prozent gleichzeitig → Euro gewinnt (R3)
  4. Nicht verplant = `E` − `P`, negativ bei Überplanung und dann mit Bezeichnung "Überplant"; Soll gesamt enthält es nicht (R4, R6)
  5. Einnahmen = 0 → keine Division durch null, Anteile leer (R8, R9)
  6. Vorkommen einer wiederkehrenden Transaktion zählt mit vollem Betrag im Monat des Vorkommens, in anderen Monaten nicht (R1)
  7. Überschuss negativ → Bezeichnung "Monatlicher Verlust" (R7)
  8. Soll ab M gilt nicht für M−1, aber für M und M+1; ein neuerer Eintrag ersetzt den älteren (R3, Auflösung in `TargetResolver`)
  9. Vorzeichen (R2, R9, R11): Erstattung mindert das Ist ihrer Kategorie; eine Kategorie mit Soll und positivem Netto zählt als Einnahme (Ist 0, Abweichung = Soll)
  10. Budget-Tabelle ohne Kategorien ohne Soll und ohne Ausgaben, in der Reihenfolge `sortOrder` (R10)
  11. Wiederkehrende Ausgaben (R10): Vorkommen mit negativem Betrag zählen zu `W`, einzelne Transaktionen und Vorkommen mit positivem Betrag nicht; eine Erstattung in derselben Kategorie mindert zuerst den einmaligen Teil; übersteigt sie die einmaligen Ausgaben der Kategorie, sinkt `W(k)` auf `I(k)`; `W` + einmalige Ausgaben = `A`
- **Unit:** `RecurrenceSchedule` (R12): je Intervall die richtigen Vorkommen; Tag 31 und 29.02. auf den Monatsletzten; Enddatum einschließlich; Ausnahmen werden ausgelassen; kein Vorkommen vor dem Startdatum.
- **Unit:** `ReserveCalculator` (R14): Ø Ausgaben, Ø Einnahmen und Ø Überschuss über 12 Monate, über weniger Monate ab dem ersten Eintrag und leer ohne abgeschlossenen Monat; Rückblick mit denselben Monaten, fehlende Monate mit 0; Topf nur für Intervall > 1, angespart vor, im und nach dem Fälligkeitsmonat, Ausnahme auf dem nächsten Vorkommen, beendete Serie; Vorschau über 12 Monate mit einzelnen Ausgaben, ohne Einnahmen.
- **Integration:** `@SpringBootTest` + MockMvc für Transaktion anlegen → Dashboard spiegelt es wider; Validierungsfehler (400, auch Betrag 0); Serie anlegen → Vorkommen in `/transactions` und `/dashboard`; R13 "Nur dieser Eintrag" (Ausnahme + Ersatz-Transaktion) und "Dieser und alle folgenden" (erstes Vorkommen: Serie geändert; späteres Vorkommen: Serie geteilt, frühere Monate unverändert); unbekanntes Vorkommen → 404.
- **Frontend:** 2 bis 3 Vitest/Testing-Library-Tests (Formular-Validierung mit Umschalter, Dashboard rendert Werte inkl. Umschalter € / % und "davon wiederkehrend / einmalig", Planung zeigt "Nicht verplant", Formular mit "Wiederholen", Dialog für Bearbeiten eines Vorkommens).
- **Referenzabgleich:** Eine Testfixture mit erfundenen Zahlen (Abschnitt 12).
- **Ausbau ab 27.10.2026:**
  - `ReserveCalculator` (R14): Zeitraum 3, 6, 12 und 24 Monate, jeder frühestens ab dem ersten Eintrag; Grenzen 1 und 120 (0 und 121 → 400); Fixture F1 bleibt mit Vorgabe 12 gültig; Anteile im Rückblick je Monat und Gesamt, leer bei Einnahmen 0; Gesamt leer ohne abgeschlossenen Monat.
  - `SplitCalculator` (R17): Beträge, gleichmäßig mit Rundungsrest bei mir, Prozent; Summe ≠ `G`, negativer Anteil, mein Anteil 0 → Fehler.
  - `BalanceCalculator` (R18): Fixture F2; Vorkommen nach heute zählen nicht; Ausnahmen; Einträge, die eine Person bezahlt hat; Ausgleich in beide Richtungen.
  - `InvestmentProjection` (R19, R20): Fixture F3 (wird mit AP 21 aus dem Investment-Blatt erstellt); Rendite 0 ergibt Startwert + Einzahlungen; Auszahlungen mindern den Wert.
  - **Integration Mandantentrennung (R15):** Für jeden Endpunkt sieht Nutzer B die Daten von Nutzer A nicht (404 in der URL, 400 als Verweis); ohne Anmeldung 401; ohne CSRF-Token 403.
  - **Integration Benutzer:** Einladung annehmen (einmalig, abgelaufen → 410), Anmeldung mit Sperre nach Fehlversuchen, Konto löschen entfernt alle Daten.
  - **Migrationen:** Die Tests laufen gegen das Schema aus Flyway, nicht gegen ein von Hibernate erzeugtes.

## 8. CI/CD und Git

- GitHub Actions bei Push und PR: Backend `mvn -B verify`; Frontend `npm ci`, `npm run lint`, `npm test`, `npm run build`.
- `main` ist geschützt (PR nötig, Checks `backend` und `frontend` grün, kein Force-Push, kein Löschen). Je Feature ein Branch `feat/<name>` und ein PR mit Kurzbeschreibung: Ziel, Spec-Bezug (z. B. US3), was die KI erzeugt hat, was ich geändert habe.
- Commits in kleinen Schritten, aussagekräftig (Conventional Commits).
- Ab AP 19: CI baut zusätzlich das Docker-Image; Dependabot hält Maven, npm und GitHub Actions aktuell. Ein Deployment aus der CI kommt erst, wenn die Plattform feststeht (Entscheidung 21).

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
| 8 | Tests ergänzen, Review der KI-Ergebnisse, README mit "Learnings und Entscheidungen" | 1,0 h | |
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
10. **Keine Datenmigration:** Daten gibt es nur in lokalen Entwicklungsdatenbanken mit erfundenen Werten. Nach dem Umbau wird `backend/data/` gelöscht und neu befüllt. *Ersetzt durch Entscheidung 20, sobald es echte Daten gibt.*
11. **Rest-Kategorie nicht buchbar:** "Sonstiges (berechneter Rest)" ist eine reine Rechengröße (R4): Sie zeigt, welcher Teil der Einnahmen nicht verplant ist. Buchungen darauf werden abgelehnt (400), damit Ausgaben immer in einer echten Kategorie landen. *Ersetzt durch Entscheidung 12.*

Geklärt am 25.10.2026:

12. **Nicht verplant statt Rest-Kategorie:** Zählte der Rest im Soll gesamt mit, war Soll gesamt immer `E` und die Differenz gesamt immer gleich dem Überschuss. Eine unvollständige Soll-Verteilung fiel so nicht auf. Jetzt ist Soll gesamt nur die Summe der Soll-Werte (R6), und "Nicht verplant" (R4) ist ein eigener Wert im Dashboard. Die drei Werte beantworten verschiedene Fragen: Nicht verplant (Wie viel der Einnahmen hat kein Budget?), Abweichung zum Soll (Halte ich mein Budget ein?) und Überschuss (Was bleibt übrig?); es gilt Überschuss = Nicht verplant + Abweichung. Die System-Kategorie, die Gruppe Rest und das Feld `system` entfallen; Ausgaben ohne Gruppe erscheinen als Gruppenzeile "Ohne Gruppe" (R10; *Gruppen ersetzt durch Entscheidung 17*). "Differenz" heißt in der UI jetzt "Abweichung zum Soll". Keine Datenmigration (Entscheidung 10).
13. **Einfacheres Dashboard:** Das Dashboard beantwortet drei Fragen: Bleibt Geld übrig? Wo bin ich über Budget? Woher kam das Geld? Deshalb: drei Kacheln (Einnahmen, Ausgaben, Überschuss), eine Budget-Tabelle mit Gruppen als ein- und ausklappbaren Zwischensummenzeilen (statt einer eigenen Gruppentabelle) und eine Einnahmen-Liste; leere Zeilen entfallen (R9, R10). Die Sparquote entfällt, weil sie nur das Übrige zählte, nicht das geplante Sparen; der Überschuss in Prozent zeigt denselben Wert. Die 50-30-20-Empfehlung entfällt. "Nicht verplant" ist eine Planungsgröße und steht auf der Seite *Planung* (R4). Ein Umschalter € / % zeigt alle Werte als Anteil an den Einnahmen, z. B. zum Herzeigen ohne Beträge. Die Anteile rechnet das Backend (R8), damit die Regel an einer Stelle liegt und eine spätere Mobile-App sie nicht nachbauen muss. Das Layout ist schon jetzt für schmale Bildschirme ausgelegt. *Die Gruppen in der Budget-Tabelle ersetzt Entscheidung 17.*

Geklärt am 25.10.2026 (Navigation):

14. **Eine Transaktion ist eine Transaktion:** Für den Nutzer ist es egal, ob eine Transaktion einmalig oder wiederkehrend ist. Deshalb gibt es nur ein Erfassungsformular mit der Option "Wiederholen"; der Reiter *Wiederkehrend* entfällt. Eine eigene Serienliste gibt es nicht, weil sie neben der Monatsliste mehr verwirrt als hilft: Eine Serie wird über ihre Vorkommen geändert ("Dieser und alle folgenden") oder beendet (Löschen "Dieser und alle folgenden" setzt das Enddatum). Den monatlichen Durchschnitt der Serien zeigt die Auswertung (R14). Die getrennten APIs (`/transactions`, `/recurring-transactions`) bleiben, das Formular wählt je nach "Wiederholen" den Endpunkt. Eine bestehende einmalige Transaktion wird beim Bearbeiten nicht in eine Serie umgewandelt. Die Reiter heißen *Übersicht* (statt *Dashboard*, deutsche UI) und *Planung* (statt *Einstellungen*, denn dort wird das Soll geplant; der Name *Einstellungen* bleibt frei für spätere App-Einstellungen wie Kategorien, US10).

Geklärt am 26.10.2026:

15. **Auswertung als eigener Reiter, Kurzfassung bei den Transaktionen:** Die Auswertung (R14, US13) steht im Reiter *Auswertung* mit Ø Ausgaben, Rücklagen-Topf und Vorschau über 12 Monate (Entscheidung 16). Weil man die wiederkehrenden Transaktionen auf der Seite *Transaktionen* pflegt, zeigt sie zusätzlich eine Zeile mit dem monatlich zurückzulegenden und dem angesparten Betrag; so sieht man sofort, wie sich eine neue oder geänderte Serie auswirkt. Die Zeile entfällt, wenn der Topf leer ist. Die Länge der Balken in der Vorschau (Summe des Monats im Verhältnis zum teuersten Monat) ist reine Anzeige.
16. **Topf statt Ø Serienkosten** (26.10.2026, nach dem ersten Test): "Ø monatliche Kosten" und "davon Rücklage" waren schwer zu verstehen, weil sie nur Serien zählten und zwei ineinander verschachtelte Größen waren. Jetzt beantwortet die Auswertung drei einfache Fragen: Wie viel gebe ich im Schnitt aus? (Ø der echten Ausgaben `A` der letzten 12 abgeschlossenen Monate, wie im Dashboard; ein laufender Monat würde den Schnitt drücken.) Wie viel lege ich monatlich zurück, und wie viel sollte jetzt im Topf sein? (Rücklagen-Topf je seltener Ausgabe, im Fälligkeitsmonat voll.) Was wird wann abgebucht? (Vorschau mit allen gebuchten Ausgaben, auch einzelnen, die schon für später eingetragen sind.) Gibt es noch keine 12 Monate mit Einträgen, wird über die vorhandenen Monate gemittelt und deren Anzahl angezeigt, damit leere Monate vor dem ersten Eintrag den Schnitt nicht drücken.

Geklärt am 27.10.2026:

17. **Keine Gruppen, Fixkosten = wiederkehrende Transaktionen:** Ob eine Ausgabe fix oder flexibel ist, hängt an der Zahlung, nicht an der Kategorie: In Mobilität ist die Kfz-Versicherung fix, das Tanken flexibel. Eine feste Gruppe je Kategorie bildet das nicht ab. Die Information steckt schon in den Daten: Was regelmäßig abgebucht wird, ist eine wiederkehrende Transaktion (R12). Deshalb entfallen die Gruppen (Fixkosten, Flexibel, Sparen, Ohne Gruppe) samt Zwischensummen, Ein- und Ausklappen und der Sperre für ein Soll bei Kategorien ohne Gruppe (409). Jede Kategorie kann ein Soll haben (R3). Das Dashboard teilt die Ausgaben stattdessen in wiederkehrend und einmalig (R10). Ein Sparplan zählt als wiederkehrende Ausgabe; Sparen und Investieren sind normale Kategorien, ein eigenes Merkmal dafür gibt es nicht. "Wiederkehrend" ist bewusst eine einfache Näherung für Fixkosten: Schwankende Beträge (z. B. eine Handyrechnung) werden je Vorkommen angepasst (R13). Abgrenzung zur Auswertung (R14, Entscheidung 16): "davon wiederkehrend" im Dashboard zählt alle Vorkommen mit negativem Betrag im Monat M, netto nach Erstattungen; der Rücklagen-Topf betrifft nur seltene Serien (Intervall > 1) und schaut nach vorn. Keine Datenmigration (Entscheidung 10).

Geklärt am 27.10.2026 (reale Nutzung):

18. **Reale Nutzung mit mehreren Nutzern:** Die App wird zuerst privat und im Bekanntenkreis genutzt, später eventuell öffentlich. Alle Nutzer teilen sich eine Datenbank; jede Entität hat einen `owner`, und jede Abfrage filtert darauf (R15). Ein Schema oder eine Datenbank je Nutzer wäre für wenige Nutzer zu aufwendig. Fremde Ressourcen ergeben 404, nicht 403, damit nicht erkennbar ist, ob es sie gibt. Das Repo bleibt öffentlich (Entscheidung 4); echte Daten liegen nur in der Datenbank des Betriebs.
19. **Anmeldung mit E-Mail und Passwort, Einladung statt Registrierung:** Eine eigene Benutzerverwaltung mit Spring Security und Session-Cookie ist für den Bekanntenkreis einfacher als ein externer Anbieter (OIDC) und braucht keinen weiteren Dienst. Neue Nutzer kommen nur über einen Einladungslink, den der Admin selbst verschickt; so braucht die App keinen Mailversand. Offene Registrierung, E-Mail-Bestätigung und Passwort-Reset per Mail kommen erst mit dem öffentlichen Betrieb.
20. **Flyway und PostgreSQL ab dem ersten Deployment:** Sobald echte Daten existieren, darf ein Umbau sie nicht löschen. Deshalb entsteht das Schema nur aus Flyway-Migrationen (US11), und eine gemergte Migration wird nie geändert. Im Betrieb läuft PostgreSQL (robuster als eine H2-Datei, Backups mit Standardwerkzeugen), für Tests und lokale Entwicklung bleibt H2. Ersetzt Entscheidung 10.
21. **Docker als Auslieferung, Plattform offen:** Die App wird als ein Docker-Image mit `docker-compose.yml` (App und PostgreSQL) ausgeliefert, damit sie auf jedem Server mit Docker läuft. Wo sie läuft (eigener Server, Hoster, zu Hause), wird später entschieden; Abschnitt 15 hält die Anforderungen fest, die jede Plattform erfüllen muss.
22. **Teilen: Im Budget zählt nur mein Anteil:** Zahle ich 500 € und mein Anteil ist 200 €, zählen 200 € in meiner Kategorie; die 300 € sind eine Forderung an die andere Person, kein Konsum. Würde der volle Betrag zählen und die Rückzahlung als Erstattung, wären der Monat der Zahlung zu teuer und der Monat der Rückzahlung zu günstig. Der Betrag einer Transaktion ist deshalb mein Anteil (R16), Gesamtbetrag und Anteile der anderen stehen in der Aufteilung; R1 bis R14 und ihre Rechenklassen bleiben unverändert. Ausgleichszahlungen zählen nicht im Budget. Damit gilt Entscheidung 6 ("Ist = echter Geldfluss") für geteilte Ausgaben nicht mehr wörtlich: Das Ist ist der eigene Anteil, der Kontoauszug weicht um Auslagen und Ausgleichszahlungen ab. Wiederkehrende Ausgaben (z. B. Miete) lassen sich genauso teilen. Teilen mit verknüpften Nutzern (US24) kommt später, weil jeder Nutzer eigene Kategorien hat und eine geteilte Ausgabe beim anderen erst einer Kategorie zugeordnet werden muss.
23. **Verlauf als Erweiterung der Auswertung:** Der Rückblick der Auswertung (R14) zeigt Einnahmen und Ausgaben schon je Monat. Statt einer zweiten Auswertung wird sein Zeitraum wählbar (3, 6, 12 oder 24 Monate) und ein Diagramm kommt dazu. So gibt es nur eine Rechenlogik, und die Zahlen stimmen mit der Übersicht überein. Die API nimmt jeden Zeitraum von 1 bis 120 Monaten an (31.10.2026), weil die Regel für jeden Zeitraum gleich ist; die feste Auswahl ist reine Anzeige und kann wachsen, ohne das Backend zu ändern. Die Obergrenze verhindert, dass ein einzelner Aufruf beliebig viele Monate berechnet.
24. **Investment in vier Stufen, Sparplan = wiederkehrende Transaktion:** Der Investment-Teil kommt in vier Arbeitspaketen (Sparplan und Prognose, Einmalzahlungen, Freistellungsauftrag, Depot mit Positionen), jeweils erst nach der Analyse der Formeln des passenden Sheet-Blatts. Ein Sparplan ist eine wiederkehrende Transaktion, die mit einem Depot verknüpft ist (passt zu Entscheidung 17); so wird er nur einmal erfasst und zählt im Budget wie jede andere Ausgabe. Die App gibt keine Renditen vor und keine Anlageempfehlung; Pauschbeträge sind Einstellungen, weil sich die Rechtslage ändern kann.
25. **Auswertung kompakter und in Prozent:** Der Rückblick bekommt den Umschalter € / % aus dem Dashboard, bezogen auf die Einnahmen des jeweiligen Monats; die Anteile rechnet das Backend (wie R8). Rücklagen und Vorschau bleiben in €, weil ihnen eine Einnahmen-Basis fehlt. Eine Zeile Gesamt zeigt die Summen des Zeitraums. Rückblick und Vorschau sind einklappbar, damit die lange Seite übersichtlich bleibt; der Zustand gilt je Browser.

## 12. Testfixtures F1, F2 und Sonderfall Einnahmen = 0

Alle Zahlen sind erfunden und durchgerechnet. Sie dienen als End-to-End-Test für `DashboardCalculator` und `ReserveCalculator` (ergänzend zu den Fällen aus Abschnitt 7).

**Monat:** 2026-10 · **Einnahmen:** Gehalt/Lohn (netto) +3000,00 · Nebenjob +200,00 → `E` = 3200,00

**Soll-Einstellungen (gültig ab 2026-10, R3):**

| Kategorie | Einstellung |
|---|---|
| Wohnen | 900,00 € |
| Nebenkosten | 10 % |
| Versicherungen | 100,00 € |
| Mobilität | 60,00 € **und** 50 % (Euro gewinnt, R3) |
| Lebensmittel | 400,00 € |
| Freizeit | 5 % |
| Investitionen | 20 % |

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

Kredite (kein Soll, keine Ausgaben) erscheint nicht in der Tabelle (R10).

- Nicht verplant (R4): 3200,00 − 2580,00 = 620,00
- Überschuss: 593,51 ("Monatlicher Überschuss") = 620,00 + (−26,49) · Anteil (R8): 593,51 / 3200,00 = 0,1855
- Anteile (R8): Ausgaben gesamt Soll 0,8063 · Ist 0,8145 · Abweichung −0,0083; Wohnen Soll und Ist 0,2813
- Einnahmenanteile: Gehalt/Lohn (netto) 0,9375 · Nebenjob 0,0625
- Wiederkehrende Ausgaben (R10): Wohnen 900,00 · Versicherungen 240,00 · Freizeit 0,00 (Vorkommen ausgesetzt) → `W` = 1140,00 · einmalige Ausgaben 2606,49 − 1140,00 = 1466,49 · Anteile (R8): 0,3563 und 0,4583. Die einzelnen Ausgaben in der Vorschau der Auswertung (1496,49) sind um die Erstattung Lebensmittel (+30,00) höher, weil die Vorschau nur Ausgaben zählt und nicht saldiert (R14).
- Lebensmittel: Netto −420,50, die Erstattung zählt nicht als Einnahme (R2)
- Kategorien ohne Soll mit Ausgaben (Geschenke): Soll und Abweichung sind `null`, Ist ist ihr Ausgaben-Ist (20,00). Deshalb ergibt die Summe der Abweichungen je Kategorie (−6,49) nicht die Abweichung gesamt (−26,49); maßgeblich ist R6.

**Erwartete Ergebnisse Auswertung (R14):**
- Ø Ausgaben: erster Eintrag ist die Serie Wohnen ab 2026-01, also `n` = 9 (2026-01 bis 2026-09). Je Monat Wohnen 900,00 + Freizeit 15,00 = 915,00, im September zusätzlich Lebensmittel 999,00: (9 × 915,00 + 999,00) / 9 = 1026,00
- Rückblick: 2026-01 bis 2026-08 je Einnahmen 0,00, Ausgaben 915,00 (Wohnen 900,00, Freizeit 15,00), Überschuss −915,00; 2026-09 Ausgaben 1914,00 (zusätzlich Lebensmittel 999,00), Überschuss −1914,00 · Ø Einnahmen 0,00, Ø Überschuss −1026,00 · Gesamt Einnahmen 0,00, Ausgaben 9234,00, Überschuss −9234,00 · alle Anteile leer (`E` = 0)
- Rücklagen-Topf: nur Versicherungen (jährlich), nächstes Vorkommen 2026-10-15, `k` = 0: pro Monat 240,00 / 12 = 20,00, angespart 240,00 · Monatlich zurücklegen 20,00 · Sollte jetzt angespart sein 240,00. Im November 2026 (nächstes Vorkommen 2027-10-15, `k` = 11): angespart 20,00
- Vorschau: 2026-10: 2636,49 (Wohnen 900,00, Versicherungen 240,00, Freizeit ausgesetzt; einzelne Ausgaben 1496,49) · 2026-11 bis 2027-09: je 915,00

**Rundung:** Geld mit Skala 2 (HALF_UP), Prozentwerte und Anteile mit Skala 4; nur im Endergebnis runden, nicht in Zwischensummen.

**Sonderfall Einnahmen = 0 (Entscheidung):** Prozent-Soll ergibt 0,00; Euro-Soll bleibt erhalten; Nicht verplant wird negativ (0 minus Summe der Euro-Soll). Überschuss = 0 minus Ausgaben ("Monatlicher Verlust"); alle Anteile (R8) sind leer, die Einnahmen-Liste (R9) ist leer. Keine Division durch null, keine Exception. Das Sheet gibt hier Fehlerwerte aus, die App fängt den Fall sauber ab.

### Testfixture F2: Teilen (R16 bis R18)

Alle Zahlen und Personen sind erfunden. **Heute:** 2026-10-20 · **Personen:** Person A, Person B

| Eintrag | Kategorie | Datum | Gesamt | Bezahlt von | Aufteilung | Mein Anteil (Betrag) | Wirkung auf Saldo |
|---|---|---|---|---|---|---|---|
| Einkauf | Lebensmittel | 2026-10-03 | 500,00 | ich | Beträge: ich 200,00, A 300,00 | −200,00 | A +300,00 |
| Restaurant | Freizeit | 2026-10-08 | 90,00 | A | gleichmäßig: ich, A, B je 30,00 | −30,00 | A −30,00 (B geht nur A etwas an) |
| Kino | Freizeit | 2026-10-10 | 100,00 | ich | gleichmäßig: A 33,33, B 33,33, ich 100,00 − 66,66 = 33,34 | −33,34 | A +33,33, B +33,33 |
| Miete (Serie, monatlich ab 2026-09-01) | Wohnen | je 1. | 1200,00 | ich | gleichmäßig: ich, A je 600,00 | −600,00 je Vorkommen | A +600,00 je Vorkommen bis heute: 2026-09-01 und 2026-10-01 → +1200,00 |
| Ausgleich | – | 2026-10-15 | – | A zahlt mir | Betrag +1000,00 | zählt nicht im Budget | A −1000,00 |

**Erwartete Ergebnisse:**
- Saldo A = 300,00 − 30,00 + 33,33 + 1200,00 − 1000,00 = **503,33** ("schuldet dir")
- Saldo B = **33,33** ("schuldet dir")
- Das Vorkommen der Miete am 2026-11-01 zählt erst ab diesem Tag (Saldo A dann 1103,33).
- Budget Oktober (R1): Lebensmittel −200,00 · Freizeit −63,34 · Wohnen −600,00; der Ausgleich erscheint nicht. Budget September: Wohnen −600,00.
- Validierung: Kino mit Prozent A 50 %, B 60 % → 400 (Summe ≠ 100 %); eine geteilte Einnahme → 400.

## 13. Ausbau ab 23.10.2026

Umsetzung der Entscheidungen 6 bis 17. Jedes Arbeitspaket ist ein eigener Branch und PR; Berechnungen mit Tests zuerst.

| AP | Inhalt | Ergebnis |
|---|---|---|
| 9 | Spezifikation und `CLAUDE.md` anpassen (dieser Stand) | Spec-PR |
| 10 | Vorzeichen statt Kategorietyp (R2, R4 bis R11, US1 bis US3): Datenmodell, Seed, Validierung inkl. nicht buchbarer Rest-Kategorie, `DashboardCalculator`, Formular-Umschalter; `YearlyTransaction` entfernen (vorgezogen am 25.10.2026, weil ihre Beträge am Kategorietyp hingen) | Fixture F1 ohne wiederkehrende Transaktionen grün |
| 11 | Wiederkehrende Transaktionen (R12, R13, US6, US6a): `RecurrenceSchedule`, Serien-API, Teilen und Ausnahmen, Vorkommen in `/transactions` und `/dashboard`, Seiten *Transaktionen* und *Wiederkehrend* | Fixture F1 Dashboard grün |
| 12 | Auswertung (R14, US13, Branch `feat/evaluation`): `ReserveCalculator`, `/reserves`, Seite *Auswertung*. Idee vom 25.10.2026, im Auswertungs-Branch zu klären: die monatlich nötigen Rücklagen (Ø Kosten, davon Rücklage) zusätzlich oder stattdessen kompakt auf der Seite *Transaktionen* zeigen, weil man dort die wiederkehrenden Transaktionen pflegt | Fixture F1 Auswertung grün |
| 12a | Nicht verplant statt Rest-Kategorie (Entscheidung 12, R4 bis R7, R10): Rest-Kategorie, Gruppe REMAINDER und `system` entfernen, `DashboardCalculator`, Dashboard-Bezeichnungen | Fixture F1 grün |
| 12b | Einfacheres Dashboard (Entscheidung 13, R4, R8 bis R10, US5, US7, US7a): Anteile im `DashboardCalculator`, Budget-Tabelle mit Gruppen, Umschalter € / %, "Nicht verplant" auf der Seite *Planung*, Layout für schmale Bildschirme | Fixture F1 grün |
| 12c | Navigation (Entscheidung 14, US6): Formular mit "Wiederholen", keine eigene Serienliste, Transaktionen als Karten auf schmalen Bildschirmen, Reiter *Übersicht* · *Transaktionen* · *Planung* | Frontend-Tests grün |
| 12d | Keine Gruppen (Entscheidung 17, R3, R10, US1, US2, US5, US7): Feld `group` und 409 entfernen, Seed als flache Liste, `DashboardCalculator` ohne Gruppen und mit wiederkehrenden / einmaligen Ausgaben (Tests zuerst), Budget-Tabelle ohne Zwischensummen, Kachel Ausgaben mit "davon wiederkehrend / einmalig" | Fixture F1 grün |
| 13 | README "Learnings und Entscheidungen" (AP 8) | README |

**Stand 27.10.2026:** AP 9 bis 12d erledigt. Offen: AP 13.

Getrennt davon und ohne fachliche Änderung: Upgrade auf Spring Boot 4 (erledigt am 01.11.2026, vor AP 18, damit die Security-Konfiguration gleich für Spring Security 7 entsteht).

## 14. Ausbau ab 27.10.2026: reale Nutzung

Umsetzung der Entscheidungen 18 bis 24, nach AP 12d und 13. Das Benutzersystem (AP 18) kommt vor allen neuen Entitäten, damit `owner` nicht nachgerüstet werden muss. Jedes Arbeitspaket ist ein eigener Branch und PR; Berechnungen mit Tests zuerst.

| AP | Inhalt | Ergebnis |
|---|---|---|
| 14 | Spezifikation und `CLAUDE.md` anpassen (dieser Stand) | Spec-PR |
| 15 | Kategorien verwalten (US10): API, Seite *Einstellungen › Kategorien*, `archived` | Integrationstests grün |
| 15a | Auswertung: Umschalter € / % und Gesamt-Zeile im Rückblick, Rückblick und Vorschau einklappbar (R14, US13, Entscheidung 25) | Fixture F1 grün, Tests für Anteile und Summen |
| 16 | Auswertung mit wählbarem Zeitraum und Diagramm (R14, US13, Entscheidung 23) | Fixture F1 grün, Tests für 3/6/24 Monate |
| 17 | Flyway und PostgreSQL-Profil (US11, Entscheidung 20): Ausgangsmigration aus dem aktuellen Schema; der Seeder bleibt bis AP 18 und wird dort zum Seed je neuem Nutzer (US15), weil es vorher keinen Nutzer gibt, an dem er hängen könnte | Tests auf Flyway-Schema grün, mit H2 und in der CI mit PostgreSQL |
| 18 | Benutzersystem und Mandantentrennung (R15, US14 bis US16, Entscheidungen 18 und 19): Spring Security, `User`, `Invitation`, `owner` an allen Entitäten, Seed je Nutzer, Anmeldeseite, Konto | Integrationstests R15 grün |
| 19 | Betrieb (Abschnitt 15, Entscheidung 21): Dockerfile, `docker-compose.yml`, `.env.example`, Health-Check, Backup-Skript, Impressum und Datenschutzhinweis als Platzhalter | Image baut in der CI |
| 20 | Teilen Stufe 1 (R16 bis R18, US17 bis US19, Entscheidung 22): `SplitCalculator`, `BalanceCalculator`, Personen, Aufteilung für Transaktionen und Serien, Seite *Teilen* | Fixture F2 grün |
| 21 | Investment I1: Sparplan und Prognose (R19, US20); vorher Formeln des Blatts "Sparplan"/"Prognosen" analysieren und R19 präzisieren | Fixture F3 grün |
| 22 | Investment I2: Einmalzahlungen (R20, US21) | Tests grün |
| 23 | Investment I3: Freistellungsauftrag (R21, US22); vorher Blatt "Freistellungsauftrag" analysieren | Tests grün |
| 24 | Teilen Stufe 2 mit verknüpften Nutzern (US24): eigene Spec-Runde | Spec-PR, dann Umsetzung |
| 25 | Investment I4: Depot mit Positionen (R22, US23): eigene Spec-Runde (Kursquelle) | Spec-PR, dann Umsetzung |

**Stand 01.11.2026:** AP 14, 15, 15a, 16 und 17 erledigt. AP 18 in zwei PRs: 18a (Spring Security, `User`, `owner` an allen Entitäten, Seed je Nutzer, Anmeldeseite) erledigt; 18b (Einladung US15, Konto US16) offen.

**Erste Nutzung im Bekanntenkreis:** nach AP 19. AP 15 und 16 können davor oder parallel laufen, AP 20 bis 25 kommen danach.

## 15. Betrieb, Sicherheit und Datenschutz (nicht-funktionale Anforderungen)

**Auslieferung (Entscheidung 21):**
- Ein Docker-Image: mehrstufiger Build (Frontend bauen, Backend bauen), Spring Boot liefert das Frontend aus, läuft als Nicht-root-Nutzer.
- `docker-compose.yml` mit App und PostgreSQL; die Datenbank liegt in einem benannten Volume.
- Konfiguration und Secrets (Datenbank-Passwort, erster Admin) nur über Umgebungsvariablen; im Repo nur `.env.example` mit Platzhaltern.
- Health-Check über Spring Boot Actuator (`/actuator/health`, nur Status nach außen).

**Betrieb (jede Plattform muss das erfüllen):**
- Nur HTTPS (z. B. über einen Reverse Proxy mit automatischen Zertifikaten); HTTP leitet um.
- Tägliches Backup der Datenbank (`pg_dump`), mindestens 7 Tage aufbewahrt, nicht nur auf demselben Server; die Wiederherstellung ist einmal getestet und beschrieben.
- Updates: neues Image bauen, Migrationen laufen beim Start (Flyway); vor einem Update ein Backup.
- Logs ohne Beträge, Beschreibungen, Passwörter und Tokens.
- Datenstandort in der EU.

**Sicherheit:**
- Passwörter nur als Hash (BCrypt oder Argon2), mindestens 12 Zeichen.
- Session-Cookie `HttpOnly`, `Secure`, `SameSite=Lax`; CSRF-Schutz für schreibende Aufrufe; Ablauf nach 30 Tagen ohne Nutzung.
- Sperre nach 5 fehlgeschlagenen Anmeldungen in 15 Minuten (US14).
- Mandantentrennung nach R15, durch Integrationstests für jeden Endpunkt abgesichert.
- Security-Header (Content-Security-Policy, `X-Content-Type-Options`, `Referrer-Policy`).
- Abhängigkeiten aktuell halten (Dependabot, Abschnitt 8).

**Datenschutz:**
- Konto löschen und Daten exportieren (US16).
- Vor dem öffentlichen Betrieb: Impressum, Datenschutzerklärung, Vertrag zur Auftragsverarbeitung mit dem Hoster, offene Registrierung mit E-Mail-Bestätigung (Entscheidung 19).

## 16. Offene Punkte

- Plattform für den Betrieb (Entscheidung 21).
- Konkrete Formeln der Investment-Blätter (vor AP 21 und 23 aus dem Sheet ableiten); Fixture F3.
- Teilen mit verknüpften Nutzern (US24) und Depot mit Positionen (US23, Kursquelle): eigene Spec-Runden.
