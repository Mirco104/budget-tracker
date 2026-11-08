# Spezifikation: Budget-Tracker (Web-App)

Stand: 04.11.2026 · Basis: Google Sheet "Budget + Investment Planer V1.1.0" · Stack: Java + Spring Boot (REST) und TypeScript + React · MVP: ca. 8 h Arbeitszeit, fertig bis Di 20.10.2026 · Ausbau ab 23.10.2026 siehe Abschnitt 13 · Reale Nutzung mit mehreren Nutzern ab 27.10.2026 siehe Abschnitte 14 bis 16 · Überarbeitung von Bedienung und Design ab 04.11.2026 siehe Abschnitt 17

## 1. Ziel und Scope

**Ziel:** Eine schlanke Web-App, die das Budget-Blatt des Sheets abbildet: Soll-Verteilung festlegen, Ausgaben und Einnahmen erfassen, Dashboard mit Soll/Ist/Abweichung. Das Projekt zeigt vor allem den Prozess: Spezifikation → Plan → Umsetzung mit Claude Code → Review → Tests → CI.

**Ziel ab 27.10.2026 (Entscheidung 18):** Die App wird real genutzt, zuerst privat und im Bekanntenkreis, später eventuell öffentlich. Dafür kommen ein Benutzersystem, der Betrieb (Abschnitt 15), die Verwaltung von Kategorien, das Teilen von Ausgaben und der Investment-Teil dazu (Abschnitt 14).

**Im MVP (drin):**
- Kategorien mit Soll-Werten in Euro (eingeben lassen sie sich auch in Prozent der Einnahmen, Entscheidung 36)
- Transaktionen erfassen (Einnahmen positiv, Ausgaben negativ, mit Datum)
- Wiederkehrende Transaktionen (monatlich, vierteljährlich, halbjährlich, jährlich), die im Monat ihrer Abbuchung mit vollem Betrag zählen
- Dashboard pro Monat: Einnahmen, Ausgaben (davon wiederkehrend und einmalig), Überschuss; Budget-Tabelle mit Soll, Ist und Abweichung zum Soll je Kategorie (der frühere Umschalter € / % entfällt seit Entscheidung 34)
- Die Seite *Planung* zeigt, welcher Teil der Einnahmen nicht verplant ist
- Bedienbar auch auf schmalen Bildschirmen (ab ca. 360 px Breite)
- Auswertung: durchschnittliche Ausgaben der letzten 12 Monate, Rücklagen-Topf für seltene wiederkehrende Ausgaben und Vorschau der Ausgaben der nächsten 12 Monate

**Ausbau (Abschnitt 14):**
- Kategorien anlegen, umbenennen, sortieren, archivieren und löschen (US10)
- Auswertung über einen wählbaren Zeitraum mit Diagramm (R14, US13)
- Benutzersystem mit Einladung und getrennten Daten je Nutzer (R15, US14 bis US16)
- Ausgaben teilen wie in Splitwise, zuerst ohne, später mit Verknüpfung zu anderen Nutzern (R16 bis R18, US17 bis US19)
- Investment: Sparplan und Prognose, Einmalzahlungen, Freistellungsauftrag, Depot mit Positionen (R19 bis R22, US20 bis US23)

**Überarbeitung (Abschnitt 17), vor dem Teilen und dem Investment-Teil:**
- Einheitliches Design mit klarer Navigation; jede Seite hat die Zeitsteuerung, die zu ihr passt
- "Nicht verplant" und die Eingabe eines Solls in Prozent beziehen sich wahlweise auf die durchschnittlichen oder die Mindesteinnahmen der letzten 12 Monate (R23, Entscheidung 36); die *Planung* zeigt je Kategorie die fixen Kosten aus Serien (R25)
- Übersicht mit "Noch frei" und Fortschritt je Kategorie, Buchungsdialog, Transaktionen mit Zeitraumfilter, Auswertung mit Rückblick von Monat bis Monat und Vorausschau, Icons für Kategorien

**Bank-Import (Abschnitt 18), nach der Überarbeitung:**
- Bankkonten, Umbuchungen zwischen eigenen Konten (R26, R27)
- Kategorie-Vorschlag aus den bisherigen Buchungen, auch im Buchungsdialog (R29)
- CSV-Import von C24, Trade Republic, N26 und der Sparkasse mit Vorschau, Duplikat-Erkennung und Rückgängig (R30); eine Bankbuchung ersetzt das geplante Vorkommen einer Serie (R28)

**Bewusst vorerst draußen:**
- Mehrere Währungen, direkte Anbindung an Banken (PSD2, FinTS; der CSV-Import aus Abschnitt 18 ist dagegen drin), eigene Mobile-App (langfristig geplant; das Web-Layout ist schon jetzt für schmale Bildschirme ausgelegt)

## 2. Fachliche Regeln (aus den Formeln des Sheets abgeleitet)

Sei `M` der gewählte Monat und `N(k)` das Netto der Kategorie `k` in M (R1).

| # | Regel | Quelle im Sheet |
|---|---|---|
| R1 | **Netto je Kategorie** `N(k)` = Summe der Beträge (mit Vorzeichen, R11) aller Transaktionen der Kategorie in M + Summe der Beträge aller Vorkommen wiederkehrender Transaktionen der Kategorie, die in M liegen (R12). Ein Vorkommen zählt mit seinem vollen Betrag; das Ist bildet den echten Geldfluss ab. | `Dashboard_Budget!E39:E59`, `K39:K46`; bewusste Abweichung: keine Verteilung von Jahresbeträgen auf 12 Monate |
| R2 | **Einnahmen und Ausgaben:** Eine Kategorie mit `N(k) > 0` zählt in M als Einnahme, eine mit `N(k) < 0` als Ausgabe. **Einnahmen gesamt (Ist)** `E` = Summe aller positiven `N(k)`. **Ausgaben-Ist je Kategorie** `I(k)` = `−N(k)`, wenn `N(k) < 0`, sonst 0. **Ausgaben gesamt (Ist)** `A` = Summe aller `I(k)`. Erstattungen (positive Beträge in einer Ausgaben-Kategorie) mindern so das Ist ihrer Kategorie. | `K37` |
| R3 | **Soll je Kategorie:** Jede Kategorie kann ein Soll haben. Maßgeblich ist der Soll-Eintrag der Kategorie mit dem spätesten `validFrom ≤ M` (gilt ab M für alle folgenden Monate, bis ein neuerer Eintrag ihn ersetzt). Gibt es keinen solchen Eintrag oder ist sein Betrag leer: kein Soll. Sonst ist das Soll der Euro-Betrag des Eintrags. Ein Soll ist immer ein Ausgabenbudget (Betrag ≥ 0). Seit Entscheidung 36 speichert die App nur Euro-Beträge; Prozent ist eine Eingabehilfe auf der *Planung*, die das Frontend vor dem Speichern in Euro umrechnet (US2). Vorher gab es auch ein Prozent-Soll (`Ē(M)` × Prozent, Euro mit Vorrang, Entscheidung 26). | `Einstellungen_Budget!F11:F30`; bewusste Abweichung: im Sheet auch in Prozent |
| R4 | **Nicht verplant** = `B(M)` − `P`, wobei `B(M)` die gewählte Basis ist, die Ø Einnahmen `Ē(M)` oder die Mindesteinnahmen `Emin(M)` vor M (beide R23), und `P` die Summe aller Soll-Werte (R3) (seit Entscheidung 36; vorher nur `Ē(M)` − `P`, Entscheidung 26, davor `E` − `P`). Das Backend liefert den Wert für beide Basen, die *Planung* zeigt den der gewählten (US7a). Zeigt, welcher Teil der üblichen bzw. sicheren Einnahmen kein Budget hat. Darf negativ sein (Überplanung). Bezeichnung: "Nicht verplant" bei ≥ 0, sonst "Überplant"; bei "Überplant" wird der Betrag ohne Minuszeichen in der Warnfarbe angezeigt. Eine reine Rechengröße, keine Kategorie. Angezeigt auf der Seite *Planung* für den Monat "Gilt ab" (Ø Einnahmen und Mindesteinnahmen mit ihrem Zeitraum, verplant, nicht verplant); ist `Ē(M)` leer (dann auch `Emin(M)`), steht dort statt der Werte ein Hinweis. | `F31`, `G31`; bewusste Abweichung: im Sheet eine berechnete Rest-Kategorie, die im Soll gesamt mitzählte |
| R5 | **Abweichung zum Soll je Kategorie** = Soll − `I(k)`. Positiv heißt unter Budget, negativ über Budget. Ohne Soll: leer. | `F39:F59` |
| R6 | **Gesamt:** Soll gesamt = `P` (ohne nicht verplante Einnahmen); Ist gesamt = `A`; Abweichung zum Soll gesamt = `P` − `A` | `D37:F37`; bewusste Abweichung: im Sheet war Soll gesamt immer `E` und die Differenz damit gleich dem Überschuss |
| R7 | **Überschuss** = `E` − `A` (gleich der Summe aller `N(k)`). Bezeichnung für einen Monat vor dem aktuellen Monat: "Monatlicher Überschuss" bei ≥ 0, sonst "Monatlicher Verlust". Für den aktuellen und alle späteren Monate: "Noch frei" bei ≥ 0, sonst "Überschritten" (Betrag mit Minuszeichen in der Warnfarbe, seit Entscheidung 34); das ist, was nach allen bisher gebuchten Einträgen und allen Vorkommen des Monats übrig ist. Der aktuelle Monat ist der Monat von heute (Datum wie in R18). Seit Entscheidung 26 ist der Überschuss nicht mehr gleich Nicht verplant + Abweichung zum Soll gesamt, weil Nicht verplant auf `Ē(M)` beruht. | `I17`, `I18` |
| R8 | *Entfallen (Entscheidung 34).* Früher lieferte das Dashboard jeden Betrag zusätzlich als Anteil an `E` für einen Umschalter € / %. Alle Beträge werden nur noch in Euro geliefert und angezeigt. | bewusste Abweichung vom Sheet (`I15`, `I16`: Sparquote als Einnahmen / Ausgaben − 1) |
| R9 | **Einnahmen-Liste:** zeigt jede Kategorie mit `N(k) > 0`, mit `N(k)`. | `L39:L46` |
| R10 | **Budget-Tabelle und wiederkehrende Ausgaben:** Kategorien haben keine Gruppen. Die Budget-Tabelle zeigt jede Kategorie, die ein Soll oder ein `I(k) > 0` hat, alphabetisch (Entscheidung 38); Kategorien ohne Soll und ohne Ausgaben entfallen. Es gibt keine Zwischensummen, nur die Gesamtzeile (R6). Fixkosten sind keine Eigenschaft einer Kategorie, sondern einer Transaktion: Was regelmäßig abgebucht wird, ist eine wiederkehrende Transaktion (R12). Sei `V(k)` die Summe von \|Betrag\| aller Vorkommen wiederkehrender Transaktionen der Kategorie mit negativem Betrag, die in M liegen (Ausnahmen beachtet). **Wiederkehrendes Ist je Kategorie** `W(k)` = min(`I(k)`, `V(k)`). **Wiederkehrende Ausgaben** `W` = Summe aller `W(k)`; **einmalige Ausgaben** = `A` − `W`. Erstattungen mindern so zuerst den einmaligen Teil, und es gilt immer 0 ≤ `W` ≤ `A`. Vorkommen mit positivem Betrag zählen nicht. | bewusste Abweichung vom Sheet (`Einstellungen_Budget!H11:J31`: Gruppen Fixkosten, Flexibel, Sparen je Kategorie) |
| R11 | **Vorzeichen:** Das Vorzeichen des Betrags entscheidet: positiv = Einnahme, negativ = Ausgabe. Ein Betrag von 0 ist ungültig. Kategorien haben keinen Typ. Im Formular wird der Betrag immer positiv eingegeben; ein Umschalter "Ausgabe / Einnahme" (Vorgabe: Ausgabe) setzt das Vorzeichen. | bewusste Abweichung vom Sheet (dort entschied die Kategorie) |
| R12 | **Vorkommen wiederkehrender Transaktionen:** Eine wiederkehrende Transaktion hat ein Intervall (monatlich = 1, vierteljährlich = 3, halbjährlich = 6, jährlich = 12 Monate), ein Startdatum und optional ein Enddatum (einschließlich). Ihre Vorkommen liegen am Startdatum plus k × Intervall Monate (k = 0, 1, 2, …), jeweils am Tag des Startdatums; hat ein Monat diesen Tag nicht, am letzten Tag des Monats (31.01. → 28.02. bzw. 29.02., 30.04.; 29.02. → 28.02. in Nicht-Schaltjahren). Ein Vorkommen zählt, wenn es nicht nach dem Enddatum liegt und für sein Datum keine Ausnahme (R13) existiert. Vorkommen werden berechnet, nicht als Transaktionen gespeichert. | neu, nicht im Sheet |
| R13 | **Vorkommen bearbeiten oder löschen:** Der Nutzer wählt "Nur dieser Eintrag" oder "Dieser und alle folgenden". Sei `d` das gewählte Vorkommen. **Nur dieser Eintrag:** Für `d` wird eine Ausnahme gespeichert, das Vorkommen zählt nicht mehr. Beim Bearbeiten wird zusätzlich eine normale Transaktion mit den geänderten Werten angelegt (Datum frei wählbar, Vorschlag `d`). **Dieser und alle folgenden:** Ist `d` das erste Vorkommen, wird die Serie selbst geändert bzw. gelöscht (beim Löschen samt ihrer Ausnahmen). Sonst wird das Enddatum der Serie auf den Tag vor `d` gesetzt, Ausnahmen ab `d` entfallen, und beim Bearbeiten entsteht eine neue Serie mit den geänderten Werten und derselben `seriesId`; ihr Startdatum (Vorschlag `d`) muss nach dem vorherigen Vorkommen liegen. Vergangene Vorkommen werden nie über die Serie rückwirkend geändert; eine Option "Alle Einträge" gibt es nicht. | neu, nicht im Sheet |
| R14 | **Auswertung.** Sei `H` der aktuelle Monat (Monat von heute, R7). **Rückblick über den Zeitraum `V` bis `B`** (Monate, `V` ≤ `B` < `H`, höchstens 120 Monate; Vorgabe die 12 Monate vor `H`; die Oberfläche bietet dazu Schnellwahlen wie letzte 3, 6 oder 12 Monate und ein Kalenderjahr). Gezählt werden nur Monate ab dem Monat des ersten Eintrags (frühestes Datum einer Transaktion oder frühestes Startdatum einer Serie, beliebiges Vorzeichen). Sei `n` die Anzahl dieser Monate (0 bis 120). **Ø Ausgaben** = Summe von `A` (R2) dieser Monate / `n`; ebenso **Ø Einnahmen** (`E`) und **Ø Überschuss** (`E` − `A`, R7); bei `n` = 0 leer. **Je Monat** (aufsteigend) Einnahmen `E(m)`, Ausgaben `A(m)` und Überschuss `E(m)` − `A(m)`, dazu die Einnahmen je Kategorie (`N(k)` > 0) und die Ausgaben je Kategorie (`I(k)` > 0); ein Monat ohne Einträge hat überall 0. **Gesamt:** Summe von `E`, `A` und `E` − `A` über die `n` Monate, bei `n` = 0 leer. Anteile an den Einnahmen gibt es seit Entscheidung 34 nicht mehr. **Vorausschau ab `H`:** **Rücklagen-Topf:** Für jede Serie mit negativem Betrag, Intervall `i` > 1 Monat und einem nächsten Vorkommen `d` in `H` bis `H`+11 (R12, Ausnahmen beachtet) sei `k` die Anzahl Monate von `H` bis zum Monat von `d` (0, wenn `d` in `H` liegt). **Pro Monat** = \|Betrag\| / `i`; **Angespart** = \|Betrag\| × max(0, `i` − `k`) / `i` (im Fälligkeitsmonat ist der Topf voll, danach beginnt er bei einer Monatsrate). **Monatlich zurücklegen** = Summe aller Pro-Monat-Werte; **Sollte jetzt angespart sein** = Summe aller Angespart-Werte. **Noch fehlende Raten** je Serie = min(`i`, `k`) (seit Entscheidung 37; 0 im Fälligkeitsmonat). **Vorschau:** Für `H` und die folgenden 11 Monate je Monat alle Vorkommen von Serien mit negativem Betrag (R12, Ausnahmen beachtet) und alle einzelnen Transaktionen mit negativem Betrag, je mit \|Betrag\|, und ihre Summe; **davon aus den Rücklagen** = Summe der Vorkommen von Serien mit Intervall `i` > 1 Monat im Monat, also der Ausgaben des Rücklagen-Topfs (seit Entscheidung 37). Einnahmen zählen nicht. Die API nimmt für die Vorausschau weiter einen Monat an (Vorgabe `H`), damit Tests und der Update-Test einen festen Monat verwenden können; die Oberfläche fragt immer `H` ab. | neu, nicht im Sheet; Zeitraum von Monat bis Monat und Vorausschau ab heute seit Entscheidung 29 |

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

**Regeln für die Überarbeitung ab 04.11.2026 (Abschnitt 17):**

| # | Regel | Quelle |
|---|---|---|
| R23 | **Ø Einnahmen vor M** `Ē(M)`: Durchschnitt von `E` (R2) über die 12 Monate M−12 bis M−1. Es zählen nur Monate vor dem aktuellen Monat `H` (R7) und ab dem Monat des ersten Eintrags (wie R14); sei `n(M)` ihre Anzahl (0 bis 12). `Ē(M)` = Summe von `E` dieser Monate / `n(M)` (Skala 2), bei `n(M)` = 0 leer. Für einen Monat nach `H` zählen also höchstens die Monate bis `H`−1, weil ein laufender oder künftiger Monat den Schnitt verfälschen würde. **Mindesteinnahmen vor M** `Emin(M)`: das kleinste `E` derselben `n(M)` Monate (Skala 2), bei `n(M)` = 0 leer; ein Monat ohne Einnahmen zählt mit 0. Welcher Monat das Minimum hat, wird nicht angezeigt, weil es oft mehrere sind (seit Entscheidung 36). `Ē(M)` und `Emin(M)` sind die beiden wählbaren Basen für Nicht verplant (R4) und für die Eingabe eines Solls in Prozent (US2). **Ø Ausgaben vor M** `Ā(M)`: genauso der Durchschnitt von `A` (R2) über dieselben `n(M)` Monate (Skala 2), bei `n(M)` = 0 leer; die Übersicht zeigt ihn unter der Kachel Ausgaben (seit Entscheidung 34). Die Rechnung liegt in einer reinen Rechenklasse `IncomeAverage`, die `DashboardCalculator` und `ReserveCalculator` gemeinsam nutzen; sie mittelt beliebige Monatswerte und liefert auch ihr Minimum, und rechnet so `Ē(M)`, `Emin(M)` und `Ā(M)`. | Entscheidungen 26, 34 und 36 |
| R24 | **Kategorie nur mit Einnahmen:** Eine Kategorie ist "nur Einnahmen", wenn sie mindestens einen Eintrag hat (Transaktion oder Serie) und alle ihre Transaktionen und Serien einen positiven Betrag haben. Die Seite *Planung* blendet solche Kategorien standardmäßig aus (einblendbar), außer sie haben im Monat "Gilt ab" ein Soll; Kategorien ohne Einträge erscheinen immer. Das Backend liefert die Eigenschaft (`incomeOnly` in `/categories`). | Entscheidung 33 |
| R25 | **Fixe Kosten je Kategorie im Monat M** `F(k, M)`: Es zählen alle Serien (R12) der Kategorie mit negativem Betrag, die am letzten Tag von M laufen (Startdatum spätestens an diesem Tag, kein Enddatum oder Enddatum frühestens an diesem Tag). So zählt eine geteilte Serie (R13) nur mit ihrem neuen Teil, und eine Serie, die in M endet, nicht mehr. Jede zählt mit ihrem Monatsanteil \|Betrag\| / `i` (Intervall `i` in Monaten, wie "Pro Monat" im Rücklagen-Topf, R14). `F(k, M)` ist die Summe dieser Anteile (Skala 2, nur am Ende gerundet); ohne eine solche Serie gibt es keinen Wert. Ausnahmen (R13) und Serien mit positivem Betrag zählen nicht, weil der Anteil eine Planungsgröße ist und kein Ist. Die *Planung* zeigt `F(k, M)` für den Monat "Gilt ab" samt den einzelnen Serien (US2). Die Rechnung liegt in einer reinen Rechenklasse `FixedCosts`. | Entscheidung 36 |

**Regeln für den Bank-Import ab 07.11.2026 (Abschnitt 18):**

