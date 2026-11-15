# Konzept: Plan und Ist trennen

Stand 13.11.2026 · abgestimmt mit dem klickbaren Mockup (https://claude.ai/artifact/CL9gJTWpxBTpRdL5AUQEHT, Version 21) · danach wandert das Beschlossene in `docs/spezifikation.md` (neue Entscheidungen in Abschnitt 11, geänderte Regeln, neuer Abschnitt 19 mit Arbeitspaketen).

Die offenen Punkte sind am 13.11.2026 mit dem Nutzer geklärt (Abschnitt 10).

---

## 1. Ziel

Die App soll einfach und für jeden sofort verständlich bleiben. Der Nutzer plant einen **normalen Monat** und sieht, wo er davon abweicht. Vertrauen in die Zahlen entsteht, weil **nur echte Buchungen als Ist** zählen. Was regelmäßig passiert, wird geplant, vorgeschlagen und eingetragen, aber nie heimlich als Ist angenommen.

Bewusst einfach gehalten, für später: Hochrechnung im laufenden Monat, „Verfügbar"-Zahl, Kostenhebel in der Auswertung.

## 2. Grundprinzipien

1. **Ist sind nur echte Buchungen**, von Hand angelegt, automatisch aus der Planung eingetragen oder importiert. Ein geplanter Betrag zählt nie als Ist.
2. **Die Planung ist ein normaler Monat aus einzelnen Posten.** Der Nutzer trägt ein, was er kennt (Miete, Deutschlandticket, Benzin, Gehalt), und muss nichts zusammenrechnen.
3. **Alles verplanen:** Jeder Euro des geplanten Einkommens hat einen Zweck (Fixkosten, Budgets, Sparen). „Nicht verplant" ist kein Erfolg, sondern ein offener Rest.
4. **Seltene Zahlungen über Rücklagen:** Eine Jahreszahlung zählt im Budget nur mit ihrem Monatsanteil. Den Rest zahlt die Rücklage.
5. **Wenig Pflege:** Automatische Einträge, Abhaken mit einem Tipp, Vorschläge aus den eigenen Buchungen, der Import ersetzt automatische Einträge.
6. **Ruhige Oberfläche:** Erklärungen hinter (?), Meldungen hinter der Glocke, Details hinter einem Tipp.

## 3. Begriffe

### 3.1 Geplanter Posten (bisher „Serie")
Ein Eintrag im normalen Monat.

| Feld | Bedeutung |
|---|---|
| Name | z. B. „Benzin", „Kfz-Versicherung" |
| Wie oft | wöchentlich, alle 2 Wochen, monatlich, alle 2 Monate, vierteljährlich, halbjährlich, jährlich, ohne festen Termin |
| Betrag | der **echte Betrag pro Zahlung** (Kfz-Versicherung: 1.200 €). Bei „ohne festen Termin": Betrag im Monat |
| Am Tag / Fällig im | Tag im Monat (monatlich) bzw. Monat der Fälligkeit (seltener) |
| Kategorie | frei wählbar, jede Kategorie, auch bei seltenen Posten |
| Konto | Konto der Buchung, Vorgabe Standardkonto |
| Eintragen | automatisch am Termin · beim Abhaken · selbst anlegen |
| Nur an Werktagen | fällt der Termin auf einen Wochenend- oder Feiertag, gilt der **nächste Werktag** |
| Gilt ab | Monat, ab dem der Posten (oder seine Änderung) gilt; Vorgabe der aktuelle Monat |

- **Rhythmus technisch flexibel:** Gespeichert wird **Einheit** (Woche oder Monat), **Anzahl** und das **erste Datum** als Anker. Die Auswahl „Wie oft" bietet die Vorgaben aus der Tabelle (wöchentlich = 1 Woche, alle 2 Wochen = 2 Wochen, monatlich = 1 Monat, alle 2 Monate = 2 Monate, vierteljährlich = 3, halbjährlich = 6, jährlich = 12 Monate). „Alle X Wochen / Monate" frei einzugeben ist später ohne Umbau möglich. Komplexe Regeln („jeden letzten Freitag") sind bewusst nicht vorgesehen. Termine liegen am Anker + k × Abstand; bei Monaten gilt wie heute die Regel für den Monatsletzten (der 31. wird im Februar zum 28. bzw. 29.). Übernommen aus PR #98 (Ticket #79).
- Der **Monatsanteil** wird nie eingetippt, die App rechnet ihn (1.200 € jährlich = 100 € im Monat) und zeigt ihn im Bearbeiten-Fenster an.
- **Gruppen** ergeben sich aus den Feldern: Einkommen (positiver Betrag) · **Fixkosten** (Ausgabe mit Termin) · **Budgets (variable Kosten)** (Ausgabe ohne festen Termin) · **Sparen** (Sparrate, Sparziel, Rücklage von Hand).
- **Budget einer Kategorie** = Summe aller ihrer Posten (fest und variabel, seltene mit Monatsanteil). Ein eigenes Kategorie-Soll gibt es nicht mehr.
- **Vorgaben der Eintragungsart:** Einkommen und Fixkosten automatisch, Sparen beim Abhaken, Budgets selbst anlegen.
- **Änderungen gelten ab einem Monat:** Ändert der Nutzer einen Posten, fragt die App „ab welchem Monat?" (Vorgabe: aktueller Monat). Vergangene Monate behalten ihren Plan. Ein beendeter Posten gilt bis zum Vormonat.
- **Nur in Euro:** Beträge werden in Euro eingegeben. Die Eingabe in Prozent der Einnahmen entfällt; die Anteile zeigt der Balken der Planung.

### 3.2 Termin
Jedes Vorkommen eines Postens mit Datum hat einen Status: **erwartet**, **erfüllt** (eine echte Buchung passt), **ausgeblieben** (5 Tage nach dem Termin ohne passende Buchung) oder **entfällt** (vom Nutzer für diesen Monat ausgesetzt).

### 3.3 Buchung
Wie heute, mit Konto (R26) und Kategorie. Neu:
- **fix / variabel:** Eine Ausgabe, die einen Termin erfüllt, ist fix, alle anderen variabel. Das ersetzt „wiederkehrend / einmalig" (R10).
- **automatisch:** Kennzeichen für Buchungen, die aus der Planung angelegt wurden.
- **Art:** Einnahme, Ausgabe, **Sparen**, Umbuchung. Sparen ist eine Umbuchung von einem Alltagskonto auf ein Spar- oder Depotkonto (oder direkt als Sparen gebucht, wenn das Zielkonto nicht in der App ist). Umbuchungen zwischen Alltagskonten zählen weiter nirgends (R27).

### 3.4 Konto
Wie heute (R26), dazu:
- **Rolle:** Alltag, Sparen oder Depot.
- **Kontostand** mit Stichtag, von Hand eingetragen (später aus CAMT.053). Der App-Saldo ist dieser Stand plus alle Buchungen danach. Ein neuer Stand zeigt die Differenz zum App-Saldo („fehlt eine Buchung?").

### 3.5 Rücklagentopf
Zu jedem seltenen Posten, also mit einem Abstand über einem Monat (alle 2 Monate, vierteljährlich, halbjährlich, jährlich), gehört automatisch ein Topf. Wöchentliche und monatliche Posten haben keinen. Der Topf hängt am **Posten**, nicht an einer Kategorie: Ein seltener Posten hat wie jeder andere eine frei wählbare Kategorie (Kfz-Versicherung in Mobilität oder Versicherungen, Rundfunkbeitrag in Nebenkosten, Jahresabo in Freizeit …), und seine Kosten zählen in dieser Kategorie.
- **Jeden Monat** gilt der Monatsanteil als zurückgelegt.
- **Ohne Konto** rechnet die App den Stand nur aus („gerechnet").
- **Mit verknüpftem Sparkonto** ist der Stand der des Kontos, und Zahlungen von diesem Konto gelten automatisch als aus der Rücklage bezahlt.
- **Erkennung „aus der Rücklage bezahlt", ohne dass der Nutzer etwas einstellt:** Entweder geht die Zahlung vom verknüpften Konto ab, oder sie erfüllt den Termin des seltenen Postens. Für Ungeplantes (z. B. Reparatur aus dem Notgroschen) hat der Buchungsdialog einen Schalter **„aus Rücklage / Ersparnissen bezahlen"** mit Auswahl des Topfs oder Sparziels. Er erscheint nur, wenn es Rücklagen oder Sparziele gibt. Die Ausgabe belastet dann nicht das Monatsbudget, sondern verringert den Topf.
- **Reicht der Topf nicht,** zählt der fehlende Teil ins Monatsbudget der Kategorie.

### 3.6 Sparziel
Betrag, optional Zieldatum und Konto. Die Monatsrate ist ein Posten in der Gruppe Sparen. Notgroschen und Urlaub sind Sparziele.

### 3.7 Sonstiges
Eine Standard-Kategorie und ein normales Budget. Sie ist **bewusst ein Sammelbecken** für Kleinkram und für Buchungen, die man später einordnet. Es gibt dafür keine eigene Meldung.

## 4. Seiten

Navigation: **Übersicht · Transaktionen · Planung · Auswertung (unverändert) · Konten (neu)**, dazu die Glocke oben rechts. Am Handy hat die untere Leiste damit fünf Einträge.

### 4.1 Übersicht
**Die Übersicht bleibt wie in der App heute, mit drei Änderungen:**
1. **Hinzu kommen** die Karten
   - **Sparen:** geplante Sparsumme des Monats und die Sparquote. Ein Tipp öffnet die Sparposten und, falls der Monat günstiger läuft, „+ X € könntest du im November zusätzlich sparen".
   - **Erwartet:** „8 Zahlungen im November". Ein Tipp springt zum Abschnitt „Erwartet" der Transaktionen.
2. **Entfernt wird** die dritte Kachel oben, **Noch frei / Überschritten**.
3. **Seltene Zahlungen mit Rücklage**, in der Kategorie, die der Nutzer dem Posten gegeben hat (Beispiel: Kfz-Versicherung in Mobilität): In der Budgetzeile zählt nur der Monatsanteil, darunter steht eine grüne Zeile:
   - jeden Monat: „100 € zurückgelegt für die Kfz-Versicherung · Rücklage 1.000 € von 1.200 €"
   - im Fälligkeitsmonat, Rücklage reicht: gleiche Zahlen, kein Sprung, „Kfz-Versicherung 1.200 € aus der Rücklage bezahlt"
   - Rücklage zu klein: der fehlende Teil zählt ins Monatsbudget, die Zeile wird orange, „1.000 € aus der Rücklage, 200 € fehlten darin"

Alles andere bleibt, wie es ist. Inhaltlich ändert sich durch das neue Modell nur, woraus die bestehenden Zahlen kommen:
- **Budget je Kategorie:** „Ist von Plan", der Plan ist die Summe der Posten der Kategorie (3.1), das Ist sind echte Buchungen. Die Gesamtzeile bleibt.
- **Kachel Ausgaben:** „fix · variabel" statt „wiederkehrend · einmalig".

### 4.2 Transaktionen
Wie heute (Zeitraum, Suche, Filter, Liste nach Tagen, Import). Neu:
- Kennzeichen **fix** (nur bei Ausgaben, siehe P5), **automatisch** und wie bisher **Umbuchung**; der Filter Art bietet fix, variabel, Sparen. Ein Info-Tipp (?) erklärt „fix": „Fix sind Ausgaben, die zu einem geplanten Posten passen, z. B. Miete, Versicherungen, Abos." Gleicher Tipp an „fix · variabel" in der Kachel Ausgaben der Übersicht.
- **Abschnitt „Erwartet bis 30.11."** am Ende der Liste (eigene Adresse `#erwartet`): alle Termine des Monats, die noch nicht erfüllt sind.
  - Automatische zeigen „wird am 15.11. eingetragen".
  - Die anderen haben einen Kreis zum Abhaken, bezahlte einen Haken.
  - Ein Tipp öffnet ein Fenster mit **„Ist bezahlt: als Buchung eintragen"**, „Anderer Betrag oder anderes Datum" und „Diesen Monat fällt es weg".
  - Abhaken lässt sich rückgängig machen, über die Bestätigung unten und im Fenster.
- **Import-Ergebnis direkt beim Import:** Der Import zeigt, welche automatischen Einträge die Datei durch die echte Buchung ersetzt hat und welche **automatisch eingetragen, aber nicht in der Datei** sind. Für Letztere gibt es „Eintrag löschen" oder „Behalten".

### 4.3 Planung
- **Kopf:** Balken aus Fixkosten, Budgets und Sparen, dazu **„Nicht verplant"** in Grau. Für den Rest gibt es **„Zum Sparen"** (grün, öffnet die Auswahl des Sparziels) und **„Zu Sonstiges"**. Ist alles verteilt: „✓ Alles verplant".
- **Gruppen:** Einkommen · Fixkosten · **Budgets (variable Kosten)** · Sparen. In Fixkosten und Budgets sind die Posten **je Kategorie** gruppiert, mit Zwischensumme.
- **Einkommen:** Gerechnet wird immer mit dem **geplanten Einkommen**, eine Auswahl der Basis gibt es nicht mehr. Zur Orientierung stehen die Ø- und die Mindesteinnahmen der letzten 12 Monate darunter.
- **Posten bearbeiten:** Felder wie in 3.1. Seltene Posten mit echtem Betrag und Rhythmus, der Monatsanteil wird angezeigt.
- **Vorschläge:** Unter Fixkosten „Gefunden in deinen Buchungen" (z. B. ein Abo) mit „Übernehmen".
- **Gilt ab** je Posten (3.1): Beim Speichern einer Änderung fragt die App, ab welchem Monat sie gilt. Die Planung zeigt den aktuellen Monat; frühere Monate lassen sich ansehen. Im Mockup nicht gezeigt.

### 4.4 Konten (neu, ersetzt Einstellungen › Bankkonten)
- Konten nach Rolle Alltag, Sparen, Depot, jeweils mit Summe.
- Ein Tipp auf den Betrag trägt den **Kontostand laut Bank** ein und zeigt die Differenz.
- **Rücklagen:** jeder Topf mit Stand („1.000 € von 1.200 €"), Monatsanteil und Fälligkeit. Dazu die Auswahl „Konto der Rücklage" (ohne Konto oder ein Sparkonto).
- **Verlauf** eines Kontos nur, wenn es ins Minus zu rutschen droht.

### 4.5 Auswertung
**Unverändert aus der App übernommen.** Nicht Teil des Mockups.

### 4.6 Glocke (Benachrichtigungen)
Meldungen stehen nicht auf den Seiten, sondern hinter der Glocke mit Zähler. Jede lässt sich mit „Erledigt" entfernen:
- **Monatsabschluss:** „Oktober abschließen: 180 € sind übrig geblieben. Wohin damit?". Zur Wahl stehen die Sparziele. Klein und grau darunter „Nicht zuordnen (das Geld bleibt unverplant auf dem Girokonto)". Rückgängig ist möglich.
- „X € nicht verplant" mit Link zur Planung.
- Konto droht ins Minus zu rutschen.
- Preisänderung eines Postens („Streaming kostet mehr") mit Link zur Planung.

## 5. Rechenregeln

| Nr. | Regel |
|---|---|
| P1 | **Ist** eines Monats = Summe der echten Buchungen je Art (Einnahme, Ausgabe, Sparen). Geplante Posten zählen nie. |
| P2 | **Monatsanteil** eines Postens = Betrag × Faktor des Rhythmus. Einheit Woche: Faktor = 52/12 ÷ Anzahl (10 € wöchentlich = 43,33 €, alle 2 Wochen = 21,67 €). Einheit Monat: Faktor = 1 ÷ Anzahl (1.200 € jährlich = 100 €). Ohne festen Termin: Betrag im Monat. Gerechnet in voller Genauigkeit, gerundet erst am Ende (Skala 2, HALF_UP). Ein Monat mit 5 statt 4 Terminen eines wöchentlichen Postens liegt im Ist leicht über dem Monatsanteil; das ist der echte Geldfluss und bleibt so. |
| P3 | **Plan einer Kategorie** = Summe der Monatsanteile ihrer Posten. **Gesamtplan** = Summe aller Kategorien. |
| P4 | **Nicht verplant** = geplantes Einkommen − Fixkosten − Budgets − Sparen (alle als Monatsanteil). |
| P5 | **Erfüllung:** Eine echte Buchung erfüllt einen Termin nach der heutigen Treffer-Regel (R28: gleiches Vorzeichen, ±20 % Betrag, gemeinsames Wort bzw. gleiche Kategorie), mit einer **Toleranz beim Datum von höchstens 5 Tagen und höchstens dem halben Abstand** zum Nachbartermin (abgerundet): ±3 Tage bei wöchentlich, ±5 Tage sonst. So trifft eine Buchung nie den Termin der Nachbarwoche (aus PR #98). Erfüllte **Ausgaben** sind **fix** (ersetzt R10); Einnahmen bekommen kein Kennzeichen. |
| P6 | **Automatischer Eintrag:** Am Termin (bzw. nächsten Werktag) legt die App eine Buchung mit dem geplanten Betrag an, gekennzeichnet „automatisch". Bringt ein Import eine Buchung, die diesen Termin erfüllt (mit der Toleranz aus P5), **ersetzt** sie den automatischen Eintrag (der echte Betrag gilt). Ist ein automatischer Eintrag im Zeitraum eines Imports desselben Kontos nicht enthalten, meldet der Import das. |
| P7 | **Abhaken:** „Ist bezahlt" legt eine Buchung mit dem geplanten Betrag an; „Rückgängig" löscht sie wieder. |
| P8 | **Rücklage:** Soll-Stand eines Topfs = Monatsanteil × Monate seit der letzten Fälligkeit (heutige Logik aus R14). Mit verknüpftem Konto zählt dessen Saldo. In der Budgetzeile zählt jeden Monat der Monatsanteil als erledigt. Im Fälligkeitsmonat wird die Zahlung aus dem Topf bezahlt; was der Topf nicht deckt, zählt ins Monatsbudget. |
| P9 | **Sparquote** = geplantes Sparen ÷ geplantes Einkommen (in der Sparen-Karte). |
| P10 | **Monatsabschluss:** Übrig = Einnahmen − Ausgaben − Sparen des Vormonats (Ist). Ist es positiv, fragt die Glocke nach dem Ziel. |
| P11 | **App-Saldo** = letzter Kontostand + Buchungen danach; der Abgleich zeigt die Differenz und bucht sie nicht. |

## 6. Was sich an der Spezifikation ändert

| Bestehend | Änderung |
|---|---|
| R1, R12 (Vorkommen zählen im Ist) | Vorkommen zählen nie im Ist (P1); R12 beschreibt die Termine der Posten |
| R2–R4, R23, R24 (Soll je Kategorie ab Monat, Basis Ø/Minimum, %) | Soll = Summe der Posten (P3), „gilt ab" je Posten; Basis immer geplantes Einkommen; Ø und Minimum nur zur Orientierung; Eingabe in % entfällt |
| R7 „Noch frei" | Kachel entfällt |
| R10 wiederkehrend/einmalig | fix/variabel über Erfüllung (P5) |
| R13 Bearbeiten von Vorkommen | „Nur dieser Eintrag" wird zu „Anderer Betrag oder anderes Datum" / „fällt weg"; „Alle Einträge" = Posten in der Planung bearbeiten |
| R14 Rücklagen | Töpfe je seltenem Posten, optional mit Konto (P8) |
| R25 Fixkosten | ergibt sich aus der Gruppe Fixkosten |
| R26 Bankkonten | dazu Rolle und Kontostand (3.4) |
| R27 Umbuchung | Alltag → Sparen/Depot ist Sparen |
| R28 Ist ersetzt Plan | wird zu „Buchung erfüllt Termin" (P5) und „Import ersetzt automatischen Eintrag" (P6) |
| R31 Serien im Import | schlägt geplante Posten vor |
| Entscheidung 6 | abgelöst: Ist = nur echte Buchungen |
| Entscheidungen 36 (Soll in €, % als Hilfe), 41, 44 | durch P3, Kontorollen und die Art Sparen ersetzt |
| Tickets #76, #91 (PR #96) | entfallen: abgedeckt durch Abhaken, Erfüllung und „Übernehmen" von Vorschlägen |
| Ticket #79 (wöchentlich) | gelöst: flexibler Rhythmus mit Einheit und Anzahl (3.1), wöchentlich und alle 2 Wochen von Anfang an; Monatsanteil P2, Toleranz P5 |
| Ticket #86 (fix/variabel) | gelöst durch P5 |
| Ticket #104 (Rhythmus anzeigen und ändern) | gelöst durch die Planung (3.1) |

## 7. Übernahme der bestehenden Daten
1. **Serien → geplante Posten** mit Rhythmus, Tag, Konto und Kategorie. Eintragungsart **automatisch**, damit sich für bestehende Nutzer zunächst nichts ändert.
2. **Kategorie-Soll wird nicht übernommen.** Die Planung startet nur mit den bisherigen Serien; Budgets (variable Kosten) legt der Nutzer neu an. Folge: Vergangene Monate haben danach keinen Plan für variable Kosten mehr, ihre Budgetzeilen zeigen nur noch das Ist und den Plan der festen Posten. Die Planung weist beim ersten Öffnen darauf hin („Lege deine Budgets für variable Kosten an").
3. **Vergangene Vorkommen ohne Buchung → echte Buchungen**, einmalig beim Umstieg, gekennzeichnet „automatisch". So bleiben alle bisherigen Monatszahlen gleich.
4. **Ersetzte Vorkommen (R28)** → erfüllte Termine.
5. **Konten:** alle zunächst „Alltag". Der Nutzer stellt Tagesgeld und Depot um.
6. **Seltene Serien** (Intervall über 1 Monat) bekommen einen Rücklagentopf ohne Konto.

## 8. Arbeitspakete (Vorschlag)

| AP | Inhalt | Umfang |
|---|---|---|
| 27a | Spezifikation, Entscheidungen, `CLAUDE.md` | S |
| 27b | **Geplante Posten** statt Serien und Kategorie-Soll: Datenmodell mit flexiblem Rhythmus (Einheit, Anzahl, Anker), Migration (Abschnitt 7), P2–P4, Seite *Planung* (Gruppen, Kategorien, Nicht verplant, Bearbeiten mit Rhythmus) | L |
| 27c | **Ist nur aus Buchungen:** Termine und Erfüllung (P1, P5), automatische Einträge (P6), Abschnitt *Erwartet* mit Abhaken (P7), Übersicht mit fix/variabel und Plan je Kategorie | L |
| 27d | **Übersicht:** Karten Sparen und Erwartet, Kachel Noch frei entfernen | S |
| 27e | **Rücklagentöpfe** (P8) mit Anzeige in der Budgetzeile | M |
| 27f | **Konten:** Reiter, Rollen, Art Sparen, Kontostand und Abgleich (P11), Rücklagen mit Kontoverknüpfung | M |
| 27g | **Import:** ersetzt automatische Einträge, Meldung fehlender Einträge (P6) | M |
| 27h | **Glocke** mit Monatsabschluss (P10), Nicht verplant, Konto im Minus, Preisänderung | M |
| 27i | **Vorschläge** aus den Buchungen in der Planung (Abos, regelmäßige Zahlungen) | M |

Rechenklassen ohne Spring, Tests zuerst: `PlanCalculator` (P2–P4), `Fulfillment` (P5, Status der Termine), `ReserveCalculator` (bestehend, an P8 anpassen), `AccountBalance` (P11), `MonthClosing` (P10).

## 9. Später (bewusst nicht in dieser Runde)
- Hochrechnung im laufenden Monat; erst nach den übrigen Funktionen neu bewerten.
- „Verfügbar"-Zahl und Prognose je Konto über mehrere Monate.
- Kostenhebel in der Auswertung, Verträge mit Kündigungsfristen (Vertragsende, Frist am Posten; Erinnerung in der Glocke).
- Regeln zur Auto-Kategorisierung, CAMT.053 mit Kontostand.
- Freie Eingabe „alle X Wochen / Monate" in der Oberfläche; Verschlüsselung (#89, Vorschlag in PR #98).
- Teilen (AP 20) und Investment (AP 21–25).

## 10. Geklärte Punkte (13.11.2026)

| Frage | Entscheidung |
|---|---|
| Wann gilt ein Termin als ausgeblieben? | 5 Tage nach dem Termin ohne passende Buchung |
| Ungeplantes aus einer Rücklage bezahlen? | Schalter „aus Rücklage / Ersparnissen bezahlen" im Buchungsdialog, nur wenn es Töpfe oder Sparziele gibt |
| Eingabe in Prozent der Einnahmen? | entfällt |
| „Gilt ab"? | je Posten: Änderungen gelten ab einem Monat (Vorgabe aktueller Monat), vergangene Monate behalten ihren Plan |
| Kategorie-Soll beim Umstieg? | wird nicht übernommen; nur die Serien werden zu Posten |
| Verträge mit Kündigungsfristen? | spätere Stufe |
| Was wird aus PR #98 (Tickets #79, #86) übernommen? | flexibler Rhythmus mit wöchentlich und alle 2 Wochen, Toleranz nach Abstand, Info-Tipp zu „fix"; #86 ist durch P5 gelöst, „regelmäßig / einmalig" für Einnahmen entfällt |