| # | Regel | Quelle |
|---|---|---|
| R26 | **Bankkonten:** Jede Transaktion und jede Serie gehört zu genau einem Bankkonto des Nutzers. Ein Bankkonto hat einen Namen (1 bis 50 Zeichen, eindeutig je Nutzer ohne Beachtung der Groß- und Kleinschreibung), optional eine IBAN (gespeichert ohne Leerzeichen in Großbuchstaben, eindeutig je Nutzer, geprüft auf Aufbau und Prüfziffer nach ISO 13616) und kann archiviert sein. Genau ein Konto je Nutzer ist das **Standardkonto**; es lässt sich weder archivieren noch löschen (409). Fehlt das Konto in einer Anfrage, gilt das Standardkonto. Ein Konto lässt sich nur löschen, wenn keine Transaktion und keine Serie darauf gebucht ist (sonst 409 mit dem Hinweis, es zu archivieren). Konten ändern keine Rechnung: R1 bis R25 zählen über alle Konten. Einen Kontostand führt die App nicht, weil ihr der Anfangsbestand fehlt. | Entscheidung 40 |
| R27 | **Umbuchung:** Eine Transaktion kann eine Umbuchung sein, also Geld zwischen zwei eigenen Konten. Eine Umbuchung hat keine Kategorie und zählt in keiner Rechnung (R1 bis R25 und R29); sie steht nur in der Transaktionsliste, gekennzeichnet und ohne Beitrag zu den Summen über der Liste. Jede Seite ist eine eigene Transaktion auf ihrem Konto (Ausgang negativ, Eingang positiv); die App verknüpft die beiden Seiten nicht. Eine Serie kann vorerst keine Umbuchung sein. **Erkennung beim Import:** Eine Zeile ist als Umbuchung vorgeschlagen, (a) *sicher*, wenn die IBAN der Gegenseite die IBAN eines eigenen Bankkontos ist, oder (b) *unsicher*, wenn es eine Gegenbuchung gibt: einen Eintrag auf einem anderen eigenen Konto, in derselben Datei oder schon gespeichert, mit genau dem umgekehrten Betrag und höchstens 3 Tagen Abstand, der kein Ersatz für ein Vorkommen ist (R28) und keine Umbuchung, außer einer gespeicherten Umbuchung ohne andere Seite (unter den gespeicherten Umbuchungen keine auf einem anderen Konto mit umgekehrtem Betrag und höchstens 3 Tagen Abstand, gepaart wie unten, die mit dem kleinsten Abstand zuerst), etwa eine von Hand gebuchte Umbuchung, deren andere Seite mit der Datei kommt; jede Gegenbuchung zählt nur einmal, die mit dem kleinsten Abstand zuerst. Eine unsichere Umbuchung nennt ihre Gegenbuchung mit Beschreibung, Betrag, Datum und Konto (bei einer Zeile derselben Datei deren Quellkonto und "in dieser Datei"); liegt sie in derselben Datei, springt ein Klick auf den Hinweis zu ihr und hebt sie kurz hervor. Ist die Gegenbuchung schon gespeichert und noch keine Umbuchung, wird sie beim Übernehmen ebenfalls zur Umbuchung (ihre Kategorie entfällt, der Import merkt sie sich); wird der Import rückgängig gemacht, bekommt sie ihre Kategorie zurück, sofern sie dann noch eine Umbuchung ist (seit der Code-Prüfung, 08.11.2026). | Entscheidung 41 |
| R28 | **Ist ersetzt Plan:** Eine einzelne Transaktion kann ein Vorkommen einer Serie ersetzen. Dann gibt es für das Vorkommen eine Ausnahme (R13), die auf die Transaktion verweist: Das Vorkommen zählt nicht mehr, die Transaktion zählt mit ihrem echten Betrag und für R10 als wiederkehrend (sie gehört zu `V(k)`). Wird die Transaktion gelöscht, entfällt die Ausnahme, und das Vorkommen zählt wieder. Wird die Serie gelöscht, bleibt die Transaktion als einmalige. Teilt "Dieser und alle folgenden" die Serie ab `d` (R13), geht eine verknüpfte Ausnahme ab `d` auf die neue Serie über, wenn diese an demselben Tag ein Vorkommen hat; sonst entfällt die Verknüpfung, und die Transaktion bleibt als einmalige. Eine Transaktion ersetzt höchstens ein Vorkommen, eine Umbuchung keines. **Treffer:** Für eine neue Buchung (Import oder Buchungsdialog) mit Datum `t`, Betrag `b` und Text `x` (R29) ist ein Vorkommen `d` einer Serie mit Betrag `s` ein Treffer, wenn das Vorkommen noch zählt (keine Ausnahme), `b` und `s` dasselbe Vorzeichen haben, \|`t` − `d`\| ≤ 5 Tage, \|`b` − `s`\| ≤ 20 % von \|`s`\| und entweder `x` und die Beschreibung der Serie ein gemeinsames Wort mit mindestens 4 Buchstaben haben (R29, ohne Groß- und Kleinschreibung) oder `b` = `s` und der Kategorie-Vorschlag für `x` die Kategorie der Serie ist. Gibt es mehrere Treffer, gilt einer mit gemeinsamem Wort vor einem ohne, dann der mit dem kleinsten Abstand in Tagen, dann der kleinsten Betragsdifferenz, dann der kleineren Serien-ID. Beim Import bekommt jede Zeile höchstens ein Vorkommen und jedes Vorkommen höchstens eine Zeile; die Zeilen werden nach Datum und dann nach ihrer Reihenfolge in der Datei zugeordnet. Mit einem Treffer übernimmt die Buchung die Kategorie der Serie (änderbar). Das Konto spielt keine Rolle, weil ältere Serien alle auf dem Standardkonto liegen. | Entscheidung 43 |
| R29 | **Kategorie-Vorschlag:** Der **Text** einer Buchung ist die Gegenseite (Empfänger bzw. Auftraggeber), wenn sie bekannt ist, sonst die Beschreibung. Er wird **normalisiert**: Kleinbuchstaben, alles außer Buchstaben (auch ä, ö, ü, ß) wird zu Leerzeichen, Wörter mit weniger als 2 Buchstaben entfallen. Der **Schlüssel** sind die ersten zwei Wörter. Der **Verlauf** sind alle Transaktionen mit Kategorie (keine Umbuchung) und alle Serien des Nutzers mit demselben Vorzeichen wie die Buchung, ohne archivierte Kategorien, die neuesten zuerst (Datum bzw. Startdatum). Der Vorschlag ist (1) *sicher*, wenn die bis zu 10 neuesten Einträge mit demselben Schlüssel alle dieselbe Kategorie haben; (2) *unsicher*, wenn diese Einträge verschiedene Kategorien haben: die häufigste, bei Gleichstand die des neuesten Eintrags; (3) *unsicher*, wenn kein Eintrag den Schlüssel hat, aber Einträge dasselbe erste Wort mit mindestens 4 Buchstaben: die häufigste unter den 10 neuesten; (4) *unsicher* aus der Kategorie der Bank (nur beim Import, R30): die häufigste Kategorie der bis zu 10 neuesten Transaktionen mit derselben Bankkategorie, sonst eine Kategorie, deren erstes Wort das erste Wort der Bankkategorie ist (dazu Art-Hinweise, die nur das Profil liefert, heute nur Trade Republic: Arten mit `BUY`, `SELL` oder `SAVINGS` → "Investitionen", mit `INTEREST` oder `DIVIDEND` → "Kapitalerträge", jeweils wenn es die Kategorie gibt; andere Profile und der Buchungsdialog haben keine); (5) sonst gibt es keinen Vorschlag. Ein Treffer nach R28 (Kategorie seiner Serie) und eine Umbuchung nach R27 gehen dem Vorschlag vor. Die Oberfläche belegt keinen Vorschlag vor, ob sicher oder unsicher: Er steht als Knopf "Vorschlag: …" neben dem Feld, auch "Vorschlag: Umbuchung" (seit dem zweiten Test, 07.11.2026). Die Rechnung liegt in einer reinen Rechenklasse `CategorySuggester`. | Entscheidung 42 |
| R30 | **CSV-Import:** Eine Datei hat höchstens 2 MB und 5000 Zeilen (sonst 400); sie wird nicht gespeichert. Zeichensatz UTF-8 (mit oder ohne BOM), sonst Windows-1252; Trennzeichen `;` oder `,` (das häufigere in der Kopfzeile); Felder in Anführungszeichen dürfen Trennzeichen, `""` und Zeilenumbrüche enthalten. Die **Kopfzeile** ist die erste der ersten 20 Zeilen, die alle Pflichtspalten eines Profils enthält; Zeilen davor (Vorspann) und leere Zeilen werden übersprungen; ohne passendes Profil ist die Datei unbekannt (400). **Profile** (Pflichtspalten fett): *C24* **Buchungsdatum** (`dd.MM.yyyy`), **Betrag** (deutsches Format, `€` und Tausenderpunkte erlaubt), **Zahlungsempfänger**, IBAN, **Verwendungszweck**, Beschreibung, Kontonummer, **Kontoname**, Kategorie, Unterkategorie, Transaktionstyp; *Trade Republic* **date** (ISO), **amount** (Punkt als Dezimaltrennzeichen, beliebig viele Nachkommastellen, HALF_UP auf 2), **currency**, **transaction_id**, type, name, counterparty_name, counterparty_iban, payment_reference, description, account_type; *N26* **Booking Date** (ISO), **Amount (EUR)**, **Partner Name**, Partner Iban, Type, Payment Reference, **Account Name**; *Sparkasse* (ein Profil für alle vier Exporte CSV-CAMT V2, CSV-CAMT V8, CSV-MT940 und CSV - mit Kategorien, das sich nach den vorhandenen Spalten richtet) **Auftragskonto**, **Buchungstag** (`dd.MM.yy`, Jahr 2000 bis 2099), Valutadatum, Buchungstext, Verwendungszweck, Glaeubiger ID, Mandatsreferenz, Kundenreferenz (End-to-End), Sammlerreferenz, **Beguenstigter/Zahlungspflichtiger**, Kontonummer/IBAN (MT940: Kontonummer), BIC (SWIFT-Code) (MT940: BLZ), **Betrag** (deutsches Format, eine Gutschrift ohne Vorzeichen), Waehrung, **Info**, Kategorie. **Datum** ist der Buchungstag (nicht die Wertstellung). Jedes Profil füllt die normalisierte Bankbuchung (Entscheidung 46); was eine Bank nicht liefert, bleibt leer. **Zeilen, die nicht importiert werden:** unlesbar (Datum, Betrag) oder ohne Betrag (leer oder 0, z. B. Wertpapierübertrag ohne Geld) erkennt schon das Lesen, bei der Sparkasse auch einen Wert in `Info`, der weder "Umsatz gebucht" ist noch "vorgemerkt" enthält (Grund "Status unbekannt: …"); vorgemerkt (Status `PENDING`, bei C24 eine leere Spalte Buchungsdatum, bei der Sparkasse "vorgemerkt" in `Info` oder ein leerer Buchungstag) und eine andere Währung als EUR schließt die Regel danach aus; alle stehen mit Grund in der Vorschau. **Quellkonto** einer Zeile: C24 die Spalte Kontoname (sonst Kontonummer), N26 Account Name, Trade Republic eines je `account_type`, Sparkasse die IBAN in `Auftragskonto`; sein Schlüssel ist `<Profil>:<Name>`. Ein Bankkonto merkt sich die Schlüssel, die ihm zugeordnet wurden. **Gegenseite und Zweck:** C24 Zahlungsempfänger und Verwendungszweck (leer: Beschreibung); N26 Partner Name und Payment Reference; Trade Republic counterparty_name und payment_reference (leer: description), bei einer Zeile mit `name` (Wertpapier) ist die Gegenseite `name` mit "Kauf " (Art mit `BUY` oder `SAVINGS`), "Verkauf " (`SELL`) oder "Dividende " (`DIVIDEND`) davor; Sparkasse Beguenstigter/Zahlungspflichtiger und Verwendungszweck. Beginnt der Verwendungszweck mit einer SEPA-Kennung (CSV-MT940: `EREF+`, `KREF+`, `MREF+`, `CRED+`, `DEBT+`, `SVWZ+`, `ABWA+`, `ABWE+`, `IBAN+`, `BIC+`, `COAM+`, `OAMT+`), ist der Zweck der Text nach `SVWZ+` bis zur nächsten Kennung (ohne `SVWZ+` leer), und Mandat, Gläubiger-ID und End-to-End kommen aus `MREF+`, `CRED+` und `EREF+`; sonst aus den Spalten, End-to-End aus Kundenreferenz und, wenn leer, aus Sammlerreferenz (der Export "mit Kategorien" legt sie dort ab). Der Zweck wird bereinigt: UUIDs und Ziffernfolgen ab 8 Stellen entfallen, Leerraum wird zu einem Leerzeichen, Satzzeichen am Ende entfallen. **Beschreibung** = Gegenseite, wenn der Zweck leer ist oder die Gegenseite enthält; = Zweck, wenn die Gegenseite leer ist; sonst "Gegenseite · Zweck"; ohne beides die Art der Buchung (Transaktionstyp, Type bzw. Buchungstext), sonst "Buchung"; länger als 100 Zeichen wird auf 99 Zeichen und "…" gekürzt. **Bankkategorie:** C24 "Kategorie › Unterkategorie" (ohne Unterkategorie nur Kategorie), Trade Republic und N26 die Art (type/Type), Sparkasse die Spalte Kategorie, sonst Buchungstext. **Fingerabdruck:** SHA-256 über Profil und die ID der Bank (`entryReference`, heute nur die `transaction_id` von Trade Republic), wenn die Zeile eine hat; sonst, bei jedem Profil und auch bei einer Trade-Republic-Zeile ohne ID, über Profil, Quellkonto, Datum, Betrag, Gegenseite, Zweck und die laufende Nummer gleicher Zeilen in der Datei (1, 2, …). Bei der Sparkasse entfällt der Zweck, in der Prüfsumme und beim Zählen gleicher Zeilen, weil CSV-MT940 ihn anders liefert als die CAMT-Exporte (Leerzeichen an Umbrüchen fehlen, "." statt ":"); so ist eine Buchung in jedem der vier Exporte dieselbe (Entscheidung 47). Eine Zeile, deren Fingerabdruck der Nutzer schon importiert hat, ist "schon importiert" und wird nicht noch einmal angelegt. **Mögliche Doppelung:** Gibt es eine einzelne Transaktion ohne Import mit demselben Betrag und höchstens 2 Tagen Abstand (etwa von Hand gebucht, auch wenn sie ein Vorkommen ersetzt, R28), ist die Zeile als "nicht importieren" vorbelegt und nennt die Buchung. Die Rechnung liegt in reinen Klassen ohne Spring (Lesen der Datei je Profil, Fingerabdruck, Erkennung nach R27 und R28). | Entscheidungen 39, 44, 45 und 47 |
| R31 | **Monatliche Serien im Import:** Unter den neuen Zeilen einer Vorschau (keine Umbuchung, kein Treffer nach R28) bilden Zeilen mit demselben Schlüssel (R29) und demselben Vorzeichen eine Gruppe; Zeilen, deren Betrag mehr als 20 % vom Median der Gruppe abweicht, entfallen. Für jeden Tag des Monats, den eine Zeile hat, wird jede Zeile dem Monat zugeordnet, in dem dieser Tag (in kürzeren Monaten der Monatsletzte) höchstens 5 Tage entfernt liegt. Eine Serie sind mindestens 3 Monate in Folge mit genau einer Zeile je Monat, ohne weitere Zeile der Gruppe zwischen der ersten und der letzten; es gilt der längste Lauf, bei Gleichstand der Tag, den die meisten Zeilen genau haben, dann der Tag der neuesten Zeile. Vorgeschlagen wird eine monatliche Serie ab diesem Tag im ersten Monat, mit Betrag und Beschreibung der neuesten Zeile und der häufigsten vorgeschlagenen Kategorie ihrer Zeilen (R29), je Gruppe höchstens eine. "Als Serie anlegen" merkt die Serie nur vor; sie entsteht erst beim Übernehmen, zusammen mit ihren Zeilen, und jede übernommene Zeile ersetzt das nächstgelegene freie Vorkommen der neuen Serie, höchstens 5 Tage entfernt (sonst 400, und nichts wird gespeichert). Eine Serie wird nur angelegt, wenn mindestens eine ihrer Zeilen mit übernommen wird; übrige Zeilen treffen sie danach nach R28. Wird der Import rückgängig gemacht, wird auch die Serie gelöscht, die er angelegt hat (seit der Code-Prüfung, 08.11.2026). Die Rechnung liegt in der reinen Rechenklasse `SeriesDetector`. | Entscheidung 46 |

**Nicht übernehmen:** Die Hilfsblätter `IGNORE…` (Diagrammdaten, rechnet das Frontend selbst) sowie veraltete Formeln in `Dashboard_Budget` (Zeilen 38 und 48–55 der Einnahmenspalte verweisen auf ein nicht existierendes Blatt `Monatliche_Transaktionen`). Als Referenzwerte gelten die Formeln, nicht diese Reste.

**Geldbeträge:** `BigDecimal` im Backend (Skala 2, Rundung HALF_UP), im JSON als String oder Zahl mit 2 Nachkommastellen. Kein `double`.

## 3. User Stories und Akzeptanzkriterien

### Must
- **US1 Kategorien ansehen:** Als Nutzer sehe ich alle Kategorien als eine Liste, alphabetisch sortiert (Entscheidung 38, vorher eine eigene Reihenfolge `sortOrder`), ohne Gruppen. *AK:* Beim ersten Start sind die Kategorien des Sheets als Seed vorhanden; jeder Kategoriename ist eindeutig.
- **US2 Soll festlegen:** Als Nutzer lege ich auf der Seite *Planung* je Kategorie ein Soll fest, gültig ab einem Monat ("Gilt ab", Vorgabe aktueller Monat). Es gilt für diesen und alle folgenden Monate, bis ein neuerer Eintrag es ersetzt; frühere Monate bleiben unverändert. *AK:* R3 gilt; gespeichert wird immer ein Euro-Betrag (Entscheidung 36). Je Kategorie ein Eingabefeld mit Umschalter € / %: Er wählt nur, ob ich in Euro oder in Prozent der gewählten Basis (US7a) eingebe, und rechnet die Zahl im Feld beim Umschalten um. Unter dem Feld steht immer der andere Wert ("≙ 33,78 % der Ø Einnahmen" bzw. "≙ 950,00 €, wird so gespeichert"). Die Umrechnung macht das Frontend in Cent und rundet auf 2 Nachkommastellen; ohne Basis (`Ē(M)` leer oder 0) ist % nicht wählbar, und statt des Prozentwerts steht "–". Prozent zwischen 0 und 100 (prüft das Frontend); negativer Euro-Wert wird abgelehnt (400 mit Fehlermeldung); erneutes Speichern für denselben Monat überschreibt den Eintrag dieses Monats; ein leeres Feld heißt "kein Soll ab diesem Monat"; es gibt keinen Speichern-Button: eine Änderung wird gespeichert, sobald der Fokus das Feld verlässt oder Enter gedrückt wird, danach steht kurz "Gespeichert ab …". Hat eine Kategorie fixe Kosten (R25), steht unter ihrem Namen "fix X / Monat", aufklappbar zu den einzelnen Serien mit ihrem Monatsanteil (bei einem Intervall über 1 Monat zusätzlich Betrag und Intervall, z. B. "480,00 € jährlich"). Liegt das Soll darunter, sind Feld und Zeile orange, und ein Hinweis nennt die fixen Kosten. "übernehmen" setzt das Soll auf `F(k, M)` und erscheint, wenn kein Soll gesetzt ist oder es darunter liegt. Die Spalte "Ist im Vormonat (Oktober)" zeigt das Ist der Kategorie im Monat vor "Gilt ab" als Orientierung, als schlichter Text. Kategorien nur mit Einnahmen sind ausgeblendet und lassen sich einblenden (R24). Am Handy hat das Feld eine feste Breite; das Ist im Vormonat steht unter dem Namen, fixe Kosten und Gegenwert stehen in einer Zeile unter Name und Feld, die Hinweise "kein Soll" und "unter den fixen Kosten" entfallen dort (die Farbe zeigt es). *(Überarbeitet mit Entscheidungen 26, 28, 33 und 36.)*
- **US3 Transaktion erfassen:** Als Nutzer erfasse ich Betrag, Beschreibung, Datum und Kategorie und wähle per Umschalter "Ausgabe" oder "Einnahme". *AK:* R11; eingegebener Betrag > 0, gespeichert mit Vorzeichen (API: Betrag ≠ 0); Beschreibung höchstens 100 Zeichen; Kategorie muss existieren (sonst 400); Datum Pflicht.
- **US4 Transaktion ändern/löschen.**
- **US5 Übersicht:** Als Nutzer wähle ich einen Monat (‹ Monat ›, dazu "Zum aktuellen Monat", wenn ein anderer Monat gewählt ist; der Monat lässt sich im Feld zwischen den Pfeilen auch auswählen oder eintippen, und der Knopf "Zum aktuellen Monat" behält seinen Platz, damit die Navigation nicht springt) und sehe drei Kacheln: Einnahmen (darunter der Ø der letzten 12 Monate `Ē(M)` mit seinem Zeitraum, R23), Ausgaben (davon wiederkehrend und einmalig, darunter der Ø `Ā(M)` über denselben Zeitraum, R23) und Überschuss bzw. "Noch frei" (R7). Darunter eine Budget-Liste: je Kategorie mit Icon (US28) das Ist von Soll, ein Fortschrittsbalken und "noch X" bzw. "X über dem Soll" (Abweichung zum Soll, R5), am Ende Gesamt (R6); danach die Einnahmen-Liste (R9). Alle Werte stehen in Euro. Ein Klick auf eine Kategorie klappt ihre Buchungen und Vorkommen im Monat auf (US12). Ein Hinweis nennt, ob der Monat abgeschlossen, der aktuelle oder ein künftiger ist. *AK:* R1 bis R10; Werte entsprechen der Testfixture F1 (Abschnitt 12); ohne gezählten Monat (R23) fehlen beide Ø-Angaben; über dem Soll und "Überschritten" in der Warnfarbe; Kategorien ohne Soll zeigen "kein Soll" ohne Balken; hat der Nutzer weder Transaktionen noch Serien noch ein Soll, zeigt die Seite zusätzlich drei Schritte für den Einstieg (Soll festlegen, erste Buchung, Übersicht ansehen). *(Überarbeitet mit Entscheidungen 26 bis 28 und 34.)*

### Should
- **US6 Wiederkehrende Transaktionen erfassen:** Als Nutzer erfasse ich eine wiederkehrende Transaktion im selben Formular wie eine einmalige: Betrag mit Umschalter "Ausgabe / Einnahme", Beschreibung, Datum, Kategorie und "Wiederholen" (nie, monatlich, vierteljährlich, halbjährlich, jährlich). Bei einer Wiederholung ist das Datum die erste Abbuchung (Startdatum), und ein optionales Enddatum erscheint. Eine Serie wird über ihre Vorkommen in der Monatsliste geändert oder beendet (R13); eine eigene Serienliste gibt es nicht. *AK:* R11, R12; Betrag > 0 eingegeben, Beschreibung höchstens 100 Zeichen, Kategorie muss existieren (sonst 400), Startdatum Pflicht, Enddatum nicht vor dem Startdatum (sonst 400); die Vorkommen erscheinen in der Transaktionsliste ihres Monats, als "wiederkehrend" gekennzeichnet, und zählen im Dashboard mit vollem Betrag (R1).
- **US6a Wiederkehrende Einträge bearbeiten/löschen:** Als Nutzer bearbeite oder lösche ich ein Vorkommen und wähle "Nur dieser Eintrag" oder "Dieser und alle folgenden". *AK:* R13; frühere Vorkommen bleiben unverändert; ein Datum, das kein Vorkommen der Serie ist, ergibt 404.
- **US7 Wiederkehrende und einmalige Ausgaben** (ersetzt die Gruppensummen): Als Nutzer sehe ich in der Kachel Ausgaben, wie viel davon wiederkehrend (meine Fixkosten) und wie viel einmalig war. *AK:* R10; die beiden Werte ergeben zusammen die Ausgaben.
- **US7a Nicht verplant:** Als Nutzer sehe ich auf der Seite *Planung* oben eine Leiste, die beim Scrollen stehen bleibt, mit Mindesteinnahmen und Ø Einnahmen (R23, mit ihrem Zeitraum, in dieser Reihenfolge), verplantem Soll und dem nicht verplanten Teil für den Monat "Gilt ab" (R4); sie rechnet nach jedem gespeicherten Soll neu. Ein Klick auf die Kachel Mindesteinnahmen oder Ø Einnahmen macht sie zur Basis für "Nicht verplant", "Verplant … % der …" und die Eingabe in Prozent (US2). Die gewählte Kachel ist markiert ("Basis"), die andere zeigt "als Basis wählen". Vorgabe sind die Mindesteinnahmen, weil vorsichtige Planung mit dem schwächsten Monat rechnet; die Wahl merkt sich der Browser. Am Handy steht der Zeitraum im Einleitungstext statt in den Kacheln. *(Überarbeitet mit Entscheidungen 26 und 36.)*
- **US8 Monatsnavigation** (vor/zurück) und Anzeige leerer Monate ohne Fehler, nur noch in der *Übersicht* (Entscheidung 28): *Transaktionen* hat einen Zeitraumfilter (US26), *Planung* das Feld "Gilt ab" (US2), *Auswertung* den Zeitraum des Rückblicks (US13).
- **US13 Auswertung:** Die Seite hat zwei Unterseiten. **Rückblick:** Als Nutzer wähle ich einen Zeitraum von Monat bis Monat (Vorgabe die letzten 12 abgeschlossenen Monate; Schnellwahl letzte 3, 6 und 12 Monate und Kalenderjahr) und sehe Ø Einnahmen, Ø Ausgaben und Ø Überschuss, ein Balkendiagramm mit Einnahmen und Ausgaben je Monat (Recharts; beim Überfahren mit der Maus wird der Monat hervorgehoben, und ein Tooltip zeigt Einnahmen, Ausgaben und Überschuss) und eine Tabelle je Monat mit den Zeilen Ø und Gesamt. Jeder Monat lässt sich aufklappen und zeigt seine Einnahmen und Ausgaben je Kategorie (mit Icon, US28). Alle Werte stehen in Euro (Entscheidung 34). **Vorausschau:** ab dem aktuellen Monat die Rücklagen für seltene Ausgaben (seit Entscheidung 37): ein Satz "Leg jeden Monat 58,36 € beiseite. Davon bezahlst du 2 seltene Ausgaben, als Nächstes „Rundfunkbeitrag“ im Januar 2027 (55,08 €)." (bei einer Ausgabe "Davon bezahlst du 1 seltene Ausgabe: „Kfz-Versicherung“ im März 2027 (480,00 €)."; die nächste ist die mit dem frühesten Fälligkeitsdatum), die Kacheln "Jeden Monat beiseitelegen" und "Sollte zurückgelegt sein" ("Summe der Balken unten") und je seltene Ausgabe eine Karte mit Name, Intervall, Betrag, Fälligkeit, einem Fortschrittsbalken "320,00 € von 480,00 €" und "noch 4 Raten à 40,00 €" (im Fälligkeitsmonat "diesen Monat fällig"). Darunter die Vorschau der nächsten 12 Monate, aufgebaut wie der Rückblick: ein Diagramm mit allen gebuchten Ausgaben je Monat als Balken, der Teil aus den Rücklagen in Blau, darunter eine Tabelle Monat · Ausgaben · davon aus den Rücklagen; jeder Monat lässt sich aufklappen und zeigt seine Ausgaben mit Datum, und beim Überfahren eines Monats in Diagramm oder Tabelle wird er in beiden hervorgehoben; der Tooltip zeigt die Ausgaben und den Teil aus den Rücklagen mit den Namen. *AK:* R14; liegen im Zeitraum Monate vor dem ersten Eintrag, nennt die Seite die Anzahl der berücksichtigten Monate; ohne berücksichtigten Monat zeigt sie "–" und einen Hinweis; in der API ergeben "Von" nach "Bis", "Bis" im aktuellen Monat oder später oder mehr als 120 Monate 400; auf schmalen Bildschirmen steht "Bis" unter "Von"; jede Unterseite hat eine eigene Adresse (US29); die Überschriften Rücklagen und Vorschau 12 Monate und die beiden Kacheln haben ein Info-Symbol (?), das beim Überfahren, beim Fokus und beim Antippen eine kurze Erklärung zeigt (Entscheidung 37). *(Überarbeitet mit Entscheidungen 29 und 34; ersetzt die feste Auswahl 3, 6, 12 oder 24 Monate aus AP 16 und das Einklappen von Rückblick und Vorschau aus AP 15a.)*

### Ausbau ab 27.10.2026: Must (Voraussetzung für die reale Nutzung)
- **US10 Kategorien verwalten** (früher Nice): Als Nutzer lege ich unter *Einstellungen › Kategorien* Kategorien an (mit Icon, US28), benenne sie um, archiviere oder lösche sie. *AK:* Name 1 bis 50 Zeichen, eindeutig je Nutzer (sonst 409); Löschen nur ohne Transaktionen, Serien und Soll-Einträge, sonst 409 mit dem Hinweis, die Kategorie zu archivieren; eine archivierte Kategorie fehlt in den Formularen und auf der Seite *Planung*, vergangene Monate in *Übersicht* und *Auswertung* zeigen sie weiter korrekt; eine neue Kategorie steht an ihrem alphabetischen Platz (Entscheidung 38). Buchungen auf einer archivierten Kategorie bleiben erlaubt, damit bestehende Einträge bearbeitet werden können; das Formular zeigt sie nur, wenn sie schon gewählt ist. Gleiche Namen werden ohne Beachtung der Groß- und Kleinschreibung erkannt. *Überarbeitung (AP 19j):* *Einstellungen* hat Unterseiten (Kategorien, Konto, Einladungen, Nutzer). Kategorien stehen alphabetisch und lassen sich nicht sortieren (Entscheidung 38); "+ Kategorie" fragt Name und Icon ab; Umbenennen, Icon ändern (US28), Archivieren und Löschen liegen im Menü "⋯" je Zeile; archivierte Kategorien stehen in einem eigenen Abschnitt mit "Wiederherstellen".
- **US11 Flyway-Migrationen** statt Seeder (früher Nice). *AK:* Das Schema entsteht nur aus Migrationen; eine gemergte Migration wird nie geändert; die Standard-Kategorien legt der Service beim Anlegen eines Nutzers an (US15), nicht eine Migration.
- **US14 Anmelden und Abmelden:** Als Nutzer melde ich mich mit E-Mail und Passwort an und wieder ab. *AK:* R15; falsche Angaben ergeben eine allgemeine Fehlermeldung (ohne Hinweis, ob die E-Mail existiert); nach 5 Fehlversuchen in 15 Minuten wird die Anmeldung für diese E-Mail 15 Minuten gesperrt; die Sitzung läuft nach 30 Tagen ohne Nutzung ab.
- **US15 Einladung:** Als Admin erzeuge ich einen Einladungslink und schicke ihn selbst weiter (kein Mailversand durch die App). Wer den Link öffnet, legt E-Mail, Anzeigename und Passwort fest und erhält die Standard-Kategorien (Seed, Abschnitt 4) ohne Beträge. *AK:* Link einmalig nutzbar und 7 Tage gültig (sonst 410); E-Mail eindeutig (409); Passwort mindestens 12 Zeichen. Der Systemadmin kommt aus Umgebungsvariablen (`ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_DISPLAY_NAME`, siehe US25).
- **US16 Konto:** Als Nutzer sehe ich unter *Einstellungen › Konto* meinen Anzeigenamen und meine E-Mail-Adresse und ändere sie und mein Passwort jeweils über "Ändern" (Anzeigename 1 bis 50 Zeichen ohne Passwort; E-Mail mit Passwort, außer beim Systemadmin, dessen E-Mail nur `ADMIN_EMAIL` festlegt (409); Passwort mit altem Passwort, das neue wird zweimal eingegeben und muss übereinstimmen, damit ein Tippfehler den Nutzer nicht aussperrt), exportiere alle meine Daten (JSON) und lösche mein Konto samt allen Daten. *AK:* Löschen erfordert die Eingabe des Passworts; danach existieren keine Daten des Nutzers mehr. Neue E-Mail eindeutig (409, ohne Groß-/Kleinschreibung) und gültig (400). Der Systemadmin kann sein Konto nicht löschen (409), damit es immer einen gibt.

### Ausbau ab 27.10.2026: Should
- **US25 Nutzerübersicht:** Als Admin sehe ich unter *Einstellungen › Nutzer* alle Konten mit Anzeigename, E-Mail, Rolle, Anlagedatum und letzter Anmeldung ("noch nie" ohne Anmeldung), aber keine Finanzdaten (R15 gilt weiter für alle Budgetdaten). Ich mache einen User zum Admin und einen Admin zum User. *AK:* Nur Admins und der Systemadmin (sonst 403); der Systemadmin bleibt immer Systemadmin (409), die Rolle `SYSTEM_ADMIN` lässt sich nicht vergeben (400); die eigene Rolle lässt sich nicht ändern (409); eine Rollenänderung gilt ab der nächsten Anfrage, ohne neue Anmeldung; die eigene Zeile zeigt den Namen mit "(Du)". **Systemadmin** ist immer das Konto mit der E-Mail aus `ADMIN_EMAIL`; das Backend prüft das bei jedem Start: Gibt es das Konto nicht, legt es dieses mit `ADMIN_PASSWORD` und den Standard-Kategorien an; gibt es ein Konto mit dieser E-Mail, wird es Systemadmin und behält sein Passwort; ein bisheriger Systemadmin mit anderer E-Mail wird Admin. Ohne `ADMIN_EMAIL` ändert sich nichts (Warnung im Log, wenn es keinen Systemadmin gibt).
- **US17 Personen:** Als Nutzer lege ich unter *Einstellungen › Personen* Personen an, mit denen ich Ausgaben teile (nur Name, eindeutig je Nutzer), und benenne sie um. *AK:* Löschen nur bei Saldo 0 und ohne geteilte Einträge, sonst 409.
- **US18 Ausgabe teilen:** Als Nutzer teile ich im Transaktionsformular eine einmalige oder wiederkehrende Ausgabe: Gesamtbetrag, wer bezahlt hat, Beteiligte und Aufteilung (Beträge, gleichmäßig oder Prozent), z. B. "200 € meines Anteils von 500 €". *AK:* R16, R17; das Formular zeigt meinen Anteil, bevor gespeichert wird; im Budget und in der Monatsliste steht mein Anteil, die Liste kennzeichnet geteilte Einträge ("geteilt, gesamt 500,00"); R13 behält die Aufteilung (R18).
- **US19 Salden und Ausgleich:** Als Nutzer sehe ich auf der Seite *Teilen* je Person den Saldo ("schuldet dir" / "du schuldest") und die geteilten Einträge und erfasse einen Ausgleich (Betrag, Richtung, Datum). *AK:* R18; Werte entsprechen der Testfixture F2 (Abschnitt 12); ein Ausgleich erscheint nicht im Dashboard.
- **US20 Sparplan und Prognose:** Als Nutzer lege ich Depots an, verknüpfe Serien als Sparpläne und sehe je Depot die Prognose in drei Szenarien als Tabelle je Jahr und als Diagramm. *AK:* R19; nur Serien mit negativem Betrag (sonst 400); eine Serie gehört zu höchstens einem Depot.
- **US21 Einmalzahlungen:** Als Nutzer verknüpfe ich eine Transaktion mit einem Depot (Einzahlung, Auszahlung, Ertrag). *AK:* R20; Vorzeichen passend zur Art (sonst 400).
- **US22 Freistellungsauftrag:** Als Nutzer trage ich den Sparerpauschbetrag des Jahres und je Bank meinen Freistellungsauftrag ein und sehe, wie viel ausgeschöpft ist. *AK:* R21.

### Überarbeitung ab 04.11.2026 (Abschnitt 17)
- **US26 Transaktionen filtern:** Als Nutzer sehe ich auf der Seite *Transaktionen* die Einträge eines Zeitraums (Vorgabe aktueller Monat; Schnellwahl dieser Monat, letzte 3 Monate, dieses Jahr und frei von Datum bis Datum), nach Tagen gruppiert mit Tagessumme, neueste zuerst, bei mehr als einem Monat zusätzlich nach Monaten getrennt, und filtere nach Beschreibung (Suche), Kategorie, Art (wiederkehrend oder einmalig) und, bei mehr als einem Konto, nach Konto (seit dem zweiten Test, 07.11.2026). *AK:* R1, R12: Vorkommen von Serien im Zeitraum erscheinen, als "wiederkehrend" gekennzeichnet; Einträge nach heute sind als "geplant" gekennzeichnet; über der Liste stehen Zeitraum, Anzahl der Einträge und die Summen der Einnahmen und der Ausgaben; in der API ergeben "Von" nach "Bis" oder mehr als 10 Jahre 400; Suche, Kategorie und Art filtern die Einträge des geladenen Zeitraums; der Hinweis auf die Rücklagen bleibt (Entscheidung 15). Auf schmalen Bildschirmen steht "Bis" unter "Von".
- **US27 Buchungsdialog:** Als Nutzer erfasse und bearbeite ich Buchungen in einem Dialog, am Handy als Fenster von unten. Er öffnet sich von *Übersicht* und *Transaktionen* über "+ Buchung", am Handy über einen runden Knopf unten rechts. Reihenfolge: Umschalter Ausgabe / Einnahme, großer Betrag, Beschreibung, Kategorie mit Schnellwahl der zuletzt genutzten Kategorien zum gewählten Vorzeichen (aus den geladenen Einträgen) und Auswahl aller Kategorien, Datum (bei einer Serie "Erste Abbuchung"), Wiederholen und bei einer Serie optional das Enddatum. Ein Klick auf eine Zeile der Liste öffnet den Dialog zum Bearbeiten, Löschen liegt im Dialog; bei einem Vorkommen fragt der Dialog "Nur dieser Eintrag" oder "Dieser und alle folgenden" (R13). *AK:* wie US3, US4, US6 und US6a; Fehlermeldungen stehen im Dialog; nach dem Speichern oder Löschen erscheint eine kurze Bestätigung.
- **US28 Kategorie-Icons:** Als Nutzer wähle ich unter *Einstellungen › Kategorien* je Kategorie ein Icon aus einer festen Auswahl; die Standard-Kategorien haben passende Icons (Abschnitt 4). Das Icon steht in der Übersicht, in der Transaktionsliste, im Rückblick und in den Einstellungen. *AK:* unbekannter Icon-Schlüssel → 400; eine neue Kategorie ohne Icon bekommt `tag`; bestehende Kategorien bekommen per Migration das Icon ihres Standardnamens, sonst `tag`.
- **US29 Navigation:** Als Nutzer wechsle ich über die Reiter *Übersicht* · *Transaktionen* · *Planung* · *Auswertung*, am Handy über eine Leiste am unteren Rand. *Einstellungen* und *Abmelden* liegen im Kontomenü oben rechts. Jede Seite und Unterseite hat eine eigene Adresse, damit Neuladen, Lesezeichen und der Zurück-Knopf des Browsers funktionieren. *AK:* bedienbar ab 360 px Breite, Bedienelemente mindestens 44 px groß; die Seiten-Adressen sind deutsch (z. B. `#/auswertung/rueckblick`).

### Bank-Import ab 07.11.2026 (Abschnitt 18)
- **US30 Bankkonten:** Als Nutzer lege ich unter *Einstellungen › Bankkonten* meine Konten an (Name, optional IBAN), benenne sie um, ändere die IBAN, lege das Standardkonto fest, archiviere und lösche sie. *AK:* R26; Name 1 bis 50 Zeichen und eindeutig (409), IBAN gültig (400) und eindeutig (409); jeder Nutzer hat ab der Migration bzw. ab dem Anlegen ein Konto "Girokonto" als Standardkonto; ein archiviertes Konto fehlt im Buchungsdialog, außer es ist schon gewählt; der Buchungsdialog hat ein Feld Konto (Vorgabe Standardkonto), das bei nur einem Konto entfällt; die Transaktionsliste nennt das Konto, wenn es mehr als eines gibt; der Datenexport (US16) enthält die Konten.
- **US31 Umbuchung:** Als Nutzer markiere ich im Buchungsdialog eine Buchung als Umbuchung zwischen eigenen Konten; sie hat dann keine Kategorie und zählt nicht in Übersicht, Planung und Auswertung. *AK:* R27; in der Kategorie-Auswahl steht "Umbuchung (zählt nicht)" als eigene Wahl; die Liste zeigt "Umbuchung" mit eigenem Icon; der Filter Art in *Transaktionen* hat zusätzlich "Umbuchung"; eine Umbuchung kann kein Vorkommen ersetzen (400).
- **US32 Kategorie-Vorschlag:** Als Nutzer bekomme ich im Buchungsdialog beim Tippen der Beschreibung eine Kategorie vorgeschlagen, solange ich keine selbst gewählt habe. *AK:* R29; der Vorschlag steht als Knopf "Vorschlag: …" unter dem Feld und wird erst mit einem Klick gewählt (seit dem zweiten Test, 07.11.2026, vorher vorausgewählt); Vorschläge nur aus den eigenen Buchungen (R15).
- **US33 CSV-Import:** Als Nutzer lade ich unter *Transaktionen › Importieren* eine CSV-Datei meiner Bank hoch und sehe eine Vorschau: erkannte Bank, Anzahl der Zeilen (neu, schon importiert, nicht importierbar), die Quellkonten der Datei mit der Zuordnung zu meinen Bankkonten (vorbelegt aus früheren Importen, sonst "Neues Konto anlegen") und je Zeile Datum, Beschreibung, Betrag, Kategorie und Hinweise. Keine Zeile ist vorbelegt; ein Vorschlag (auch "Umbuchung") steht als Knopf daneben, und "Alle Vorschläge übernehmen" setzt ihn bei allen offenen Zeilen, außer einer unsicheren Umbuchung (R27), die nur ihr eigener Knopf setzt. Ersetzen mindestens 2 gewählte Zeilen Vorkommen derselben Serie (R28), steht oben eine Karte je Serie ("3 Buchungen ersetzen die geplante Serie „Miete“ (Wohnen) vom … bis …"); "Alle übernehmen (N)" gibt ihnen die Kategorie der Serie und setzt "ersetzt", danach zeigt die Karte "Alle N ersetzen ihr Vorkommen". Ist die Kategorie der Serie archiviert, wählt der Nutzer je Zeile. Zeilen, die eine Karte erledigt hat (die gewählten Zeilen einer vorgemerkten Serie, die mit "Alle übernehmen" übernommenen Treffer), stehen nicht mehr in der Liste, sondern aufklappbar in der Karte ("3 Zeilen zeigen", nur zum Ansehen); über der Liste steht "N Zeilen stehen in den Karten oben", "Neu (N)" zählt nur die Zeilen der Liste. "Nicht anlegen" bringt die Zeilen einer Serie mit ihrer Kategorie zurück, "Zurücknehmen" die Treffer ohne Kategorie. Eine Zeile, die der Nutzer in der Liste selbst setzt, bleibt dort. Zeilen, die nach einer monatlichen Serie aussehen (R31), zeigt eine Karte mit "Als Serie anlegen"; die Serie entsteht erst beim Übernehmen, "Abbrechen" legt nichts an. "Fertige übernehmen" speichert die gewählten Zeilen mit Kategorie, der Rest bleibt in der Vorschau; "Abbrechen" oben schließt sie (seit dem zweiten Test, 07.11.2026). Wähle ich für eine Zeile eine Kategorie, erscheint sie bei den noch offenen Zeilen mit demselben Schlüssel (R29) als Vorschlag, gesetzt wird sie dort nicht; eine gewählte Umbuchung wird nie weitergegeben (seit dem 07.11.2026, Fehler aus dem Test: dieselbe Gegenseite wie ein Zahlungsdienst steht für ganz verschiedene Buchungen). Jede Zeile lässt sich abwählen ("nicht importieren"). Übernehmen geht erst, wenn jede gewählte Zeile eine Kategorie hat oder eine Umbuchung ist. *AK:* R26 bis R31; nach dem Übernehmen erscheint eine Bestätigung mit der Anzahl, und *Transaktionen* zeigt den Zeitraum des Imports; ein zweiter Import derselben Datei legt nichts an; Fehler (unbekanntes Format, zu groß) stehen auf der Seite; am Handy Karten statt Tabelle.
- **US34 Import rückgängig:** Als Nutzer sehe ich auf der Import-Seite meine bisherigen Importe (Datum, Bank, Anzahl, Zeitraum) und mache einen ganz rückgängig. *AK:* Alle Transaktionen des Imports werden gelöscht, ersetzte Vorkommen zählen wieder (R28), Serien, die der Import angelegt hat, werden gelöscht (R31), gespeicherte Gegenbuchungen, die beim Import zur Umbuchung wurden, bekommen ihre Kategorie zurück, sofern sie noch Umbuchungen sind (R27); eine Umbuchung aus einem anderen Import, deren Gegenbuchung so gelöscht wird, bleibt Umbuchung; Rückfrage vor dem Löschen; ein fremder Import ergibt 404.
- **US35 Ist ersetzt Plan:** Als Nutzer sehe ich in der Import-Vorschau und im Buchungsdialog, wenn eine Buchung ein geplantes Vorkommen einer Serie ersetzt ("ersetzt „Miete“ vom 01.11.", abwählbar), damit nichts doppelt zählt. *AK:* R28; ein ersetztes Vorkommen erscheint nicht mehr in Liste und Übersicht, die Buchung zählt als wiederkehrend; Löschen der Buchung bringt das Vorkommen zurück; ein fremdes oder nicht zählendes Vorkommen ergibt 400.

### Nice
- **US9 Diagramm** (Soll gegen Ist je Kategorie) mit Recharts. *Ersetzt durch das Diagramm in US13 (Erweiterung).*
- **US23 Depot mit Positionen (vorgemerkt, eigene Spec-Runde):** R22.
- **US24 Teilen mit verknüpften Nutzern (vorgemerkt, eigene Spec-Runde):** Eine Person kann mit einem registrierten Nutzer verknüpft werden (Anfrage und Bestätigung). Eine geteilte Ausgabe erscheint beim anderen Nutzer im Posteingang; erst wenn er eine eigene Kategorie wählt, wird sein Anteil bei ihm gebucht (Kategorien sind je Nutzer verschieden). Beide sehen denselben Saldo mit umgekehrtem Vorzeichen; ändert der Ersteller den Eintrag, erscheint die Änderung erneut im Posteingang.
- **US12 Kategorie aufklappen (vorgemerkt, später):** Im Dashboard zeigt ein Klick auf eine Kategorie ihre Buchungen und Vorkommen wiederkehrender Transaktionen im Monat (R1, R12). Passt gut zusammen mit US9. *Umgesetzt mit der Überarbeitung als Teil von US5 (AP 19e).*

## 4. Datenmodell

```
Category
  id            Long, PK
  name          String, eindeutig, nicht leer
  sortOrder     int      (seit Entscheidung 38 ungenutzt; Spalte bleibt, weil gemergte Migrationen nicht geändert werden)

CategoryTarget                         (Soll ab einem Monat, siehe R3)
  id            Long, PK
  category      → Category
  validFrom     LocalDate              (immer der 1. des Monats)
  targetAmount  BigDecimal, nullable   (leer = kein Soll ab validFrom; targetPercent entfällt mit Entscheidung 36, Migration V6)
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

**Überarbeitung ab 04.11.2026:** Neu an `Category`: `icon String`, nicht leer, ein Schlüssel aus der festen Liste `home`, `bolt`, `shield`, `car`, `card`, `heart`, `cart`, `music`, `suitcase`, `bag`, `gift`, `pulse`, `trend`, `briefcase`, `wallet`, `laptop`, `receipt`, `coins`, `landmark`, `plus`, `tag`; Vorgabe `tag` (US28). Das Frontend zeichnet zu jedem Schlüssel ein eigenes Linien-Icon; die Liste kann wachsen, ohne das Schema zu ändern.

```
User
  id            Long, PK
  email         String, eindeutig (klein geschrieben gespeichert)
  passwordHash  String                 (BCrypt oder Argon2, nie das Passwort)
  displayName   String, 1..50
  role          USER | ADMIN | SYSTEM_ADMIN   (genau ein SYSTEM_ADMIN: das Konto mit der E-Mail aus `ADMIN_EMAIL`, US25)
  createdAt     Instant
  lastLoginAt   Instant, nullable      (jede erfolgreiche Anmeldung, auch nach dem Annehmen einer Einladung)

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

**Bank-Import ab 07.11.2026 (Abschnitt 18):**

```
BankAccount                            (R26, US30)
  id            Long, PK
  owner         → User
  name          String, 1..50          (eindeutig je owner, ohne Groß-/Kleinschreibung)
  iban          String, nullable       (ohne Leerzeichen, groß; eindeutig je owner)
  defaultAccount boolean               (genau eines je owner)
  archived      boolean

BankAccountSource                      (Quellkonto einer Bank-Datei, R30)
  account       → BankAccount
  sourceKey     String, 1..120         (z. B. "C24:Ausgabenkonto"; eindeutig je owner über das Konto)

ImportBatch                            (ein Import, US33, US34)
  id            Long, PK
  owner         → User
  profile       C24 | TRADE_REPUBLIC | N26
  createdAt     Instant
  firstDate, lastDate  LocalDate       (Zeitraum der angelegten Buchungen)
  count         int

Transaction (neu)
  account       → BankAccount          (R26; Migration: Konto "Girokonto" je Nutzer)
  category      → Category, nullable   (leer genau dann, wenn transfer)
  transfer      boolean                (R27)
  counterparty  String, 0..100, nullable    (Gegenseite aus dem Import, für R29)
  bankCategory  String, 0..100, nullable    (Kategorie bzw. Art der Bank, für R29)
  importBatch   → ImportBatch, nullable
  fingerprint   String(64), nullable   (R30; eindeutig je owner)

RecurringTransaction (neu)
  account       → BankAccount          (R26)

RecurringException (neu)
  transaction   → Transaction, nullable, eindeutig   (R28: die Transaktion, die das Vorkommen ersetzt)
```

Die Positionen des Depots (R22, US23) werden in ihrer eigenen Spec-Runde modelliert.

Seed (aus `Einstellungen_Budget`, ohne Gruppen, angezeigt alphabetisch (Entscheidung 38); ab AP 18 für jeden neuen Nutzer, siehe US15): Wohnen, Nebenkosten, Versicherungen, Mobilität, Kredite, Spenden, Lebensmittel, Freizeit, Urlaub, Persönlicher Bedarf, Geschenke, Gesundheit, Investitionen, Selbstständigkeit, Gehalt/Lohn (netto), Nebenjob, Einnahmen Selbstständigkeit, Kapitalerträge, Staatliche Leistungen, Sonstige Einnahmen. Sparen und Investieren sind normale Kategorien. Die Platzhalter-Zeilen des Sheets entfallen, weil die App Kategorien frei anlegen lässt (US10). Der im Sheet doppelte Name "Selbstständigkeit" ist umbenannt, weil Namen eindeutig sind. Die berechnete Rest-Kategorie des Sheets entfällt (R4).

Icons der Standard-Kategorien (US28): Wohnen `home`, Nebenkosten `bolt`, Versicherungen `shield`, Mobilität `car`, Kredite `card`, Spenden `heart`, Lebensmittel `cart`, Freizeit `music`, Urlaub `suitcase`, Persönlicher Bedarf `bag`, Geschenke `gift`, Gesundheit `pulse`, Investitionen `trend`, Selbstständigkeit `briefcase`, Gehalt/Lohn (netto) `wallet`, Nebenjob `laptop`, Einnahmen Selbstständigkeit `receipt`, Kapitalerträge `coins`, Staatliche Leistungen `landmark`, Sonstige Einnahmen `plus`.

**Seed-Beträge:** ausschließlich erfundene Werte, keine echten Zahlen (Repo ist öffentlich einsehbar).

## 5. REST-API (Skizze für OpenAPI)

Basis: `/api`, JSON. Fehler als RFC 7807 (`ProblemDetail`), Validierung per Bean Validation. Faustregel: Unbekannte Ressource in der URL → 404; ungültiger Inhalt im Body oder in Parametern (auch eine unbekannte `categoryId`) → 400.

| Methode | Pfad | Zweck | Erfolg |
|---|---|---|---|
| GET | `/categories?month=2026-10` | Alle Kategorien, sortiert, mit dem in diesem Monat gültigen Soll (`targetAmount`, `targetValidFrom`; `targetPercent` entfällt mit Entscheidung 36) und `archived` (ab AP 15) | 200, 400 (Monat fehlt/ungültig) |
| PUT | `/categories/{id}/target?month=2026-11` | Soll ab diesem Monat, Body `{targetAmount}` (`null` = kein Soll ab diesem Monat; seit Entscheidung 36 ohne `targetPercent`) | 200, 404, 400 (auch Monat fehlt/ungültig) |
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
| DELETE | `/account` | Konto samt Daten löschen, Body `{password}` (US16) | 204, 400, 409 (Systemadmin) |
| PUT | `/account/email` | E-Mail ändern, Body `{email, password}` (US16) | 200, 400, 409 |
| PUT | `/account/display-name` | Anzeigenamen ändern, Body `{displayName}` (US16) | 200, 400 |
| GET | `/users` | Alle Konten ohne Finanzdaten, nur Admins (US25) | 200, 403 |
| PUT | `/users/{id}/role` | Rolle ändern, Body `{role: USER\|ADMIN}`, nur Admins (US25) | 200, 400, 403, 404, 409 |
| POST | `/categories` | Kategorie anlegen, Body `{name, icon?}` (US10, US28) | 201 + Location, 400, 409 |
| PUT | `/categories/{id}` | Ändern, Body `{name, archived, icon?}`; seit Entscheidung 38 ohne `sortOrder` (ein mitgeschicktes Feld wird ignoriert), alle Listen sind alphabetisch | 200, 404, 400, 409 |
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

**Überarbeitung ab 04.11.2026 (Abschnitt 17):**

| Methode | Pfad | Zweck | Erfolg |
|---|---|---|---|
| GET | `/transactions?from=2026-10-01&to=2026-10-31` | Einträge eines Zeitraums (beide Tage einschließlich), sonst wie `?month=`; `month` bleibt als Kurzform für einen Monat (US26) | 200, 400 (Datum fehlt/ungültig, `from` nach `to`, mehr als 10 Jahre) |
| GET | `/history?from=2026-01&to=2026-09` | Rückblick (R14); ohne Parameter die 12 Monate vor dem aktuellen Monat | 200, 400 (`from` nach `to`, `to` nicht vor dem aktuellen Monat, mehr als 120 Monate) |
| GET | `/reserves?month=2026-11` | Vorausschau (R14): Rücklagen-Topf und Vorschau; `month` optional, Vorgabe der aktuelle Monat; `months`, die Ø-Werte und `history` entfallen (stehen jetzt in `/history`) | 200, 400 |
| GET | `/dashboard?month=2026-10` | zusätzlich `averageIncome` (`Ē(M)`), `averageIncomeMonths` (`n(M)`), `averageIncomeFrom`, `averageIncomeTo` und `averageExpenses` (`Ā(M)`, R23); alle `…Share`- und `share`-Felder entfallen (Entscheidung 34); `unplanned` ist jetzt `Ē(M)` − `P` (R4) und leer ohne `Ē(M)`; `surplusLabel` nach R7; neu `monthState` (`PAST`, `CURRENT`, `FUTURE`) | 200, 400 |
| GET | `/categories?month=2026-10` | zusätzlich `icon` und `incomeOnly` (R24) | 200, 400 |
| GET | `/reserves?month=2026-11` | seit Entscheidung 37 zusätzlich `ratesLeft` je Eintrag in `reserves` (noch fehlende Raten, R14), `fromReserves` je Monat in `forecast` (Betrag aus den Rücklagen) und je Eintrag (`true` bei einem Vorkommen einer Serie des Topfs) | 200, 400 |
| GET | `/dashboard?month=2026-10` | seit Entscheidung 36 zusätzlich `minimumIncome` (`Emin(M)`, R23), `unplannedMinimum` und `unplannedMinimumLabel` (R4 mit `Emin(M)` als Basis); alle drei leer ohne `Ē(M)` | 200, 400 |
| GET | `/categories?month=2026-10` | seit Entscheidung 36 zusätzlich `fixedCosts` (`F(k, M)`, R25; `null` ohne Serie) und `fixedCostItems` (je Serie `{recurringId, description, amount, interval, monthly}`, `amount` als Betrag ohne Vorzeichen, `monthly` = Monatsanteil) | 200, 400 |
| POST/PUT | `/categories`, `/categories/{id}` | Body zusätzlich `icon` (optional, Vorgabe bzw. bisheriger Wert bleibt) (US28) | wie oben, 400 bei unbekanntem Schlüssel |

**Bank-Import ab 07.11.2026 (Abschnitt 18):**

| Methode | Pfad | Zweck | Erfolg |
|---|---|---|---|
| GET | `/bank-accounts` | eigene Bankkonten `{id, name, iban, defaultAccount, archived, used}`, Standardkonto zuerst, dann nach Name | 200 |
| POST | `/bank-accounts` | anlegen `{name, iban?}` | 201, 400, 409 |
| PUT | `/bank-accounts/{id}` | `{name, iban?, archived, defaultAccount}`; `defaultAccount: true` macht das bisherige Standardkonto zu einem normalen | 200, 400, 404, 409 |
| DELETE | `/bank-accounts/{id}` | nur ohne Buchungen (R26) | 204, 404, 409 |
| POST/PUT | `/transactions`, `/transactions/{id}` | Body zusätzlich `accountId` (optional, Vorgabe Standardkonto), `transfer` (optional, Vorgabe `false`; bei `true` muss `categoryId` fehlen, sonst ist sie Pflicht) und `replaces` (optional `{recurringId, date}`, R28) | wie oben, 400 bei fremdem Konto oder nicht zählendem Vorkommen |
| POST/PUT | `/recurring`, `/recurring/{id}` | Body zusätzlich `accountId` (optional, Vorgabe Standardkonto) | wie oben |
| GET | `/transactions?…` | Einträge zusätzlich mit `accountId`, `accountName`, `transfer` und `replaces` (`{recurringId, date}` oder `null`); `categoryId` und `categoryName` sind bei einer Umbuchung `null` | 200 |
| GET | `/booking-suggestion?text=…&amount=-12.30&date=2026-11-03&income=false` | Kategorie-Vorschlag (R29) `{categoryId, certain}` und Treffer (R28) `{recurringId, date, description, amount, categoryId}` oder `null`; `amount` und `date` sind optional, ohne sie gibt es keinen Treffer; das Vorzeichen kommt aus `income`, sonst aus `amount`, sonst Ausgabe | 200 |
| POST | `/imports/preview` | Datei (`multipart/form-data`, Feld `file`) lesen und die Vorschau liefern, ohne etwas zu speichern (R30) | 200, 400 |
| POST | `/imports` | Vorschau übernehmen: Profil, Zuordnung der Quellkonten (`accountId` oder `newAccountName`) und die gewählten Zeilen mit Kategorie bzw. `transfer`, `replaces` und `transferPeerId` | 201, 400 |
| GET | `/imports` | bisherige Importe, neueste zuerst | 200 |
| DELETE | `/imports/{id}` | Import rückgängig (US34) | 204, 404 |

**Import-Vorschau (Kern):**
```json
{
  "profile": "C24", "rows": 3, "newRows": 2, "duplicates": 1, "skipped": 0,
  "sources": [ { "key": "C24:Ausgabenkonto", "name": "Ausgabenkonto", "accountId": 2, "suggestedName": "C24 Ausgabenkonto" } ],
  "entries": [ { "line": 2, "status": "NEW", "reason": null, "fingerprint": "…", "source": "C24:Ausgabenkonto",
                 "date": "2026-11-04", "amount": -2.99, "description": "Online-Shop · Abo", "counterparty": "Online-Shop",
                 "bankCategory": "Freizeit & Unterhaltung › Digitale Produkte", "suggestionKey": "online shop",
                 "categoryId": 8, "certain": false, "transfer": false, "transferCertain": false, "transferPeerId": null,
                 "replaces": null, "duplicateOf": null } ]
}
```
`status` ist `NEW`, `DUPLICATE` (schon importiert), `POSSIBLE_DUPLICATE` (R30, `duplicateOf` mit `{id, date, description}`) oder `SKIPPED` (mit `reason`). `categoryId` ist der Vorschlag (R29), `certain` seine Sicherheit; `replaces` ist der Treffer nach R28 (`{recurringId, date, description, amount, categoryId}`); `transferPeerId` ist eine schon gespeicherte Gegenbuchung (R27).

**Rückblick-Antwort (Kern):**
```json
{
  "from": "2026-01", "to": "2026-09", "months": 9,
  "averageIncome": 3000.00, "averageExpenses": 1026.00, "averageSurplus": 1974.00,
  "total": { "income": 27000.00, "expenses": 9234.00, "surplus": 17766.00 },
  "history": [ { "month": "2026-01", "income": 3000.00, "expenses": 915.00, "surplus": 2085.00,
                 "incomeCategories": [ { "id": 15, "name": "Gehalt/Lohn (netto)", "icon": "wallet", "amount": 3000.00 } ],
                 "expenseCategories": [ { "id": 1, "name": "Wohnen", "icon": "home", "amount": 900.00 }, … ] } ]
}
```
`months` ist `n` (R14); die Ø-Werte und `total` sind `null`, wenn `n` 0 ist. `from` und `to` sind der angefragte Zeitraum, `history` enthält nur die gezählten Monate. (Zahlen aus Fixture F1.)

**Dashboard-Antwort (Kern):**
```json
{
  "month": "2026-10",
  "incomeTotal": 2000.00,
  "averageIncome": 2000.00, "averageExpenses": 1600.00, "averageIncomeMonths": 9,
  "averageIncomeFrom": "2026-01", "averageIncomeTo": "2026-09",
  "expenses": { "target": 1800.00, "actual": 1635.00, "difference": 165.00,
                "recurring": 900.00, "oneOff": 735.00 },
  "unplanned": 200.00, "unplannedLabel": "Nicht verplant",
  "surplus": 365.00, "surplusLabel": "Monatlicher Überschuss",
  "categories":[ { "id": 1, "name": "…", "target": 500.00, "actual": 500.00, "difference": 0.00 } ],
  "income":    [ { "id": 20, "name": "…", "actual": 2000.00 } ]
}
```
`difference` ist die Abweichung zum Soll (R5, R6), `unplanned` sind die nicht verplanten Einnahmen (R4), `averageIncome` und `averageExpenses` sind `Ē(M)` und `Ā(M)` über die Monate `averageIncomeFrom` bis `averageIncomeTo` (R23), `minimumIncome` ist `Emin(M)` über dieselben Monate und `unplannedMinimum` Nicht verplant mit dieser Basis (R4, Entscheidung 36), `expenses.recurring` ist `W` und `expenses.oneOff` ist `A` − `W` (R10). `categories[].actual` ist `I(k)`, `income[].actual` ist `N(k)` (R2, R9). (Zahlen im Beispiel nur zur Form.)

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

**Frontend:** Reiter *Übersicht* · *Transaktionen* · *Planung* · *Auswertung*. *Übersicht* ist das Dashboard (Kacheln Einnahmen, Ausgaben mit "davon wiederkehrend" und "davon einmalig", Überschuss; Budget-Tabelle je Kategorie mit Gesamtzeile; Einnahmen-Liste; alle Werte in Euro, Entscheidung 34), *Transaktionen* (ein Formular für einmalige und wiederkehrende Transaktionen mit "Wiederholen"; Monatsliste inkl. Vorkommen wiederkehrender Transaktionen; beim Bearbeiten oder Löschen eines Vorkommens fragt ein Dialog "Nur dieser Eintrag" oder "Dieser und alle folgenden"; Serien werden nur über ihre Vorkommen bearbeitet), *Planung* (Soll je Kategorie, mit "Nicht verplant", R4), *Auswertung* (Kachel Ø Ausgaben der letzten 12 Monate, darunter der Rückblick: Tabelle mit Einnahmen, Ausgaben und Überschuss je Monat, aufklappbar je Kategorie, mit Zeile Ø; Rücklagen-Topf mit den Kacheln "Monatlich zurücklegen" und "Sollte jetzt angespart sein" und einer Tabelle der seltenen Ausgaben; Vorschau aller Ausgaben über 12 Monate mit Balken je Monat, R14); *Transaktionen* zeigt zusätzlich eine Zeile mit dem monatlich zurückzulegenden und dem angesparten Betrag (Entscheidungen 15 und 16). Das Layout funktioniert ab ca. 360 px Breite: Kacheln untereinander, in Tabellen Beträge ohne Währungszeichen (Einheit im Spaltenkopf), unter 480 px ohne Spalte Soll; unter 600 px wird jede Transaktion als Karte gezeigt (Beschreibung und Betrag, darunter Datum und Kategorie, dann die Aktionen). Datenzugriff mit TanStack Query, API-Typen in `src/api/`, keine Fachlogik im Frontend (nur Anzeige und Formatierung, `Intl.NumberFormat('de-DE')`). Formulare mit einfacher Validierung (Betrag > 0, Pflichtfelder); der Umschalter "Ausgabe / Einnahme" setzt nur das Vorzeichen.

**Ausbau ab 27.10.2026:**
- **Backend:** Spring Security mit Session-Cookie und CSRF-Schutz (Abschnitt 15). Der angemeldete Nutzer kommt im Service aus dem Security-Kontext; jedes Repository fragt mit `owner` ab (R15), Controller reichen keine Nutzer-IDs aus der URL durch. Datenbank: PostgreSQL im Betrieb, H2 für Tests und lokale Entwicklung; Schema nur über Flyway (US11). Weitere reine Rechenklassen ohne Spring: `SplitCalculator` (Anteile nach R17), `BalanceCalculator` (Salden nach R18, nutzt `RecurrenceSchedule` für Vorkommen), `InvestmentProjection` (Prognose nach R19, R20). Die Ausschöpfung des Freistellungsauftrags (R21) ist eine Summe im Service. Auslieferung als ein Docker-Image, in dem Spring Boot das gebaute Frontend ausliefert (Abschnitt 15).
- **Frontend:** Anmeldeseite und Seite für das Annehmen einer Einladung; alle anderen Seiten nur angemeldet (bei 401 zurück zur Anmeldung). Reiter *Übersicht* · *Transaktionen* · *Planung* · *Auswertung* · *Teilen* · *Investment*, dazu ein Menü *Einstellungen* (Kategorien, Personen, Konto, für Admins Einladungen). Im Transaktionsformular öffnet "Teilen" die Aufteilung (US18); die Anteile zeigt das Formular über `/splits/preview`. Diagramme mit Recharts (US13, US20).

**Überarbeitung ab 04.11.2026 (Abschnitt 17, ersetzt die Beschreibung der Oberfläche oben, wo sie abweicht):**
- **Backend:** Neue reine Rechenklasse `IncomeAverage` (R23, Ø und Minimum), genutzt von `DashboardCalculator` (Nicht verplant R4 mit beiden Basen; bis Entscheidung 36 auch Prozent-Soll R3) und `ReserveCalculator` (Ø-Werte R14). Der aktuelle Monat (R7, R14, R23) kommt aus einer injizierten `Clock`, damit Tests ihn festlegen können; die Rechenklassen bekommen ihn als Parameter. Seit Entscheidung 36 dazu die reine Rechenklasse `FixedCosts` (R25), die der Kategorie-Service für `/categories` nutzt.
- **Eingabehilfen im Frontend (Entscheidung 36):** Die Umrechnung von Prozent in Euro und zurück auf der *Planung* ist eine Eingabehilfe, keine Regel. Sie liegt in einer einzigen Funktion mit eigenen Unit-Tests, rechnet in Cent und bekommt die Basis aus `/dashboard`; das Backend kennt nur Euro.
- **Design-Grundlage:** Farben, Abstände, Radien und Schrift als CSS-Variablen an einer Stelle, mit hellem und dunklem Modus nach der Systemeinstellung. Wenige eigene Bausteine in `src/components/ui/` (Knopf in den Varianten primär, sekundär und Gefahr, Karte, Dialog bzw. Fenster von unten, Umschalter, Chip, Fortschrittsbalken, `CategoryIcon`), keine UI-Bibliothek. Farben: Blau für Aktionen und "im Rahmen", Orange als Warnfarbe für "über dem Soll", "Überschritten" und "Überplant", Petrol für Einnahmen; Rot nur für Fehler und Löschen. Bedeutungen unterscheiden sich nie nur über Rot und Grün. Icons als Inline-SVG im Linienstil.
- **Navigation:** Reiter *Übersicht* · *Transaktionen* · *Planung* · *Auswertung* (Desktop oben, Handy unten), Kontomenü mit *Einstellungen* und *Abmelden*; Hash-Routing ohne zusätzliche Abhängigkeit (US29). Später kommen *Teilen* und *Investment* als Reiter dazu, am Handy dann über "Mehr".
- **Seiten:** Jede Seite steuert ihren Zeitbezug selbst (Entscheidung 28). Kein globaler Monatswähler mehr.

## 7. Teststrategie

- **Unit (Pflicht):** `DashboardCalculator` mit mindestens diesen Fällen:
  1. Soll in Euro (R3)
  2. Eintrag mit leerem Betrag → kein Soll ab M, auch wenn ein älterer Eintrag einen Betrag hat (R3)
  3. Nicht verplant mit beiden Basen `Ē(M)` und `Emin(M)` (R4; ersetzt seit Entscheidung 36 die Fälle zum Prozent-Soll)
  4. Nicht verplant = `E` − `P`, negativ bei Überplanung und dann mit Bezeichnung "Überplant"; Soll gesamt enthält es nicht (R4, R6)
  5. Einnahmen = 0 → keine Division durch null, Einnahmen-Liste leer (R9)
  6. Vorkommen einer wiederkehrenden Transaktion zählt mit vollem Betrag im Monat des Vorkommens, in anderen Monaten nicht (R1)
  7. Überschuss negativ → Bezeichnung "Monatlicher Verlust" (R7)
  8. Soll ab M gilt nicht für M−1, aber für M und M+1; ein neuerer Eintrag ersetzt den älteren (R3, Auflösung in `TargetResolver`)
  9. Vorzeichen (R2, R9, R11): Erstattung mindert das Ist ihrer Kategorie; eine Kategorie mit Soll und positivem Netto zählt als Einnahme (Ist 0, Abweichung = Soll)
  10. Budget-Tabelle ohne Kategorien ohne Soll und ohne Ausgaben, alphabetisch (R10, Entscheidung 38)
  11. Wiederkehrende Ausgaben (R10): Vorkommen mit negativem Betrag zählen zu `W`, einzelne Transaktionen und Vorkommen mit positivem Betrag nicht; eine Erstattung in derselben Kategorie mindert zuerst den einmaligen Teil; übersteigt sie die einmaligen Ausgaben der Kategorie, sinkt `W(k)` auf `I(k)`; `W` + einmalige Ausgaben = `A`
- **Unit:** `RecurrenceSchedule` (R12): je Intervall die richtigen Vorkommen; Tag 31 und 29.02. auf den Monatsletzten; Enddatum einschließlich; Ausnahmen werden ausgelassen; kein Vorkommen vor dem Startdatum.
- **Unit:** `ReserveCalculator` (R14): Ø Ausgaben, Ø Einnahmen und Ø Überschuss über 12 Monate, über weniger Monate ab dem ersten Eintrag und leer ohne abgeschlossenen Monat; Rückblick mit denselben Monaten, fehlende Monate mit 0; Topf nur für Intervall > 1, angespart vor, im und nach dem Fälligkeitsmonat, Ausnahme auf dem nächsten Vorkommen, beendete Serie; Vorschau über 12 Monate mit einzelnen Ausgaben, ohne Einnahmen.
- **Integration:** `@SpringBootTest` + MockMvc für Transaktion anlegen → Dashboard spiegelt es wider; Validierungsfehler (400, auch Betrag 0); Serie anlegen → Vorkommen in `/transactions` und `/dashboard`; R13 "Nur dieser Eintrag" (Ausnahme + Ersatz-Transaktion) und "Dieser und alle folgenden" (erstes Vorkommen: Serie geändert; späteres Vorkommen: Serie geteilt, frühere Monate unverändert); unbekanntes Vorkommen → 404.
- **Frontend:** 2 bis 3 Vitest/Testing-Library-Tests (Formular-Validierung mit Umschalter, Dashboard rendert Werte inkl. "davon wiederkehrend / einmalig", Planung zeigt "Nicht verplant", Formular mit "Wiederholen", Dialog für Bearbeiten eines Vorkommens).
- **Referenzabgleich:** Eine Testfixture mit erfundenen Zahlen (Abschnitt 12).
- **Ausbau ab 27.10.2026:**
  - `ReserveCalculator` (R14): Zeitraum 3, 6, 12 und 24 Monate, jeder frühestens ab dem ersten Eintrag; Grenzen 1 und 120 (0 und 121 → 400); Fixture F1 bleibt mit Vorgabe 12 gültig; Gesamt leer ohne abgeschlossenen Monat.
  - `SplitCalculator` (R17): Beträge, gleichmäßig mit Rundungsrest bei mir, Prozent; Summe ≠ `G`, negativer Anteil, mein Anteil 0 → Fehler.
  - `BalanceCalculator` (R18): Fixture F2; Vorkommen nach heute zählen nicht; Ausnahmen; Einträge, die eine Person bezahlt hat; Ausgleich in beide Richtungen.
  - `InvestmentProjection` (R19, R20): Fixture F3 (wird mit AP 21 aus dem Investment-Blatt erstellt); Rendite 0 ergibt Startwert + Einzahlungen; Auszahlungen mindern den Wert.
  - **Integration Mandantentrennung (R15):** Für jeden Endpunkt sieht Nutzer B die Daten von Nutzer A nicht (404 in der URL, 400 als Verweis); ohne Anmeldung 401; ohne CSRF-Token 403.
  - **Integration Benutzer:** Einladung annehmen (einmalig, abgelaufen → 410), Anmeldung mit Sperre nach Fehlversuchen, Konto löschen entfernt alle Daten.
  - **Migrationen:** Die Tests laufen gegen das Schema aus Flyway, nicht gegen ein von Hibernate erzeugtes.
  - **Update-Test (CI, jeder PR):** Der Stand des Ziel-Branches startet mit Docker Compose und bekommt erfundene Testdaten über die API (Person A mit Soll, Transaktionen und Serien, Person B über eine Einladung); danach wird mit demselben Datenbank-Volume auf den Stand des PRs aktualisiert. Die App muss starten, und Dashboard, Monatsliste und Rücklagen beider Nutzer müssen unverändert sein (`scripts/update-test.mjs`). Das fängt ab, was Tests auf einer leeren Datenbank nicht sehen: eine Migration, die an vorhandenen Daten scheitert, oder ein neues Major-Release von PostgreSQL, das das alte Datenverzeichnis nicht lesen kann.

- **Überarbeitung ab 04.11.2026:**
  - `IncomeAverage` (R23): 12 Monate, weniger Monate ab dem ersten Eintrag, `n` = 0 → leer, Monate ab dem aktuellen Monat zählen nicht (auch für M in der Zukunft), Monate ohne Einnahmen zählen mit 0.
  - `IncomeAverage` (seit Entscheidung 36): Minimum über dieselben Monate wie der Ø, Monat ohne Einnahmen zählt mit 0, leer bei `n` = 0.
  - `FixedCosts` (R25, Tests zuerst): monatlich, vierteljährlich und jährlich als Monatsanteil; Summe nur am Ende gerundet (55,08 / 3 = 18,36); Serie beginnt nach dem letzten Tag von M oder endet davor → zählt nicht; geteilte Serie (R13) zählt nur mit dem neuen Teil; Serien mit positivem Betrag zählen nicht; Ausnahmen ändern nichts; ohne Serie kein Wert.
  - `DashboardCalculator`: Soll nur in Euro (Entscheidung 36); `Ē(M)` leer → Euro-Soll bleibt; Nicht verplant aus `Ē(M)` und aus `Emin(M)`; `Ā(M)` über dieselben Monate wie `Ē(M)`, leer ohne gezählten Monat; Bezeichnungen nach R7 für vergangenen, aktuellen und künftigen Monat; Fixture F1 mit den neuen Werten.
  - `ReserveCalculator`: Rückblick von `V` bis `B` mit Grenzen (`V` = `B`, 120 Monate, Monate vor dem ersten Eintrag), Vorausschau ab einem übergebenen Monat wie bisher.
  - Integration: `/transactions?from&to` mit Vorkommen an den Grenzen und Ausnahmen, 400-Fälle; `/history` mit Vorgabe und 400-Fällen; `incomeOnly` (R24) mit Transaktionen und Serien; Icon anlegen, ändern, unbekannter Schlüssel → 400; Migration setzt die Icons bestehender Standard-Kategorien. Mandantentrennung (R15) auch für die neuen Endpunkte. Seit Entscheidung 36: `fixedCosts` und `fixedCostItems` in `/categories` (nur eigene Serien, R15); PUT target nur mit `targetAmount`; Migration V6 entfernt `target_percent`, ein Eintrag nur mit Prozent gilt danach als kein Soll.
  - Frontend: Navigation mit Adressen und Zurück; Buchungsdialog (Vorzeichen, Pflichtfelder, Dialog für Vorkommen); Planung mit Umschalter € / % (Umrechnung in Cent als Unit-Test, gespeichert wird Euro, Gegenwert unter dem Feld, % ohne Basis gesperrt), Basis per Klick auf die Kachel, fixe Kosten auf- und zuklappen, "übernehmen", Warnung unter den fixen Kosten, "Gespeichert ab …" nach dem Speichern, und ausgeblendeten Kategorien; Rückblick mit aufgeklapptem Monat; Übersicht mit "Noch frei", "über dem Soll" und den Ø-Angaben für Einnahmen und Ausgaben.
  - Update-Test: Werte, die sich durch Entscheidung 26 gewollt ändern (Soll gesamt bei Prozent-Soll), werden im PR von AP 19c begründet; das Skript vergleicht dann nur, was die Änderung nicht betrifft. Seit Entscheidung 36 legt das Skript nur Euro-Soll an; was die Migration V6 mit einem Prozent-Soll macht, prüft ein eigener Migrationstest.
  - Beispieldaten (Entscheidung 35): `SampleDataSeeder` legt auf einer leeren Datenbank Person A bis D mit ihren Rollen an, ein zweiter Lauf ändert nichts; die Daten liegen von 2025-01 bis 2026-10 und ergeben im Dashboard die erwarteten Werte.

## 8. CI/CD und Git

- GitHub Actions bei Push und PR: Backend `mvn -B verify`; Frontend `npm ci`, `npm run lint`, `npm test`, `npm run build`.
- `main` ist geschützt (PR nötig, Checks `backend` und `frontend` grün, kein Force-Push, kein Löschen). Je Feature ein Branch `feat/<name>` und ein PR mit Kurzbeschreibung: Ziel, Spec-Bezug (z. B. US3), was die KI erzeugt hat, was ich geändert habe.
- Commits in kleinen Schritten, aussagekräftig (Conventional Commits).
- Ab AP 19: CI baut zusätzlich das Docker-Image und spielt bei jedem PR ein Update mit Testdaten durch (Update-Test, Abschnitt 7); Dependabot hält Maven, npm, GitHub Actions und die Docker-Images aktuell, mit einem gemeinsamen PR für kleine Updates je Ökosystem. Major-Versionen von Java, Node und PostgreSQL schlägt Dependabot nicht vor; sie werden bewusst umgestellt, PostgreSQL mit Dump und Restore. Ein Deployment aus der CI kommt erst, wenn die Plattform feststeht (Entscheidung 21).

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
23. **Verlauf als Erweiterung der Auswertung:** Der Rückblick der Auswertung (R14) zeigt Einnahmen und Ausgaben schon je Monat. Statt einer zweiten Auswertung wird sein Zeitraum wählbar (3, 6, 12 oder 24 Monate) und ein Diagramm kommt dazu. So gibt es nur eine Rechenlogik, und die Zahlen stimmen mit der Übersicht überein. Die API nimmt jeden Zeitraum von 1 bis 120 Monaten an (31.10.2026), weil die Regel für jeden Zeitraum gleich ist; die feste Auswahl ist reine Anzeige und kann wachsen, ohne das Backend zu ändern. Die Obergrenze verhindert, dass ein einzelner Aufruf beliebig viele Monate berechnet. *Die feste Auswahl ersetzt Entscheidung 29 durch einen Zeitraum von Monat bis Monat.*
24. **Investment in vier Stufen, Sparplan = wiederkehrende Transaktion:** Der Investment-Teil kommt in vier Arbeitspaketen (Sparplan und Prognose, Einmalzahlungen, Freistellungsauftrag, Depot mit Positionen), jeweils erst nach der Analyse der Formeln des passenden Sheet-Blatts. Ein Sparplan ist eine wiederkehrende Transaktion, die mit einem Depot verknüpft ist (passt zu Entscheidung 17); so wird er nur einmal erfasst und zählt im Budget wie jede andere Ausgabe. Die App gibt keine Renditen vor und keine Anlageempfehlung; Pauschbeträge sind Einstellungen, weil sich die Rechtslage ändern kann.
25. **Auswertung kompakter und in Prozent:** Der Rückblick bekommt den Umschalter € / % aus dem Dashboard, bezogen auf die Einnahmen des jeweiligen Monats; die Anteile rechnet das Backend (wie R8). Rücklagen und Vorschau bleiben in €, weil ihnen eine Einnahmen-Basis fehlt. Eine Zeile Gesamt zeigt die Summen des Zeitraums. Rückblick und Vorschau sind einklappbar, damit die lange Seite übersichtlich bleibt; der Zustand gilt je Browser. *Das Einklappen ersetzt Entscheidung 29 durch zwei Unterseiten; den Umschalter € / % ersetzt Entscheidung 34.*

Geklärt am 04.11.2026 (Überarbeitung, nach einem klickbaren Entwurf):

26. **Soll-Basis sind die Ø Einnahmen:** Ein Prozent-Soll und "Nicht verplant" rechnen mit dem Ø der Einnahmen der 12 Monate vor M (R23), nicht mit den Einnahmen des Monats. Der laufende Monat ist oft unvollständig (das Gehalt kommt erst später), und einzelne Monate schwanken (Bonus, Erstattungen); geplant wird aber mit dem, was üblicherweise reinkommt. Der Ø ist rollierend: Jeder Monat hat seine eigene Basis, vergangene Monate bleiben stabil. Es zählen nur Monate vor dem aktuellen Monat, damit ein unfertiger oder künftiger Monat den Ø nicht verfälscht. Ohne einen solchen Monat hat ein Prozent-Soll keinen Wert. (Der Umschalter € / %, der hier auf die Einnahmen des Monats bezogen blieb, entfällt mit Entscheidung 34.) Ersetzt den Teil von Entscheidung 12, nach dem Überschuss = Nicht verplant + Abweichung gilt.
27. **"Noch frei" statt Überschuss im laufenden Monat:** Für einen abgeschlossenen Monat ist "Monatlicher Überschuss" die richtige Frage. Im laufenden und in künftigen Monaten will man wissen, was nach allem bisher Gebuchten noch übrig ist; das heißt dort "Noch frei" bzw. "Überschritten" (R7). Auch für künftige Monate, weil dort nur Serien und vorgemerkte Einträge stehen und "voraussichtlich" eine Genauigkeit vortäuschen würde.
28. **Jede Seite hat ihren eigenen Zeitbezug:** Ein Monatswähler für alle Seiten passt nicht: Die *Übersicht* zeigt einen Monat, *Transaktionen* einen frei wählbaren Zeitraum (Vorgabe der aktuelle Monat), die *Planung* nur, ab wann ein Soll gilt, und die *Auswertung* hat den heutigen Monat als Nullpunkt. Die Seite steht in der Adresse, damit Neuladen und Zurück funktionieren. *Einstellungen* braucht man selten; es wandert ins Kontomenü, damit am Handy vier Reiter mit lesbarer Schrift in die Leiste passen. "+ Buchung" gibt es nur auf *Übersicht* und *Transaktionen*, weil man dort bucht; am Handy als Knopf unten rechts.
29. **Auswertung in Rückblick und Vorausschau:** Der Rückblick schaut zurück und hat einen frei wählbaren Zeitraum von Monat bis Monat (statt fester 3, 6, 12 oder 24 Monate); damit ist auch eine Jahresbilanz möglich. Rücklagen-Topf und Vorschau schauen nach vorn und beginnen immer im aktuellen Monat. Zwei Unterseiten statt einer langen, einklappbaren Seite. *Die Anzeige in % (Einnahmen im Verhältnis zum Ø) entfällt mit Entscheidung 34.*
30. **Liste zuerst, Buchen im Dialog:** Auf *Transaktionen* sucht man meistens etwas; das Formular stand über der Liste und schob sie nach unten. Jetzt steht die Liste oben, nach Tagen gruppiert und filterbar (US26), und gebucht wird in einem Dialog (US27). Ein Klick auf eine Zeile bearbeitet sie; so entfallen zwei Knöpfe je Zeile.
31. **Icons für Kategorien:** Icons machen Listen schneller lesbar. Gespeichert wird nur ein Schlüssel aus einer festen Liste, kein Bild, damit Icons zum Design passen und keine Uploads nötig sind (US28).
32. **Eigene Design-Grundlage statt UI-Bibliothek:** Die App braucht wenige Bausteine; eine Bibliothek brächte mehr Abhängigkeiten und Anpassungsaufwand, als sie spart (passt zu "Keep it simple"). Blau und Orange statt Grün und Rot, damit die App auch bei Rot-Grün-Schwäche lesbar ist.
33. **Planung ohne reine Einnahmen-Kategorien:** Kategorien haben keinen Typ (Entscheidung 9), deshalb listete die *Planung* auch "Gehalt/Lohn". Ein Soll ist aber immer ein Ausgabenbudget (R3). Die *Planung* blendet deshalb Kategorien aus, die bisher nur positive Einträge haben (R24); Kategorien ohne Einträge bleiben sichtbar, weil noch offen ist, wofür sie genutzt werden.

Geklärt am 05.11.2026 (nach der manuellen Prüfung von AP 19e):

34. **Nur Euro, Ø auch für Ausgaben:** Der Umschalter € / % in *Übersicht* und *Rückblick* entfällt samt der Anteile in der API (R8, R14). Prozentwerte, die sich auf die Einnahmen eines oft noch unvollständigen Monats beziehen, waren schwer zu lesen, und eine Ansicht reicht. Damit man den laufenden Monat trotzdem einordnen kann, steht unter der Kachel Ausgaben wie bei den Einnahmen der Ø der Monate vor M (`Ā(M)`, R23), über denselben Zeitraum. "Überschritten" zeigt den Betrag mit Minuszeichen in der Warnfarbe (R7), weil das Vorzeichen auf einen Blick sagt, in welche Richtung der Monat kippt. Das Eingabefeld € / % für ein Soll auf der *Planung* (US2) bleibt, weil ein Prozent-Soll eine Planungsregel ist (R3). Ersetzt die Umschalter aus den Entscheidungen 13, 25, 26 und 29.

Geklärt am 06.11.2026 (lokale Entwicklung):

35. **Feste lokale Datenbanken `dev` und `sample`:** Zum Entwickeln und manuellen Testen braucht es immer gleiche Logins und, je nach Fall, eine leere oder eine gefüllte Datenbank. Lokal gibt es deshalb zwei Spring-Profile mit je einer H2-Datei: `dev` (Standard ohne Profil, nur der Systemadmin *Admin*, keine Buchungen) und `sample` (*Admin* und Person A bis D mit erfundenen Daten von Januar 2025 bis Oktober 2026). Die Dateien bleiben zwischen zwei Starts erhalten, damit neue Migrationen wie im Betrieb auf vorhandene Daten treffen; `scripts/reset-db.sh dev|sample` löscht genau eine davon. Die Beispieldaten legt beim ersten Start eine Klasse über die Services an, nicht SQL, damit alle Regeln und Validierungen gelten und kein Skript bei Schemaänderungen nachgezogen werden muss. Die Logins sind erfunden und stehen nur in den Profildateien, nie in `application.properties`, weil diese Datei auch im Betrieb gilt. Eine fertige Datenbankdatei im Repo scheidet aus (binär, veraltet mit jeder Migration, nicht prüfbar). Der Name `test` bleibt den automatischen Tests vorbehalten.

Geklärt am 06.11.2026 (Prüfung von AP 19f, nach einem überarbeiteten klickbaren Entwurf):

36. **Soll nur in Euro, Prozent als Eingabehilfe, wählbare Basis, fixe Kosten in der Planung:** Ein Prozent-Soll änderte sich jeden Monat von selbst, weil der Ø der Einnahmen rollt (R23); das verwirrt beim Planen. Gespeichert wird deshalb nur noch ein Euro-Betrag (R3). Wer lieber in Prozent denkt, gibt Prozent ein, und das Frontend rechnet vor dem Speichern um. Die Umrechnung ist eine Eingabehilfe wie ein Taschenrechner und keine Geschäftsregel; eine zweite Form der API nur dafür wäre unnötig, das Backend bleibt bei einem Wert und prüft ihn. Unter dem Feld steht immer der andere Wert, damit man beides sieht. Neben dem Ø gibt es die Mindesteinnahmen (kleinstes `E` derselben Monate), weil vorsichtige Planung mit dem schwächsten Monat rechnet. Welcher Wert Basis für "Nicht verplant" und die Eingabe in Prozent ist, wählt man per Klick auf die Kachel; vorgewählt sind die Mindesteinnahmen, und ihre Kachel steht vor der des Ø (nach dem ersten Test am 06.11.2026); die Wahl gilt nur für die *Planung* und wird im Browser gemerkt, denn ohne gespeichertes Prozent-Soll hängt sonst nichts davon ab. Den Monat mit dem Minimum nennt die App nicht, weil es oft mehrere sind. Die fixen Kosten einer Kategorie (R25) zeigen beim Planen, womit man sicher rechnen muss, etwa mit der Miete; nicht monatliche Serien zählen mit ihrem Monatsanteil, weil das Soll jeden Monat gilt, und "übernehmen" setzt das Soll auf diesen Betrag. Die Liste der Serien ist zugeklappt, damit die Seite kurz bleibt. Vorhandene Prozent-Soll werden nicht umgerechnet, weil dafür der Ø zum jeweiligen Monat nötig wäre und noch niemand die App ernsthaft nutzt: Die Migration V6 entfernt die Spalte, ein Eintrag nur mit Prozent gilt danach als kein Soll. Ersetzt den Teil von Entscheidung 26, nach dem ein Prozent-Soll mit dem Ø rechnet, und den Satz zum Eingabefeld in Entscheidung 34.

37. **Rücklagen erklären** (06.11.2026, nach dem Test von AP 19i): Woher "Angespart" kommt und dass "Sollte jetzt angespart sein" ein Sollwert ist und kein Kontostand, war auf der *Vorausschau* nicht zu erkennen. Ein Diagramm sollte nicht zwei Fragen zugleich beantworten; die Seite trennt deshalb "Wie weit bin ich mit dem Zurücklegen?" (Rücklagen) und "Wann wird es teuer, und was davon ist schon zurückgelegt?" (Vorschau). Die Rücklagen beginnen mit einem Satz, der Rate, Anzahl und die nächste seltene Ausgabe nennt; nur die nächste, weil der Satz bei vielen Ausgaben sonst zu lang wird und die Karten darunter jede einzeln zeigen. Statt einer Tabelle zeigt je Ausgabe ein Fortschrittsbalken, wie viel davon zurückgelegt sein sollte und wie viele Raten noch fehlen; "Sollte zurückgelegt sein" ist die Summe dieser Balken und erklärt sich damit ohne Formel. Die Begriffe sind einfacher ("Jeden Monat beiseitelegen", "Sollte zurückgelegt sein", "aus den Rücklagen" statt "Topf"). Die Vorschau ist aufgebaut wie der Rückblick (Diagramm und Tabelle verknüpft, aufklappbare Monate) und färbt nur den Teil der Ausgaben blau, der aus den Rücklagen bezahlt wird. Ein Info-Symbol (?) an den Überschriften und Kacheln erklärt bei Bedarf mehr; an der Überschrift Rücklagen steht der allgemeine Zweck. Fehlende Raten und den Teil aus den Rücklagen rechnet das Backend (R14); das Frontend wählt für den Satz nur die erste Ausgabe der nach Fälligkeit sortierten Liste. Verworfen nach Test und Mockup (06.11.2026): ein Verlauf des Soll-Stands als Linie, auch auf einer logarithmischen Achse oder als eigenes Diagramm, und gestapelte Balken "laufende Ausgaben + Rate" (wirkten komplexer statt einfacher), ein Satz mit der nächsten und der teuersten Ausgabe.
38. **Kategorien alphabetisch** (07.11.2026, nach dem Test von AP 19j): Eine eigene Reihenfolge zu pflegen (Ziehen, "Nach oben" / "Nach unten") ist Aufwand ohne großen Nutzen; alphabetisch findet man eine Kategorie in jeder Liste ohne Nachdenken. Deshalb sind Kategorien überall alphabetisch sortiert: Einstellungen, Planung, Budget-Liste der Übersicht, Buchungsdialog und Rückblick. Sortiert wird im Backend mit deutscher Sortierung (Umlaute wie ihr Grundbuchstabe, ohne Groß- und Kleinschreibung, bei Gleichstand nach `id`), nicht in der Datenbank, weil H2 und PostgreSQL Text verschieden sortieren. `sortOrder` entfällt in der API und im Datenexport; die Spalte `sort_order` bleibt ungenutzt, damit keine gemergte Migration geändert werden muss. Eine neue Kategorie bekommt beim Anlegen gleich ihr Icon.

39. **CSV-Import mit festen Profilen, keine Bank-API:** CSV-Dateien der Banken sind nicht genormt; Spalten, Zahlen- und Datumsformat, Vorspann und Zeichensatz unterscheiden sich je Bank. Die App liest deshalb feste Profile für die Banken, die tatsächlich genutzt werden (C24, Trade Republic, N26), erkannt an der Kopfzeile; eine freie Zuordnung der Spalten kann später kommen. Eine direkte Anbindung (PSD2 über einen Anbieter oder FinTS) bleibt vorerst draußen: Sie braucht einen Vertrag mit einem Anbieter oder eine Registrierung, eine Zustimmung je Nutzer alle 90 bis 180 Tage und eine sichere Ablage von Tokens, und alles nach dem Lesen (Kategorien, Duplikate, Serien, Umbuchungen) ist dasselbe wie beim CSV-Import. Der Import ist so gebaut, dass eine spätere Quelle nur die Zeilen liefern muss. Vorgemerkte Umsätze werden übersprungen, weil sie sich noch ändern; es zählt der Buchungstag. Die Datei wird nicht gespeichert, die Buchungen nur mit Gegenseite und Bankkategorie (für R29), ohne IBAN der Gegenseite, damit so wenig Bankdaten wie nötig liegen bleiben. (07.11.2026)
40. **Bankkonten:** Wer mehrere Konten importiert, braucht die Herkunft jeder Buchung, sonst lassen sich Umbuchungen nicht erkennen. Jede Transaktion und Serie gehört deshalb zu einem Konto (R26). Bestehende Daten kommen per Migration auf ein Konto "Girokonto", das Standardkonto; die API nimmt das Konto optional an, damit bestehende Aufrufe weiter funktionieren. Einen Kontostand führt die App nicht, weil ihr der Anfangsbestand fehlt und das Budget nicht davon abhängt. Die Seite heißt *Bankkonten*, weil *Konto* in den Einstellungen schon das Nutzerkonto ist. (07.11.2026)
41. **Umbuchungen zählen nicht:** Geld zwischen eigenen Konten (Girokonto → Depot-Verrechnungskonto, Kreditkartenabrechnung) ist weder Einnahme noch Ausgabe. Als Kategorie mit ±0 würden beide Kacheln der *Übersicht* größer; eine Umbuchung hat deshalb keine Kategorie und fehlt in allen Rechnungen (R27, Wahl des Nutzers am 07.11.2026). Die beiden Seiten werden nicht verknüpft, weil jede für sich richtig ist und eine Verknüpfung beim Import nur eine zweite Fehlerquelle wäre. (07.11.2026)
42. **Kategorie-Vorschlag aus dem Verlauf:** Bankdaten haben keine (oder eine fremde) Kategorie. Ein Vorschlag aus den eigenen bisherigen Buchungen lernt mit, ohne dass jemand Regeln pflegt, und hilft auch im Buchungsdialog (Wunsch des Nutzers). Sicher ist er nur, wenn die letzten Buchungen mit demselben Empfänger einig sind; unsichere Zeilen kategorisiert der Nutzer selbst, mit dem Vorschlag als Hilfe. Feste Stichwort-Regeln pflegen kann man später ergänzen. (07.11.2026)
43. **Ist ersetzt Plan:** Eine Serie (etwa Miete oder Strom) und ihre Bankbuchung würden sonst doppelt zählen. Die Bankbuchung ersetzt das Vorkommen, weil sie den echten Betrag hat (z. B. schwankender Strom), und sie bleibt für R10 wiederkehrend (Wahl des Nutzers am 07.11.2026). Technisch ist das die vorhandene Ausnahme (R13) mit einem Verweis auf die Buchung, damit Löschen das Vorkommen zurückbringt. Der Treffer verlangt ein gemeinsames Wort oder den gleichen Betrag mit passender Kategorie, damit zwei Abos mit gleichem Preis nicht vertauscht werden. Dieselbe Erkennung hilft im Buchungsdialog. Neue Serien aus wiederkehrenden Bankbuchungen vorschlagen kann später kommen. (07.11.2026)
44. **Wertpapiere als Ausgabe:** Ein Kauf bei Trade Republic ist eine Ausgabe (Vorschlag "Investitionen"), ein Verkauf eine Einnahme, Zinsen und Dividenden sind Einnahmen (Vorschlag "Kapitalerträge"). Das passt zu den Sparplänen als Serien (US20) und zum Sheet, in dem Sparen eine Kategorie ist (Wahl des Nutzers am 07.11.2026). Die Überweisung vom Girokonto auf das Verrechnungskonto ist dann eine Umbuchung. (07.11.2026)
45. **Vorschau ohne Zwischenspeicher, Rückgängig je Import:** Die Vorschau liest die Datei und speichert nichts; zum Übernehmen schickt das Frontend die gewählten Zeilen zurück, und das Backend prüft sie wie jede Buchung (Betrag, Länge, Kategorie, Konto, Vorkommen, R15). So bleibt keine halbe Datei liegen, wenn jemand die Seite verlässt. Jede Buchung merkt sich ihren Import, damit ein Fehlgriff mit einem Klick rückgängig ist; der Fingerabdruck verhindert, dass dieselbe Zeile zweimal angelegt wird, auch bei überlappenden Zeiträumen. (07.11.2026)
46. **Normalisierte Bankbuchung nach ISO 20022, Serien im Import, nichts vorbelegen** (07.11.2026, nach dem zweiten Test): Jedes Profil übersetzt eine Zeile in einen einheitlichen Datensatz mit den Feldern, die CAMT.053 und die PSD2-Schnittstellen (Berlin Group) für eine Buchung verwenden: Konto (Name, IBAN), Buchungstag, Wertstellung, Betrag mit Vorzeichen, Währung, Status gebucht oder vorgemerkt, Gegenseite (Name, IBAN, BIC), Verwendungszweck, End-to-End-, Mandats- und Gläubiger-ID, Art (Geschäftsvorfall) und ID der Bank; Bankeigenes wie die C24-Kategorie steht in einem Zusatzfeld. Danach weiß keine Regel mehr, woher eine Zeile kommt; ein späterer Import von CAMT.053 oder eine Bank-API wird nur ein weiterer Adapter. Verworfen: die Zeilen erst in CAMT.053-XML umzuwandeln (Umweg ohne Gewinn, CSV-Exporte haben keine Salden) und sie vor dem Übernehmen zu speichern (ungebuchte Bankdaten auf dem Server, siehe Entscheidung 45). Weil eine Regel zur Art nur zu einer Bank passt, liefert das Profil die Art-Hinweise (R29), und der Fingerabdruck nutzt bei jeder Bank die ID, sonst die Prüfsumme (R30). Monatliche Serien erkennt der Import selbst (R31), weil man beim Import am ehesten merkt, dass etwas regelmäßig kommt; ein Gehalt, das je nach Wochenende einige Tage früher oder später kommt, bleibt dieselbe Serie, und die Abweichungen werden über "Ist ersetzt Plan" (R28) zu Ausnahmen. Ein Vorschlag wird nie vorbelegt, auch kein sicherer, weil das verständlicher ist (Wunsch des Nutzers); "Alle Vorschläge übernehmen" spart trotzdem Klicks. Für große Importe genügt "Fertige übernehmen": Der Fingerabdruck sorgt dafür, dass eine erneut hochgeladene Datei nur noch die offenen Zeilen zeigt.
47. **Sparkasse: ein Profil für vier Exporte** (07.11.2026): Das Online-Banking der Sparkasse bietet CSV-CAMT V2, CSV-CAMT V8, CSV-MT940 und CSV - mit Kategorien. Alle haben dieselbe Form (Semikolon, jedes Feld in Anführungszeichen, `dd.MM.yy`, deutsches Zahlenformat, eine Gutschrift ohne Vorzeichen); V2 und V8 sind gleich, "mit Kategorien" hat die Spalte Kategorie mehr, und CSV-MT940 trägt Mandat, Gläubiger-ID und End-to-End als SEPA-Kennungen im Verwendungszweck. Ein Profil, das sich nach den vorhandenen Spalten richtet, liest alle vier. Verworfen: je ein Profil pro Export (dieselbe Bank, mehr Code, und Quellkonto und Fingerabdruck müssten trotzdem für alle gleich sein) und eine Klassenhierarchie der Profile (Umbau aller Profile ohne Gewinn). Der Fingerabdruck lässt bei der Sparkasse den Zweck weg, weil CSV-MT940 ihn verändert; sonst käme eine Buchung doppelt, sobald man den Export wechselt. Vorgemerkte Umsätze lagen in keiner Beispieldatei vor: Die App nimmt "vorgemerkt" in `Info` oder einen leeren Buchungstag an und liest eine Zeile mit einem anderen unbekannten Wert in `Info` nicht, damit nichts Unfertiges gebucht wird (Abschnitt 16). Die Kategorien der Sparkasse sind nur Bankkategorie (R29); Art-Hinweise gibt es keine, weil sie nicht zu den Kategorien der App passen.

## 12. Testfixtures F1, F2 und Sonderfall Einnahmen = 0

Alle Zahlen sind erfunden und durchgerechnet. Sie dienen als End-to-End-Test für `DashboardCalculator` und `ReserveCalculator` (ergänzend zu den Fällen aus Abschnitt 7).

**Monat:** 2026-10 · **Heute:** 2026-10-20 (aktueller Monat 2026-10, R7) · **Einnahmen:** Gehalt/Lohn (netto) +3000,00 (Vorkommen der Serie, s. u.) · Nebenjob +200,00 → `E` = 3200,00

*Seit Entscheidung 26 (04.11.2026) ist das Gehalt eine monatliche Serie ab 2026-01, damit `Ē` einen Wert hat; vorher war es eine einzelne Transaktion im Oktober. Geänderte Ergebnisse: Prozent-Soll, Soll gesamt, Abweichung, Nicht verplant, Anteile des Solls und die Einnahmen in der Auswertung.*

**Soll-Einstellungen (gültig ab 2026-10, R3):**

*Seit Entscheidung 36 (06.11.2026) gibt es nur Euro-Soll. Die früheren Prozent-Soll (Nebenkosten 10 %, Freizeit 5 %, Investitionen 20 % von `Ē` = 3000,00; Mobilität zusätzlich 50 %) stehen hier mit dem Euro-Wert, den sie hatten; alle Ergebnisse bleiben deshalb gleich.*

| Kategorie | Einstellung |
|---|---|
| Wohnen | 900,00 € |
| Nebenkosten | 300,00 € |
| Versicherungen | 100,00 € |
| Mobilität | 60,00 € |
| Lebensmittel | 400,00 € |
| Freizeit | 150,00 € |
| Investitionen | 600,00 € |

**Transaktionen im Oktober:** Nebenjob +200,00 · Nebenkosten −200,00 und −140,00 · Lebensmittel −120,30, −200,20 und −130,00 · Erstattung Lebensmittel +30,00 · Mobilität −45,99 · Investitionen −640,00 · Geschenke −20,00 (ohne Soll)

**Wiederkehrende Transaktionen:**
- Gehalt/Lohn (netto) +3000,00, monatlich ab 2026-01-01, unbefristet (Vorkommen 2026-10-01; positive Vorkommen zählen nicht zu `W`, R10)
- Wohnen −900,00, monatlich ab 2026-01-01, unbefristet (Vorkommen 2026-10-01)
- Versicherungen −240,00, jährlich ab 2026-10-15, unbefristet (Vorkommen 2026-10-15, nächstes 2027-10-15)
- Freizeit −15,00, monatlich ab 2026-01-05, unbefristet, mit Ausnahme für 2026-10-05 ("Nur dieser Eintrag" gelöscht)

**Muss ignoriert werden:** Lebensmittel −999,00 am 2026-09-30

**Erwartete Ergebnisse Dashboard:**

| Kategorie | Soll | Ist | Abweichung |
|---|---|---|---|
| Wohnen | 900,00 | 900,00 | 0,00 |
| Nebenkosten | 300,00 | 340,00 | -40,00 |
| Versicherungen | 100,00 | 240,00 | -140,00 |
| Mobilität | 60,00 | 45,99 | 14,01 |
| Lebensmittel | 400,00 | 420,50 | -20,50 |
| Freizeit | 150,00 | 0,00 | 150,00 |
| Geschenke | – | 20,00 | – |
| Investitionen | 600,00 | 640,00 | -40,00 |
| **Gesamt Ausgaben** | **2510,00** | **2606,49** | **-96,49** |

Kredite (kein Soll, keine Ausgaben) erscheint nicht in der Tabelle (R10).

- Ø Einnahmen (R23): erster Eintrag ist 2026-01 (Serien Gehalt und Wohnen), also `n` = 9 (2026-01 bis 2026-09), je Monat `E` = 3000,00 → `Ē` = 3000,00
- Ø Ausgaben (R23): dieselben 9 Monate, je Monat Wohnen 900,00 + Freizeit 15,00 = 915,00, im September zusätzlich Lebensmittel 999,00 → `Ā` = (8 × 915,00 + 1914,00) / 9 = 1026,00
- Mindesteinnahmen (R23): dieselben 9 Monate, je `E` = 3000,00 → `Emin` = 3000,00
- Nicht verplant (R4): 3000,00 − 2510,00 = 490,00, mit `Emin` als Basis ebenso 490,00
- Fixe Kosten (R25) für 2026-10: Wohnen 900,00 · Versicherungen 240,00 / 12 = 20,00 · Freizeit 15,00 (die Ausnahme am 2026-10-05 ändert nichts); das Gehalt zählt nicht (positiv); alle anderen Kategorien ohne Wert
- Überschuss: 593,51, Bezeichnung "Noch frei", weil 2026-10 der aktuelle Monat ist (R7); mit heute im November 2026 "Monatlicher Überschuss"
- Einnahmen-Liste (R9): Gehalt/Lohn (netto) 3000,00 · Nebenjob 200,00
- Wiederkehrende Ausgaben (R10): Wohnen 900,00 · Versicherungen 240,00 · Freizeit 0,00 (Vorkommen ausgesetzt) → `W` = 1140,00 · einmalige Ausgaben 2606,49 − 1140,00 = 1466,49. Das Vorkommen des Gehalts zählt nicht, weil es positiv ist. Die einzelnen Ausgaben in der Vorschau der Auswertung (1496,49) sind um die Erstattung Lebensmittel (+30,00) höher, weil die Vorschau nur Ausgaben zählt und nicht saldiert (R14).
- Lebensmittel: Netto −420,50, die Erstattung zählt nicht als Einnahme (R2)
- Kategorien ohne Soll mit Ausgaben (Geschenke): Soll und Abweichung sind `null`, Ist ist ihr Ausgaben-Ist (20,00). Deshalb ergibt die Summe der Abweichungen je Kategorie (−76,49) nicht die Abweichung gesamt (−96,49); maßgeblich ist R6.
- Mit heute 2026-09-20 (aktueller Monat 2026-09) zählen für `Ē`(2026-10) nur 2026-01 bis 2026-08; der Wert bleibt 3000,00. Mit heute 2025-12-20 gibt es keinen gezählten Monat: `Ē`, `Emin` und `Ā` sind leer, Nicht verplant ist leer, alle Soll bleiben (seit Entscheidung 36 alle in Euro).

**Erwartete Ergebnisse Auswertung (R14), Rückblick mit Vorgabe (2025-10 bis 2026-09):**
- Gezählt ab dem ersten Eintrag 2026-01, also `n` = 9 (2026-01 bis 2026-09).
- Ø Ausgaben: Je Monat Wohnen 900,00 + Freizeit 15,00 = 915,00, im September zusätzlich Lebensmittel 999,00: (9 × 915,00 + 999,00) / 9 = 1026,00 · Ø Einnahmen 3000,00 · Ø Überschuss 1974,00
- Rückblick: 2026-01 bis 2026-08 je Einnahmen 3000,00 (Gehalt/Lohn (netto)), Ausgaben 915,00 (Wohnen 900,00, Freizeit 15,00), Überschuss 2085,00; 2026-09 Einnahmen 3000,00, Ausgaben 1914,00 (zusätzlich Lebensmittel 999,00), Überschuss 1086,00
- Gesamt: Einnahmen 27000,00, Ausgaben 9234,00, Überschuss 17766,00
- Zeitraum 2026-09 bis 2026-09: `n` = 1, Ø Ausgaben 1914,00. Zeitraum 2026-01 bis 2026-10 → 400, weil 2026-10 der aktuelle Monat ist.

**Erwartete Ergebnisse Vorausschau (R14), ab 2026-10:**
- Rücklagen-Topf: nur Versicherungen (jährlich), nächstes Vorkommen 2026-10-15, `k` = 0: pro Monat 240,00 / 12 = 20,00, angespart 240,00 · Monatlich zurücklegen 20,00 · Sollte jetzt angespart sein 240,00. Ab November 2026 (nächstes Vorkommen 2027-10-15, `k` = 11): angespart 20,00
- Noch fehlende Raten: ab 2026-10 0 (fällig in diesem Monat), ab 2026-11 11 · davon aus den Rücklagen: 2026-10 240,00 (Versicherungen), alle anderen Monate 0,00
- Vorschau: 2026-10: 2636,49 (Wohnen 900,00, Versicherungen 240,00, Freizeit ausgesetzt; einzelne Ausgaben 1496,49) · 2026-11 bis 2027-09: je 915,00

**Rundung:** Geld mit Skala 2 (HALF_UP); nur im Endergebnis runden, nicht in Zwischensummen. (Prozentwerte mit Skala 4 gab es bis Entscheidung 36 für das Prozent-Soll.)

**Sonderfall Einnahmen = 0 (Entscheidung, angepasst mit Entscheidungen 26 und 36):** Ist `E` = 0, ist die Einnahmen-Liste (R9) leer; Überschuss = 0 minus Ausgaben ("Monatlicher Verlust" bzw. "Überschritten", R7). Ist `Ē(M)` = 0 (Monate gezählt, aber ohne Einnahmen), ist auch `Emin(M)` = 0, Nicht verplant wird negativ (0 minus Summe der Soll), und die Eingabe in Prozent ist auf der *Planung* gesperrt. Ist `Ē(M)` leer (kein Monat gezählt), sind `Emin(M)` und Nicht verplant leer, und die *Planung* zeigt einen Hinweis. Soll bleiben immer erhalten (Euro, R3). Keine Division durch null, keine Exception. Das Sheet gibt hier Fehlerwerte aus, die App fängt den Fall sauber ab.

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
| 18c | Nutzerübersicht und Rollen (US25): Rolle `SYSTEM_ADMIN`, letzte Anmeldung, Seite *Einstellungen › Nutzer*; E-Mail und Anzeigenamen ändern (US16) | Integrationstests grün |
| 19 | Betrieb (Abschnitt 15, Entscheidung 21): Dockerfile, `docker-compose.yml`, `.env.example`, Health-Check, Backup-Skript, Impressum und Datenschutzhinweis als Platzhalter | Image baut in der CI |
| 20 | Teilen Stufe 1 (R16 bis R18, US17 bis US19, Entscheidung 22): `SplitCalculator`, `BalanceCalculator`, Personen, Aufteilung für Transaktionen und Serien, Seite *Teilen* | Fixture F2 grün |
| 21 | Investment I1: Sparplan und Prognose (R19, US20); vorher Formeln des Blatts "Sparplan"/"Prognosen" analysieren und R19 präzisieren | Fixture F3 grün |
| 22 | Investment I2: Einmalzahlungen (R20, US21) | Tests grün |
| 23 | Investment I3: Freistellungsauftrag (R21, US22); vorher Blatt "Freistellungsauftrag" analysieren | Tests grün |
| 24 | Teilen Stufe 2 mit verknüpften Nutzern (US24): eigene Spec-Runde | Spec-PR, dann Umsetzung |
| 25 | Investment I4: Depot mit Positionen (R22, US23): eigene Spec-Runde (Kursquelle) | Spec-PR, dann Umsetzung |

**Stand 01.11.2026:** AP 14 bis 19 erledigt. AP 18 kam in zwei PRs: 18a (Spring Security, `User`, `owner` an allen Entitäten, Seed je Nutzer, Anmeldeseite) und 18b (Einladung US15, Konto US16). AP 18c (Nutzerübersicht US25) vor AP 19 eingeschoben. AP 19: Einrichtung, Update, Backup und Wiederherstellung beschreibt `docs/betrieb.md`.

**Erste Nutzung im Bekanntenkreis:** nach AP 19. AP 15 und 16 können davor oder parallel laufen, AP 20 bis 25 kommen danach.

**Stand 04.11.2026:** Vor AP 20 kommt die Überarbeitung von Bedienung und Design (Abschnitt 17, AP 19a bis 19j).

## 15. Betrieb, Sicherheit und Datenschutz (nicht-funktionale Anforderungen)

**Auslieferung (Entscheidung 21):**
- Ein Docker-Image: mehrstufiger Build (Frontend bauen, Backend bauen), Spring Boot liefert das Frontend aus, läuft als Nicht-root-Nutzer.
- `docker-compose.yml` mit App und PostgreSQL; die Datenbank liegt in einem benannten Volume. Optional (Profil `https`) Caddy als Reverse Proxy mit automatischen Zertifikaten; die App lauscht dann nur lokal.
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
- Session-Cookie `HttpOnly`, `Secure`, `SameSite=Lax`; CSRF-Schutz für schreibende Aufrufe; Ablauf nach 30 Tagen ohne Nutzung. Im Betrieb (Profil `postgres`, Docker-Image) ist `Secure` an; nur die lokale Entwicklung und ein Test im Heimnetz über HTTP schalten es ab (`SESSION_COOKIE_SECURE=false`).
- Sperre nach 5 fehlgeschlagenen Anmeldungen in 15 Minuten (US14).
- Mandantentrennung nach R15, durch Integrationstests für jeden Endpunkt abgesichert.
- Security-Header (Content-Security-Policy, `X-Content-Type-Options`, `Referrer-Policy`).
- Abhängigkeiten aktuell halten (Dependabot, Abschnitt 8).

**Datenschutz:**
- Konto löschen und Daten exportieren (US16).
- Vor dem öffentlichen Betrieb: Impressum, Datenschutzerklärung, Vertrag zur Auftragsverarbeitung mit dem Hoster, offene Registrierung mit E-Mail-Bestätigung (Entscheidung 19).

## 16. Offene Punkte

- **Vor der ersten Nutzung:** HTTPS mit Caddy auf dem Server mit echter Domain prüfen (lokal mit `DOMAIN=localhost` getestet, siehe `docs/betrieb.md`).
- Plattform für den Betrieb (Entscheidung 21).
- Konkrete Formeln der Investment-Blätter (vor AP 21 und 23 aus dem Sheet ableiten); Fixture F3.
- Teilen mit verknüpften Nutzern (US24) und Depot mit Positionen (US23, Kursquelle): eigene Spec-Runden.

- **Bank-Import (Abschnitt 18), mit echten Dateien zu prüfen:** ob `amount` bei Trade Republic Gebühren und Steuern (`fee`, `tax`) schon enthält (die App bucht nur `amount`); wie C24 vorgemerkte Umsätze exportiert (die App nimmt eine leere Spalte Buchungsdatum an); weitere Arten bei Trade Republic außer Kauf, Verkauf, Sparplan, Zinsen, Dividende und Saveback; wie die Sparkasse vorgemerkte Umsätze exportiert (die App nimmt "vorgemerkt" in `Info` oder einen leeren Buchungstag an und liest andere unbekannte Werte in `Info` nicht).
- Später zum Import: Serien als Umbuchung (Dauerauftrag aufs Sparkonto), freie Spaltenzuordnung, CAMT.053 und eine direkte Bank-API als weitere Adapter der normalisierten Bankbuchung (Entscheidungen 39 und 46), Kontostand.
- Wiederkehrende Buchungen im ganzen Verlauf erkennen, unabhängig vom Import, und als Serie vorschlagen (z. B. in der *Planung*); `SeriesDetector` (R31) arbeitet schon auf beliebigen Buchungen. Die Gläubiger-ID einer Lastschrift wäre ein starkes Signal, sobald eine Quelle sie liefert (die drei CSV-Profile liefern sie nicht).
- Aus der Code-Prüfung (08.11.2026), noch offen: Ändert der Nutzer in der Vorschau die Zuordnung eines Quellkontos, werden die Umbuchungen (R27) nicht neu berechnet, sie gelten für die vorgeschlagene Zuordnung. Ohne ID der Bank zählt der Fingerabdruck gleiche Zeilen in der Datei (R30); überschneiden sich zwei Dateien an einem Tag mit gleichen Buchungen (zweimal derselbe Kaffee), kann eine Zeile fälschlich "schon importiert" sein.
- Kategorie-Vorschlag genauer machen (R29): Wörter aus Gegenseite und Zweck mit Kategorien verbinden und nach Eindeutigkeit gewichten, damit ein Zahlungsdienst wie PayPal nicht alles gleich macht; als spätere Erweiterung besprochen.

## 17. Überarbeitung von Bedienung und Design ab 04.11.2026

Umsetzung der Entscheidungen 26 bis 33, vor AP 20. Ziel: Die App soll ohne Erklärung bedienbar sein, bevor weitere Funktionen dazukommen. Vorlage ist ein klickbarer Entwurf (Desktop und Handy, mit erfundenen Daten), der am 04.11.2026 abgestimmt wurde; maßgeblich ist diese Spezifikation. Jedes Arbeitspaket ist ein eigener Branch und PR; Berechnungen mit Tests zuerst.

| AP | Inhalt | Ergebnis |
|---|---|---|
| 19a | Spezifikation und `CLAUDE.md` anpassen (dieser Stand) | Spec-PR |
| 19b | Design-Grundlage und App-Rahmen (Entscheidungen 28, 32, US29): CSS-Variablen mit hellem und dunklem Modus, Bausteine in `src/components/ui/`, Hash-Routing, Reiter bzw. Leiste unten, Kontomenü mit *Einstellungen* und *Abmelden*; der globale Monatswähler wandert vorerst in die Seiten, die ihn noch brauchen | Frontend-Tests grün, Seiten unverändert bedienbar |
| 19c | Ø Einnahmen als Soll-Basis (R3, R4, R7, R8, R23, R24, Entscheidungen 26, 27, 33): `IncomeAverage` (Tests zuerst), `DashboardCalculator`, `Clock`, neue Felder in `/dashboard` und `/categories` | Fixture F1 (neu) grün |
| 19d | Kategorie-Icons (US28, Entscheidung 31): Migration mit Icons für bestehende Standard-Kategorien, Seed, API, `CategoryIcon` im Frontend, Auswahl unter *Einstellungen › Kategorien* | Integrationstests grün |
| 19e | Übersicht (US5, US8, US12): Kacheln mit "Noch frei", Budget-Liste mit Fortschritt und aufklappbaren Buchungen, Einstieg ohne Daten; nach der manuellen Prüfung (Entscheidung 34): Ø Ausgaben `Ā(M)` in `/dashboard` und unter der Kachel Ausgaben (Tests zuerst), Umschalter € / % und alle Anteile in `/dashboard` und `/reserves` entfernt | Fixture F1 grün, Frontend-Tests grün |
| 19f | Planung (US2, US7a, R24): "Gilt ab", mitlaufende Leiste, ein Feld mit € / %, Ist im Vormonat, ausgeblendete Einnahmen-Kategorien. Nach der Prüfung erweitert (Entscheidung 36, R3, R4, R23, R25): Soll nur in Euro (Migration V6), Umrechnung € / % im Frontend, Mindesteinnahmen und Basis per Kachel, fixe Kosten je Kategorie mit "übernehmen" (`FixedCosts`, Tests zuerst) | Fixture F1 grün, Integrations- und Frontend-Tests grün |
| 19g | Buchungsdialog und Transaktionsliste (US27, Entscheidung 30): Liste nach Tagen, Dialog bzw. Fenster von unten, Bearbeiten per Klick, "+ Buchung" | Frontend-Tests grün |
| 19h | Zeitraumfilter Transaktionen (US26): `/transactions?from&to` (Tests für Grenzen und Ausnahmen), Schnellwahl, Suche, Kategorie, Art | Integrations- und Frontend-Tests grün |
| 19i | Auswertung (R14, US13, Entscheidung 29): `/history` mit Zeitraum (Tests zuerst), `/reserves` ohne Rückblick, Unterseiten *Rückblick* und *Vorausschau*, Tooltip, aufklappbare Monate (ohne Anzeige in %, Entscheidung 34) | Fixture F1 (neu) grün |
| 19j | Einstellungen (US10): Unterseiten Kategorien, Konto, Einladungen, Nutzer; Kategorien alphabetisch (Entscheidung 38), Anlegen mit Icon, Umbenennen, Icon ändern, Archivieren und Löschen im Menü "⋯" je Zeile, archivierte Kategorien mit "Wiederherstellen" | Frontend-Tests grün |
| 19k | Lokale Datenbanken `dev` und `sample` (Entscheidung 35): Profile mit festen Logins, `SampleDataSeeder` für Person A bis D (Test zuerst), `scripts/reset-db.sh` | Integrationstest grün, beide Profile starten |

**Reihenfolge:** 19b vor allen anderen Oberflächen-APs; 19c vor 19e und 19f; 19d vor 19e, 19g, 19i und 19j, weil diese Icons zeigen (bis dahin ein Platzhalter-Icon). 19c, 19d, 19h und 19i ändern das Backend und können parallel zu den Oberflächen-APs laufen, wenn sie unterschiedliche Dateien betreffen. AP 13 (README "Learnings und Entscheidungen") folgt nach 19j, damit es die Überarbeitung enthält.

**Stand 07.11.2026:** AP 19a bis 19k erledigt und gemergt. Danach kommt der Bank-Import (Abschnitt 18), dann AP 13 (README "Learnings und Entscheidungen").

**Entscheidungen beim Umsetzen (Nachtlauf 05./06.11.2026, ohne Rückfrage getroffen, zur Prüfung im PR):**

- *19f Planung:* "Gilt ab" ist dasselbe Monatsfeld mit ‹ › wie in der *Übersicht* (Vorgabe aktueller Monat, auch frühere Monate wählbar wie bisher); die Planung hat damit ihren eigenen Monat (Entscheidung 28). *Durch Entscheidung 36 überholt:* Der Umschalter € / % behielt die eingegebene Zahl und speicherte sie in der neuen Einheit; "entspricht X €" erschien nur bis zum ersten Speichern nicht (Fehler: nach dem Speichern blieb "Gespeichert ab …" stehen). Jetzt rechnet der Umschalter um, und der Gegenwert steht immer unter dem Feld (US2). "Ist im Vormonat" kommt aus `/dashboard` des Vormonats: die Ausgaben `I(k)`, sonst die Einnahmen `N(k)` mit "+" in Petrol, ohne Einträge 0,00 €. Am Handy entfällt das Icon in der Zeile, damit lange Namen Platz haben.
- *19f nach Entscheidung 36 (06.11.2026):* Das Umschalten der Einheit speichert nichts, solange der Euro-Betrag gleich bleibt; eine umgerechnete Prozentzahl steht für den genauen Euro-Betrag, bis sie bearbeitet wird (130 € sind 4,56 % von 2850 €, gespeichert bleiben 130,00 € und nicht 129,96 €). Wechselt die Basis, während in % eingegeben wird, bleibt der Euro-Betrag, und die Prozentzahl im Feld wird umgerechnet. "Gespeichert ab …" steht 3 Sekunden unter dem Feld, neben dem Gegenwert; am Handy entfällt es, weil die Zeile unter dem Feld schon die fixen Kosten trägt. Ohne Basis steht als Gegenwert "≙ – %". Die Kachel Verplant nennt den Anteil an der Basis mit einer Nachkommastelle. Die gewählte Basis liegt im Browser unter `planning-base`. "gesetzt ab …" bleibt am Desktop unter dem Namen und entfällt am Handy wie "kein Soll". Bis 900 px Breite stehen die vier Kacheln 2 × 2. Der Datenexport (US16) enthält kein `targetPercent` mehr. Beispieldaten in Euro: Person A Freizeit 150,00 € statt 5 %, Person C Lebensmittel 288,00 € statt 12 % und Freizeit 96,00 € statt 4 % (bei 2400,00 € Gehalt).
- *19g Buchungsdialog:* Die Frage "Nur dieser Eintrag" / "Dieser und alle folgenden" steht wie im Entwurf als Auswahl "Änderung gilt für" im Dialog (Vorgabe "Nur dieser Eintrag") und gilt auch für das Löschen; "Dieser und alle folgenden" übernimmt Intervall und Enddatum der Serie. Löschen fragt im Dialog noch einmal nach ("Endgültig löschen"), statt eines Browser-Dialogs. Die Schnellwahl zeigt höchstens 4 Kategorien: die zuletzt gebuchten mit dem gewählten Vorzeichen aus den Einträgen des Monats, ohne archivierte. Der Dialog schließt mit Escape, "Schließen", "Abbrechen" und einem Klick daneben. Die Bestätigung nach dem Speichern oder Löschen verschwindet nach 3 Sekunden. Die Liste zeigt die Einträge nach Tagen, neuester Tag zuerst, mit Tagessumme (in ganzen Cent addiert, reine Anzeige) und mit Vorzeichen (+ in Petrol, − in Textfarbe); "geplant" kennzeichnet Einträge nach heute schon in 19g. Am Handy ersetzt der runde Knopf "+ Buchung" im Kopf. Bis AP 19h behält *Transaktionen* den Monatswähler.
- *19h Zeitraumfilter:* "Höchstens 10 Jahre" heißt: `to` liegt vor `from` + 10 Jahre (01.01.2016 bis 31.12.2025 geht, bis 01.01.2026 ergibt 400). Fehler bei `from` nach `to` und bei zu langem Zeitraum kommen als Feldfehler an `to`; fehlt einer der beiden Tage, nennt die Meldung den fehlenden Parameter; ohne jeden Parameter wird wie bisher `month` verlangt; `month` zusammen mit `from`/`to` ergibt 400. Schnellwahl: "Dieser Monat" (1. bis letzter Tag), "Letzte 3 Monate" (die beiden Vormonate und dieser Monat, ganze Monate), "Dieses Jahr" (Kalenderjahr) und "Zeitraum …", der mit dem bisher gezeigten Zeitraum beginnt und erst mit zwei vollständigen Daten lädt. Suche ohne Groß- und Kleinschreibung in der Beschreibung; die Kategorie-Auswahl enthält auch archivierte Kategorien (mit "(archiviert)"), weil ältere Einträge sie haben. Der Filter Art (nach dem Test ergänzt) unterscheidet "Wiederkehrend" (Vorkommen von Serien) und "Einmalig" (einzelne Transaktionen), wie "davon wiederkehrend / einmalig" in der *Übersicht*; er gilt für Einnahmen und Ausgaben. Umfassen die gezeigten Einträge mehr als einen Monat (nach dem Test ergänzt), beginnt jeder Monat mit einer Überschrift "Oktober 2026" mit Trennlinie und rechts "Einnahmen … · Ausgaben …" des Monats (gefilterte Einträge, in ganzen Cent addiert, reine Anzeige); bei einem Monat entfällt sie, weil die Zeile über der Liste ihn schon nennt. Anzahl und Summen über der Liste beziehen sich auf die gefilterten Einträge. Der Rücklagen-Hinweis bezieht sich immer auf den aktuellen Monat. Ein neuer Eintrag startet mit dem heutigen Datum.
- *19i Auswertung:* `/history` verlangt beide Monate oder keinen (nur einer ergibt 400 mit dem fehlenden Parameter). Zu lang (mehr als 120 Monate) ist ein Feldfehler an `from`, "Bis" im aktuellen Monat oder später und "Von" nach "Bis" an `to`. `total` ist ein Objekt `{income, expenses, surplus}` wie im Antwort-Beispiel. Die Kategorien im Rückblick tragen `id`, `name`, `icon` und `amount`. `/reserves` liefert nur noch Monat, Rücklagen-Topf und Vorschau; ohne `month` gilt der aktuelle Monat. Die Rechnung bleibt in `ReserveCalculator` (Methoden `history` und `outlook`), ohne neue Klasse. Fixture F1 im Integrationstest bucht das Gehalt jetzt wie in Abschnitt 12 als monatliche Serie. Schnellwahl im Rückblick: letzte 3, 6 und 12 Monate (Vorgabe), "Jahr <Vorjahr>" und "<Jahr> bisher" (im Januar nicht angeboten) als Kalenderjahr sowie "Zeitraum …" mit zwei Monatsfeldern (höchstens bis zum Vormonat), der mit dem gezeigten Zeitraum beginnt. `#/auswertung` zeigt den Rückblick; die Unterseiten heißen `#/auswertung/rueckblick` und `#/auswertung/vorausschau`. Der Tooltip zeigt Einnahmen, Ausgaben und Überschuss des Monats. Das Einklappen von Rückblick und Vorschau (AP 15a, im Browser gespeichert) entfällt. Der Monatswähler bleibt nur in der *Übersicht*.
- *19i nach dem ersten Test (06.11.2026, Entscheidung 37):* `/reserves` liefert zusätzlich `ratesLeft` je seltene Ausgabe und `fromReserves` je Monat und je Eintrag der Vorschau; ein Eintrag zählt dazu, wenn er ein Vorkommen einer Serie mit Intervall über 1 Monat ist, denn genau diese bilden den Topf. In der Vorschau steht der blaue Teil oben auf dem Balken. Die Tabelle zeigt in "davon aus den Rücklagen" den Betrag oder "–"; aufgeklappt steht er auch bei der einzelnen Ausgabe. Die Karten verwenden den Baustein `ProgressBar` der *Übersicht*. Am Handy steht das Datum eines Eintrags über seinem Namen, und das Etikett "wiederkehrend" entfällt. Das Info-Symbol ist ein gemeinsamer Baustein `InfoTip` in `src/components/ui/`; die Erklärung öffnet sich beim Überfahren und beim Fokus, ein Klick hält sie offen, Escape und ein Klick daneben schließen sie. Gibt es keine seltenen Ausgaben, entfallen Satz, Kacheln und Karten, und ein Hinweis erklärt, welche Ausgaben hier erscheinen.
- *19j Einstellungen:* Die Unterseiten liegen unter `#/einstellungen/kategorien`, `/konto`, `/einladungen` und `/nutzer`; `#/einstellungen` zeigt die Kategorien. Einladungen und Nutzer erscheinen nur für Admins; ruft ein Nutzer ihre Adresse auf, sieht er die Kategorien. Die Unterseiten stehen wie bei der *Auswertung* als Reiter oben (am Handy seitlich scrollbar), nicht als Seitenleiste. Ziehen und "Nach oben" / "Nach unten" sind nach dem Test vom 07.11.2026 entfallen (Entscheidung 38). Das Menü "⋯" enthält Umbenennen (Feld in der Zeile, Enter oder Verlassen speichert, Escape bricht ab), Icon ändern, Archivieren und Löschen; ein Klick auf das Icon öffnet die Icon-Auswahl ebenfalls. Löschen fragt in der Zeile noch einmal nach ("Endgültig löschen"). Archivierte Kategorien stehen in einem eigenen Abschnitt mit "Wiederherstellen" und "Löschen". "+ Kategorie" öffnet ein Namensfeld mit Icon-Auswahl (Vorgabe `tag`). Unter *Konto* wird das neue Passwort zweimal eingegeben (die Einladung hatte das schon); die Prüfung macht das Formular, die API bleibt unverändert. Inhalte von Einladungen und Nutzer bleiben wie bisher.

## 18. Bank-Import ab 07.11.2026

Umsetzung der Entscheidungen 39 bis 45, nach AP 19j und vor AP 13 und AP 20. Ziel: Buchungen der eigenen Bankkonten per CSV übernehmen, ohne dass etwas doppelt zählt. Jedes Arbeitspaket ist ein eigener Branch und PR, gestapelt auf AP 19j; Berechnungen mit Tests zuerst.

| AP | Inhalt | Ergebnis |
|---|---|---|
| 26a | Spezifikation und `CLAUDE.md` anpassen (dieser Stand) | Spec-PR |
| 26b | Bankkonten (R26, US30): `BankAccount`, Migration mit "Girokonto" je Nutzer, Konto an Transaktion und Serie, API, *Einstellungen › Bankkonten*, Feld Konto im Buchungsdialog, Konto in der Liste, Export, Konto löschen beim Löschen des Nutzers | Integrations- und Frontend-Tests grün |
| 26c | Umbuchungen (R27, US31): Kategorie optional, `transfer`, aus allen Rechnungen heraus, Wahl im Buchungsdialog, Kennzeichnung und Filter in der Liste | Fixture F1 grün, Tests für jede Rechnung ohne Umbuchung |
| 26d | Kategorie-Vorschlag (R29, US32): `CategorySuggester` (Tests zuerst), `/booking-suggestion`, Vorauswahl im Buchungsdialog | Unit-, Integrations- und Frontend-Tests grün |
| 26e | CSV-Import im Backend (R27, R30): Lesen je Profil, Fingerabdruck, Erkennung von Umbuchungen und Doppelungen (Tests zuerst, Beispieldateien mit erfundenen Werten), `ImportBatch`, `/imports` mit Vorschau, Übernehmen, Liste, Rückgängig | Unit- und Integrationstests grün |
| 26f | Import-Seite (US33, US34): `#/transaktionen/import`, Hochladen, Zuordnung der Quellkonten, Vorschau mit Kategorie je Zeile, Übernehmen, bisherige Importe mit Rückgängig | Frontend-Tests grün |
| 26g | Ist ersetzt Plan (R28, US35): Verweis an der Ausnahme, Treffer (Tests zuerst), in Vorschau, Übernehmen und Buchungsdialog, Dashboard zählt die Buchung als wiederkehrend, Teilen und Löschen der Serie | Fixture F1 grün, Unit-, Integrations- und Frontend-Tests grün |
| 26h | Sparkasse (R30, Entscheidung 47): ein Profil für CSV-CAMT V2 und V8, CSV-MT940 und CSV - mit Kategorien, SEPA-Kennungen im Zweck, Fingerabdruck ohne Zweck (Tests zuerst, Beispieldateien mit erfundenen Werten), Hinweis auf der Import-Seite | Unit-, Integrations- und Frontend-Tests grün |

**Stand 08.11.2026:** AP 26a bis 26h in main (PR #72); Korrekturen aus der Code-Prüfung als eigener PR (siehe unten). Offen: die Punkte zum Import in Abschnitt 16 mit echten Dateien prüfen; danach AP 13.

**Reihenfolge:** 26b vor allen anderen, weil jede Buchung ein Konto braucht; 26c vor 26e (Umbuchungen beim Import); 26d vor 26e (Vorschlag in der Vorschau); 26f nach 26e; 26g zuletzt, weil es Import und Dialog erweitert.

**Testdaten:** Beispieldateien in den Tests haben das Format der echten Exporte (Kopfzeile, Trennzeichen, Zahlen- und Datumsformat), aber nur erfundene Werte: keine echten Kontonummern, IBANs, Namen oder Beträge.

**Entscheidungen beim Umsetzen (Nachtlauf 07./08.11.2026, ohne Rückfrage getroffen, zur Prüfung im PR):**

- *26a Spezifikation:* Die Regeln R26 bis R30, die Grenzen (5 Tage und 20 % für Treffer, 3 Tage für Gegenbuchungen, 2 Tage für mögliche Doppelungen, 10 Einträge für den Vorschlag, 2 MB und 5000 Zeilen) und die Aufteilung in AP 26a bis 26g sind ohne Rückfrage festgelegt; ausdrücklich gewählt hat der Nutzer: Profile statt freier Zuordnung, Buchungstag, vorgemerkte Umsätze überspringen, Umbuchungen zählen nicht, Ist ersetzt Plan, Wertpapiere als Ausgabe, Stapeln auf AP 19j.
- *26b Bankkonten:* Die Unterseite *Bankkonten* steht zwischen *Kategorien* und *Konto* (`#/einstellungen/bankkonten`). Standardkonto zuerst, dann nach Name; Sortieren per Ziehen gibt es nicht, weil die Reihenfolge nichts bedeutet. Ein neues Konto ist nie das Standardkonto. Das Menü "⋯" hat Bearbeiten (Name und IBAN in einem Formular), "Als Standardkonto festlegen", Archivieren und Löschen; beim Standardkonto nur Bearbeiten; Löschen ist gesperrt, solange Buchungen darauf liegen (`used` in der API). Archivierte Konten stehen in einem eigenen Abschnitt mit "Wiederherstellen" und, wenn unbenutzt, "Löschen". Die IBAN steht in Vierergruppen unter dem Namen (nur Anzeige). Im Buchungsdialog steht das Feld Konto nach Datum und Wiederholen, sobald es mehr als ein nicht archiviertes Konto gibt (ein archiviertes nur, wenn es schon gewählt ist); vorgewählt ist das Standardkonto, und eine neue Buchung schickt dann kein `accountId`. Die Liste in *Transaktionen* nennt das Konto hinter der Kategorie ("Freizeit · Girokonto"), wenn es mehr als ein Konto gibt (auch archivierte zählen). Der Update-Test prüft nach der Migration, dass Person A genau das benutzte Standardkonto "Girokonto" hat. Das Menü "⋯" ist jetzt ein gemeinsamer Baustein `RowMenu` in `src/components/ui/`.
- *26c Umbuchungen:* Die Migration V8 macht die Kategorie einer Transaktion optional und fügt `transfer` hinzu; eine Prüfregel der Datenbank verlangt: Umbuchung genau dann, wenn keine Kategorie. Für alle Rechnungen gibt es eine eigene Abfrage ohne Umbuchungen (Übersicht, Ø-Werte, erster Eintrag, Rückblick, Vorausschau, R24); nur die Transaktionsliste zeigt sie. Im Buchungsdialog steht "Umbuchung zwischen eigenen Konten (zählt nicht)" als erste Wahl in der Kategorie-Auswahl, nicht bei einem Vorkommen; bei einer Umbuchung entfällt "Wiederholen". "Nur dieser Eintrag" (R13) mit `transfer: true` ergibt 400 an `transfer`. Die Liste zeigt eine Umbuchung mit einem Pfeil-Icon, "Umbuchung" statt der Kategorie und den Betrag gedämpft; Tages-, Monats- und Zeitraumsummen lassen sie aus. Der Filter Art hat "Umbuchung"; "Einmalig" zeigt nur einzelne Transaktionen, die keine Umbuchung sind. Der Datenexport (US16) enthält `transfer`, die Kategorie ist dort leer.
- *26d Kategorie-Vorschlag:* `/booking-suggestion` nimmt zusätzlich `income` an, weil der Buchungsdialog das Vorzeichen kennt, bevor ein Betrag eingegeben ist. Der Verlauf sind alle Transaktionen mit Kategorie und alle Serien des Nutzers; bis zum Import (AP 26e) ist der Text die Beschreibung. Der Dialog fragt erst, wenn die Beschreibung 300 ms nicht mehr geändert wurde und mindestens 2 Zeichen hat, und nur bei einer neuen Buchung; beim Bearbeiten bleibt die Kategorie des Eintrags. Ein sicherer und ein unsicherer Vorschlag werden gleich vorausgewählt und mit "Vorschlag" neben "Kategorie" gekennzeichnet, weil man im Dialog ohnehin vor dem Speichern hinsieht; erst beim Import (US33) müssen unsichere Vorschläge bestätigt werden. Wählt der Nutzer eine Kategorie (Auswahl oder Schnellwahl), fragt der Dialog nicht mehr nach; während ein neuer Vorschlag lädt, bleibt der vorige stehen.
- *26e CSV-Import (Backend):* Das Paket heißt `importer` (`import` ist in Java reserviert). Reine Klassen: `BankCsv` (Zeichensatz, Datensätze, Kopfzeile, Profile), `ImportRules` (bereinigter Zweck, Beschreibung, Fingerabdruck, Name und Schlüssel des Quellkontos) und `ImportMatcher` (Umbuchungen nach R27, mögliche Doppelungen nach R30). Gründe für nicht importierte Zeilen: "vorgemerkt (noch nicht gebucht)", "ohne Betrag" (leer oder 0), "andere Währung als EUR (USD)", "Datum unlesbar: …", "Betrag unlesbar: …"; eine schon importierte Zeile hat den Status `DUPLICATE` mit "schon importiert". In der Vorschau zählt `newRows` die Zeilen mit `NEW` und `POSSIBLE_DUPLICATE`. Ein neues Konto heißt nach Bank und Quelle ("C24 Ausgabenkonto", "N26 Hauptkonto", "Trade Republic" für `DEFAULT`); vorgewählt ist das Konto, dem die Quelle zuletzt zugeordnet war, sonst ein Konto mit genau diesem Namen. Beim Übernehmen wird erst alles geprüft (Zuordnung, Kategorie, Gegenbuchung, Betrag), bevor ein Konto entsteht; ein neuer Name, den ein Konto schon hat, ist dieses Konto (statt 409), damit ein zweiter Klick auf "Übernehmen" nichts kaputt macht. Fehler nennen die Zeile im Feld (`rows[3].categoryId`, `sources[0].accountId`). Den Fingerabdruck schickt das Frontend aus der Vorschau zurück; wer ihn fälscht, schadet nur den eigenen Daten. Sind alle Zeilen schon importiert, entsteht kein Import (`batch` ist `null`). Gegenbuchungen (R27 b) sind alle gespeicherten einzelnen Transaktionen ohne Umbuchung, auch importierte; mögliche Doppelungen nur solche ohne Import. Zwei Quellen, die der Nutzer demselben Konto zuordnet, ergeben keine Umbuchung. Eine Zeile für ein neues Konto passt zu einer Gegenbuchung auf jedem gespeicherten Konto. Bei Trade Republic zählt nur `amount`; `fee` und `tax` werden nicht addiert (offener Punkt in Abschnitt 16). Eine zu große Datei ergibt 400 an `file` (auch über die Grenze von Spring, `spring.servlet.multipart.max-file-size=2MB`). Der Kategorie-Vorschlag nutzt jetzt die Gegenseite und die Bankkategorie gespeicherter Buchungen; der Datenexport (US16) enthält beide.
- *26f Import-Seite:* *Transaktionen* hat oben "Importieren" neben "+ Buchung"; die Seite `#/transaktionen/import` gehört zum Reiter *Transaktionen*. Die Datei wird über "CSV-Datei wählen" gewählt (kein Ziehen und Ablegen) und sofort gelesen. Die Vorschau zeigt Bank und Anzahl, je Quelle ein Feld "<Quelle> in der Datei" mit den nicht archivierten Konten und "Neues Konto anlegen: <Name>" (Vorgabe, wenn die Quelle noch keinem Konto zugeordnet war), und darunter die Zeilen mit drei Ansichten: "Neu", "Ohne Kategorie" (gewählte Zeilen, denen die Kategorie fehlt) und "Nicht importiert" (schon importiert und nicht importierbar, je mit Grund). Je Zeile: Haken, Datum, Beschreibung mit Bankkategorie und Hinweisen ("Umbuchung erkannt" bzw. "Umbuchung auf ein eigenes Konto", "Gegenbuchung wird ebenfalls zur Umbuchung", "Schon von Hand gebucht? …"), Betrag und das Feld Kategorie mit "Umbuchung (zählt nicht)" und den nicht archivierten Kategorien; eine offene Zeile hat einen orangen Rahmen. Ein unsicherer Vorschlag steht als Knopf "Vorschlag: <Kategorie>" unter dem Feld. Eine gewählte Kategorie geht auf alle offenen Zeilen mit demselben Schlüssel über, die keine Umbuchung sind. "N Buchungen übernehmen" ist gesperrt, solange eine gewählte Zeile ohne Kategorie ist; ein Hinweis nennt die Anzahl. Nach dem Übernehmen steht "N Buchungen übernommen." mit dem Link "In den Transaktionen ansehen", der *Transaktionen* mit dem Zeitraum des Imports öffnet (`#/transaktionen?von=…&bis=…`, als Zeitraum "Zeitraum …"). "Bisherige Importe" listet Bank, Anzahl, Zeitpunkt und Zeitraum (als Link) mit "Rückgängig", das im Eintrag noch einmal fragt. Am Handy werden die Zeilen zu Karten. Nebenbei: Eine Tagessumme von 0 (etwa ein Tag nur mit Umbuchungen) steht ohne Vorzeichen da, statt "−0,00 €".
- *26g Ist ersetzt Plan:* Gibt es mehrere Treffer, geht einer mit gemeinsamem Wort vor (in R28 ergänzt), sonst würden zwei Abos mit gleichem Preis und gleicher Kategorie nach der Serien-ID vertauscht. Die Migration V10 gibt der Ausnahme (R13) einen optionalen Verweis auf die Transaktion, eindeutig. `replaces` gilt nur beim Anlegen einer Transaktion; beim Bearbeiten bleibt der Verweis, wird die Transaktion zur Umbuchung, entfällt er (das Vorkommen zählt wieder). "Nur dieser Eintrag" (R13) bleibt wie bisher ohne Verweis und lehnt `replaces` ab (400). Löschen einer Transaktion oder Rückgängig eines Imports löscht zuerst die verweisende Ausnahme. Eine Transaktion, die ein Vorkommen ersetzt, ist keine Gegenbuchung für R27. In der Vorausschau (R14) zählt eine solche Transaktion als einzelne Ausgabe, nicht als Teil der Rücklagen. `/booking-suggestion` liefert mit `amount` und `date` das Vorkommen (`occurrence`) und dann die Kategorie seiner Serie als sicheren Vorschlag. Im Buchungsdialog erscheint bei einer neuen, einmaligen Buchung (keine Umbuchung) unter Datum und Wiederholen ein Feld mit Haken "Ersetzt das geplante „…“ vom … (…)", vorausgewählt; ohne Haken wird ohne `replaces` gespeichert. In der Import-Vorschau setzt ein Treffer die Kategorie der Serie als sicher und zeigt "ersetzt „…“ vom …" mit einem Haken zum Abwählen. In der Liste trägt eine solche Buchung "wiederkehrend", und der Filter Art zählt sie zu "Wiederkehrend", nicht zu "Einmalig".
- *Fehler aus dem Test (07.11.2026):* Eine gewählte Umbuchung ging an die nächste Zeile mit derselben Gegenseite (Schlüssel aus den ersten zwei Wörtern, z. B. ein Zahlungsdienst) und setzte dort ebenfalls "Umbuchung". Jetzt wird eine gewählte Kategorie für Zeilen mit demselben Schlüssel nur zum Vorschlag (sie geht dem Vorschlag des Backends vor), und eine Umbuchung wird nie weitergegeben.
- *Nach dem zweiten Test (07.11.2026):* Umsetzung von Entscheidung 46 auf demselben PR wie der erste Test: normalisierte Bankbuchung `BankTransaction`, Art-Hinweise je Profil, Fingerabdruck mit Rückfall, Serien im Import (R31), keine Vorbelegung, "Alle Vorschläge übernehmen", "Fertige übernehmen", "Abbrechen" oben im Import, Filter Konto in *Transaktionen*. Beim Umsetzen entschieden: `BankCsv` liest nur noch und überspringt nur Unlesbares und Zeilen ohne Betrag; vorgemerkt und Fremdwährung schließt `ImportRules.skipReason` aus, mit denselben Gründen wie bisher. Der Fingerabdruck ist für schon importierte Zeilen unverändert (gleiche Werte, gleiche Reihenfolge), damit nichts doppelt kommt; den Rückfall ohne ID gab es im Code schon, er ist jetzt getestet und beschrieben. Die Art-Hinweise sind eine Liste (Stichwort → Kategoriename) an `BankProfile`; der Buchungsdialog übergibt keine. Im Buchungsdialog steht der Knopf "Vorschlag: …" unter der Kategorie-Auswahl, solange keine gewählt ist; die Kennzeichnung "Vorschlag" am Feld entfällt. In der Import-Vorschau steht "Alle Vorschläge übernehmen (N)" rechts neben den Ansichten und zählt die offenen Zeilen mit Vorschlag; "Fertige übernehmen (N)" erscheint, solange Zeilen offen sind, und übernommene Zeilen wandern mit "eben übernommen" nach "Nicht importiert". Eine Serienkarte steht über den Zeilen mit Kategorie-Auswahl, Vorschlagsknopf, "Als Serie anlegen" (gesperrt ohne Kategorie) und "Ignorieren"; die Serie bekommt das Konto der Quelle, wenn diese einem bestehenden Konto zugeordnet ist, sonst das Standardkonto. Nach dem Anlegen liest die Seite dieselbe Datei neu; der Haken "ersetzt …" ist ohne eigene Wahl gesetzt, auch nach dem Neulesen. "Abbrechen" steht oben neben "Zu den Transaktionen", solange eine Vorschau offen ist. Der Filter Konto zeigt auch archivierte Konten (mit "(archiviert)"), weil ältere Buchungen auf ihnen liegen.
- *Nach dem ersten Test (07.11.2026, Wunsch des Nutzers):* In der Import-Vorschau steht am Ende der Kategorie-Auswahl "+ Neue Kategorie …": Es öffnet in der Zeile ein Namensfeld mit "Anlegen" und "Abbrechen" (Escape bricht ab); die neue Kategorie entsteht sofort (wie "+ Kategorie" in den Einstellungen, Icon `tag`), wird für die Zeile gewählt und geht wie jede Wahl auf die offenen Zeilen mit demselben Schlüssel über. Fehler (leerer oder schon vorhandener Name) stehen in der Zeile. Die alphabetische Kategorie-Auswahl, die der Nutzer ebenfalls wünschte, kommt schon aus Entscheidung 38 (main).

**Entscheidungen beim Umsetzen von AP 26h (Sparkasse, 07.11.2026, ohne Rückfrage getroffen, zur Prüfung im PR):**

- Grundlage sind die vier Exporte des Nutzers; im Repository liegen nur Beispiele mit derselben Form und erfundenen Werten. Pflichtspalten sind die, die alle vier Exporte haben (Auftragskonto, Buchungstag, Beguenstigter/Zahlungspflichtiger, Betrag, Info); keine andere Bank hat diese Spalten, und die Sparkasse hat die Pflichtspalten der anderen nicht.
- Ein neues Konto heißt "Sparkasse …" mit den letzten 4 Stellen der IBAN ("Sparkasse …3000"). Vorgewählt ist wie bisher das Konto, dem die Quelle zuletzt zugeordnet war, sonst eines mit diesem Namen, sonst (neu, für alle Banken) ein Konto, dessen IBAN die Quelle ist. Ein neues Konto bekommt wie bei den anderen Banken keine IBAN; sie lässt sich in *Einstellungen › Bankkonten* eintragen.
- Die Wertstellung kommt aus Valutadatum, die Währung aus Waehrung (leer: EUR), die IBAN der Gegenseite aus Kontonummer/IBAN bzw. Kontonummer, die BIC aus BIC (SWIFT-Code) bzw. BLZ. Lastschrift Ursprungsbetrag und Auslagenersatz Ruecklastschrift werden nicht gelesen.
- SEPA-Kennungen werden nur gelesen, wenn der Verwendungszweck mit einer beginnt; danach gilt eine Kennung nur, wenn ihr Name groß geschrieben ist und "+" folgt ("Kindle+ …" ist keine). In den CAMT-Exporten beginnt der Zweck nie mit einer Kennung und bleibt, wie er ist.
- Für C24, Trade Republic und N26 bleibt der Fingerabdruck Zeichen für Zeichen gleich, damit schon Importiertes nicht noch einmal kommt.

**Korrekturen aus der Code-Prüfung (08.11.2026):** Die Prüfung des Imports fand zwei Fehler und drei Schwachstellen. Vom Nutzer entschieden: die Serie entsteht erst beim Übernehmen; "Alle Vorschläge übernehmen" lässt unsichere Umbuchungen aus, die Zeile nennt ihre Gegenbuchung; ein eigener PR auf main.

- *Doppelung trotz Ersatz:* Eine Buchung von Hand, die ein Vorkommen ersetzt (R28), fehlte bei den möglichen Doppelungen, weil sie für die Gegenbuchungen (R27) ausgefiltert wurde und das ersetzte Vorkommen keinen Treffer mehr gab; die Zeile sah aus wie eine neue Buchung. Jetzt bleibt sie nur aus der Suche nach Gegenbuchungen heraus.
- *Rückgängig bei Gegenbuchungen:* Die Migration V11 (V10 war schon vergeben) merkt sich an einer gespeicherten Gegenbuchung den Import und ihre frühere Kategorie. Rückgängig gibt die Kategorie zurück, wenn sie noch eine Umbuchung ist; hat der Nutzer sie inzwischen geändert, bleibt sie, wie sie ist, und nur der Vermerk entfällt. Eine Kategorie, die nur noch so vermerkt ist, lässt sich nicht löschen (409, archivieren geht).
- *Unsichere Umbuchung:* Die Vorschau liefert `transferPeer` mit Datum, Beschreibung, Betrag und Konto, bei einer Zeile derselben Datei dazu ihre Nummer und als Konto ihre Quelle. Die Zeile zeigt "Gegenbuchung: „Auf Pocket“, +100,00 € vom 04.11.2026 (Pocket Urlaub, in dieser Datei)" bzw. "(Girokonto)" für eine gespeicherte Buchung; Zeilennummern zeigt die Seite nicht (Rückmeldung des Nutzers). Liegt die Gegenbuchung in der Datei, ist der Hinweis ein Link: Er schaltet auf die Ansicht "Neu", springt ohne weiches Scrollen zur Gegenzeile (mittig) und hebt sie 2 Sekunden mit Hintergrund und blauem Rand links hervor; das Ein- und Ausblenden entfällt bei reduzierter Bewegung. Der Sammelknopf zählt eine unsichere Umbuchung nicht mit.
- *Serie beim Übernehmen:* "Als Serie anlegen" merkt die Serie vor, gibt ihren Zeilen ihre Kategorie und zeigt "wird Teil der Serie „…“"; "Nicht anlegen" nimmt den Vermerk zurück, die gewählten Kategorien der Zeilen bleiben. Die Anfrage enthält `series` und je Zeile `series` (Index), aber nur Serien, von denen mindestens eine Zeile (keine Umbuchung) mitgeht. Die Serie bekommt das Konto ihrer Quelle, auch ein neu angelegtes. Jede Zeile ersetzt das nächstgelegene freie Vorkommen höchstens 5 Tage entfernt, bei Gleichstand das frühere, die Zeilen nach Datum (`SeriesDetector.occurrenceDates`); sonst 400 an `rows[i].series`, und nichts wird gespeichert. Eine Zeile mit `replaces` und `series` ergibt 400. Enthielt "Fertige übernehmen" eine Serie, liest die Seite die Datei neu, damit die übrigen Zeilen die neue Serie treffen (R28). Rückgängig löscht die Serie mit allen ihren Ausnahmen; wurde sie inzwischen mit "Dieser und alle folgenden" geteilt, bleibt der neue Teil (er ist keine Serie des Imports).
- *Umbuchung von Hand als Gegenbuchung:* Eine von Hand gebuchte Umbuchung (−22 € auf Trade Republic) fand ihre andere Seite aus der C24-Datei (+22 € am selben Tag) nicht, weil R27 gespeicherte Umbuchungen ausschloss (Rückmeldung des Nutzers). Die App verknüpft die Seiten nicht (Entscheidung 41); ob eine gespeicherte Umbuchung ihre andere Seite schon hat, zeigt deshalb nur der Abgleich der gespeicherten Umbuchungen untereinander (`ImportMatcher.openTransfers`, geladen im Zeitraum der Datei ±6 Tage, damit auch eine am Rand ihre Seite findet). Nur die offenen zählen als Gegenbuchung. Beim Übernehmen bleibt eine solche Gegenbuchung unverändert und wird vom Import nicht gemerkt, Rückgängig lässt sie also eine Umbuchung. Mögliche Doppelungen (R30) prüfen Umbuchungen weiterhin nicht.
- *Geschwindigkeit:* Mit 5000 Zeilen und 2000 gespeicherten Buchungen brauchte die Suche nach Umbuchungen etwa 5 s. `ImportMatcher` sortiert jetzt einmal und vergleicht nur Einträge mit passendem Betrag (etwa 12 ms, gleiches Ergebnis).
- *Serien-Treffer gesammelt übernehmen:* Eine Datei mit 6 Mietzahlungen traf die Serie „Miete“, aber jede Zeile brauchte noch einen Klick auf ihren Vorschlag (Rückmeldung des Nutzers). Gewählt: eine Karte je vorhandener Serie mit mindestens 2 Treffern, die alle mit einem Klick übernimmt. Verworfen: die Treffer vorzubelegen (gegen Entscheidung 46, ein falscher Treffer sähe fertig aus) und ein zweiter Sammelknopf (unübersichtlich bei mehreren Serien). Bei einem Treffer reicht der Klick in der Zeile. Gruppiert wird im Frontend nach `replaces.recurringId`; welche Zeile welches Vorkommen ersetzt, bestimmt weiter das Backend. Abgewählte Zeilen (etwa mögliche Doppelungen) zählen nicht mit. Danach wünschte der Nutzer, dass die Zeilen, die eine Karte erledigt hat, aus der Liste verschwinden (neue und vorhandene Serie); sie stehen aufklappbar in der Karte, damit die Liste nur zeigt, was noch zu tun ist. Der Hinweis "wird Teil der Serie" an der Zeile entfällt damit.
- Noch offen (Abschnitt 16): die Umbuchungen nach geänderter Kontozuordnung neu berechnen; überschneidende gleiche Zeilen ohne ID der Bank.
