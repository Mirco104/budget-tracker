# Budget-Tracker: Was die App heute kann

Stand 10.11.2026, Hauptversion (`main`). Diese Übersicht beschreibt die App aus Sicht der Nutzer, ohne Technik. Die genauen Regeln stehen in `docs/spezifikation.md`.

---

## Kurz gesagt

Der Budget-Tracker hilft, das eigene Geld im Blick zu behalten:
- **planen**, wie viel pro Kategorie und Monat ausgegeben werden soll,
- **erfassen**, was wirklich eingenommen und ausgegeben wurde, von Hand oder per Import der Kontoauszüge,
- **vergleichen**, ob der Monat im Plan liegt,
- **auswerten**, wie sich Einnahmen und Ausgaben über die Zeit entwickeln und was für seltene Ausgaben zurückgelegt werden sollte.

Mehrere Personen können die App nutzen. Jede sieht nur ihre eigenen Daten. Neue Nutzer kommen über eine Einladung.

Die App hat vier Bereiche: **Übersicht**, **Transaktionen**, **Planung** und **Auswertung**. Dazu kommen die **Einstellungen** im Kontomenü oben rechts. Jede Seite hat eine eigene Adresse, damit Lesezeichen, Neuladen und der Zurück-Knopf funktionieren.

---

## 1. Anmeldung und Konto

- **Anmelden** mit E-Mail und Passwort. Nach 5 falschen Versuchen in 15 Minuten wird die Anmeldung für 15 Minuten gesperrt. Ohne Nutzung endet die Sitzung nach 30 Tagen.
- **Einladung:** Ein Admin erzeugt einen Einladungslink und schickt ihn selbst weiter, die App versendet keine E-Mails. Wer den Link öffnet, legt E-Mail, Anzeigenamen und Passwort fest (mindestens 12 Zeichen). Ein Link gilt 7 Tage und nur einmal.
- **Neue Nutzer starten mit fertigen Kategorien** (z. B. Wohnen, Lebensmittel, Mobilität, Gehalt/Lohn) und einem Konto „Girokonto".
- **Eigenes Konto** (Einstellungen › Konto): Anzeigenamen, E-Mail und Passwort ändern; alle eigenen Daten als Datei herunterladen; das Konto samt allen Daten endgültig löschen.

## 2. Übersicht: der Monat auf einen Blick

- **Monatsauswahl** mit Pfeilen vor und zurück, Monat direkt wählbar, Knopf „Zum aktuellen Monat".
- **Drei Kacheln:**
  - **Einnahmen** des Monats, darunter der Durchschnitt der letzten 12 Monate.
  - **Ausgaben** des Monats, aufgeteilt in **wiederkehrend** (Fixkosten) und **einmalig**, darunter der Durchschnitt.
  - **Noch frei** bzw. **Überschuss:** was nach Plan und Ausgaben übrig ist.
- **Budget-Liste:** je Kategorie mit Icon „Ist von Soll", Fortschrittsbalken und „noch X €" bzw. „X € über dem Soll" (in Orange). Ein Klick auf eine Kategorie zeigt ihre Buchungen im Monat. Am Ende stehen die Gesamtsumme und die Einnahmen nach Kategorie.
- Ein Hinweis sagt, ob der Monat abgeschlossen, der laufende oder ein zukünftiger ist.
- **Einstieg für neue Nutzer:** Ohne Daten zeigt die Übersicht drei Schritte (Soll festlegen, erste Buchung, Übersicht ansehen).

## 3. Buchen: Einnahmen und Ausgaben erfassen

- **Buchungsdialog** über „+ Buchung", am Handy über den runden Knopf unten rechts. Am Handy öffnet er sich als Fenster von unten.
- Darin, in dieser Reihenfolge:
  - Umschalter **Ausgabe / Einnahme** und großer **Betrag**,
  - **Beschreibung**,
  - **Kategorie** mit Schnellwahl der zuletzt genutzten Kategorien,
  - **Datum**, **Wiederholen** und **Konto**.
- **Kategorie-Vorschlag:** Beim Tippen der Beschreibung schlägt die App eine Kategorie aus den eigenen früheren Buchungen vor. Sie wird erst per Klick übernommen.
- **Fehlt eine Kategorie oder ein Konto,** legt man sie direkt im Dialog an.
- **Umbuchung zwischen eigenen Konten** (z. B. Girokonto → Tagesgeld): Sie hat keine Kategorie und zählt nirgends als Einnahme oder Ausgabe.
- **Bearbeiten und Löschen:** ein Klick auf eine Buchung in der Liste. Vor dem Löschen fragt die App noch einmal nach. Nach dem Speichern erscheint eine kurze Bestätigung.

## 4. Wiederkehrende Buchungen (Serien)

- Mit **„Wiederholen"** wird eine Buchung zur Serie: **monatlich, vierteljährlich, halbjährlich oder jährlich**, mit erster Abbuchung und optional einem Enddatum. Beispiele sind Miete, Versicherungen, Abos und Gehalt.
- Die Einträge einer Serie erscheinen automatisch in jedem passenden Monat, als „wiederkehrend" gekennzeichnet. Einträge in der Zukunft sind als „geplant" gekennzeichnet.
- **Wichtig zum Verständnis:** Ein Eintrag einer Serie **zählt heute als Ist**, auch ohne echte Buchung dazu. Kommt eine echte Buchung dazu, ersetzt sie den geplanten Eintrag, damit nichts doppelt zählt (siehe Import).
- **Ändern oder Löschen** eines Eintrags mit drei Möglichkeiten:
  - **Nur dieser Eintrag:** z. B. die Miete war diesen Monat ausnahmsweise höher.
  - **Dieser und alle folgenden:** z. B. Mieterhöhung ab jetzt. Frühere Einträge bleiben.
  - **Alle Einträge:** Betrag, Beschreibung, Kategorie und Konto gelten für die ganze Serie, auch rückwirkend. Datum, Rhythmus und einzeln geänderte Einträge bleiben.

## 5. Transaktionen: Liste, Zeitraum und Filter

- **Liste aller Buchungen** nach Tagen gruppiert, mit Tagessumme, die neuesten zuerst. Bei mehreren Monaten ist die Liste nach Monaten getrennt.
- **Zeitraum:** dieser Monat, letzte 3 Monate, dieses Jahr oder frei von Datum bis Datum (höchstens 10 Jahre).
- **Filter:** Suche in der Beschreibung sowie Kategorie (auch „Umbuchung"), Art (wiederkehrend / einmalig) und Konto. Jede Filteroption zeigt, wie viele Treffer sie hat.
- Über der Liste stehen Anzahl der Einträge, Summe der Einnahmen und Summe der Ausgaben.

## 6. Bankkonten und Import von Kontoauszügen

- **Bankkonten** (Einstellungen › Bankkonten): mehrere Konten mit Namen und optional IBAN anlegen, eines als Standardkonto festlegen, archivieren oder löschen. Jede Buchung gehört zu einem Konto.
- **CSV-Import** (Transaktionen › Importieren) für **C24, Trade Republic, N26, Sparkasse und VR-Bank** (Volks- und Raiffeisenbanken). Die App erkennt die Bank selbst. Bei Kartenzahlungen der VR-Bank steht der Händler als Gegenseite, nicht der Zahlungsdienstleister.
- **Vorschau vor dem Übernehmen:**
  - Konten der Datei den eigenen Bankkonten zuordnen oder neu anlegen.
  - Je Zeile Datum, Beschreibung, Betrag, Kategorie und Hinweise. Nichts ist vorausgewählt; Vorschläge stehen als Knopf daneben. „Alle Vorschläge übernehmen" setzt sie alle auf einmal.
  - **Schon importierte Zeilen** erkennt die App und legt sie nicht doppelt an. Mögliche Doppelungen mit Handbuchungen werden gemeldet. Vorgemerkte Umsätze werden übersprungen.
  - **Umbuchungen** zwischen eigenen Konten erkennt die App und schlägt sie vor.
  - **Passt eine Zeile zu einem geplanten Eintrag einer Serie**, ersetzt sie ihn („ersetzt „Miete" vom 01.11."). Bei mehreren Treffern derselben Serie übernimmt eine Karte alle auf einmal.
  - **Sieht eine Gruppe von Zeilen nach einer monatlichen Serie aus**, schlägt eine Karte „Als Serie anlegen" vor. Die Serie entsteht erst beim Übernehmen.
  - Die Zeilen lassen sich wie in den Transaktionen filtern. Den angezeigten Zeilen ohne Kategorie kann man auf einmal eine Kategorie geben.
  - **„Fertige übernehmen"** speichert die Zeilen mit Kategorie, der Rest bleibt zum Weiterarbeiten. „Abbrechen" legt nichts an.
- **Import rückgängig:** Die Liste der bisherigen Importe zeigt Datum, Bank, Anzahl und Zeitraum. Ein ganzer Import lässt sich zurücknehmen, samt der Serien, die er angelegt hat.

## 7. Planung: das Soll festlegen

- **Soll je Kategorie**, gültig ab einem gewählten Monat („Gilt ab") für alle folgenden Monate, bis es geändert wird. Frühere Monate bleiben unverändert.
- **Eingabe in Euro oder in Prozent** der Einnahmen. Gespeichert wird immer der Euro-Betrag, der jeweils andere Wert steht darunter.
- **Basis wählbar:** Durchschnittseinnahmen oder Mindesteinnahmen (der schwächste der letzten 12 Monate, die vorsichtige Vorgabe).
- **„Nicht verplant":** Eine Leiste oben zeigt Basis, verplantes Soll und den Teil der Einnahmen, der noch keiner Kategorie zugeteilt ist.
- **Fixkosten je Kategorie:** Unter dem Namen steht „fix X € / Monat" aus den Serien, aufklappbar mit den einzelnen Posten; seltene Kosten sind auf den Monat umgerechnet. Liegt das Soll darunter, ist die Zeile orange. „übernehmen" setzt das Soll auf die Fixkosten.
- Zur Orientierung steht je Kategorie das **Ist im Vormonat** da.
- Gespeichert wird automatisch, sobald man das Feld verlässt.

## 8. Auswertung

- **Rückblick** über einen frei wählbaren Zeitraum (Schnellwahl 3, 6, 12 Monate oder ein Kalenderjahr):
  - Durchschnitt von Einnahmen, Ausgaben und Überschuss,
  - Balkendiagramm Einnahmen gegen Ausgaben je Monat,
  - Tabelle je Monat mit Durchschnitt und Gesamt; jeder Monat lässt sich nach Kategorien aufklappen.
- **Vorausschau mit Rücklagen** für seltene Ausgaben (z. B. jährliche Versicherung, Rundfunkbeitrag):
  - „Leg jeden Monat X € beiseite" und was davon als Nächstes fällig wird,
  - je seltene Ausgabe der Fortschritt („320 € von 480 €, noch 4 Raten à 40 €"),
  - Vorschau der Ausgaben der nächsten 12 Monate als Diagramm und Tabelle, mit dem Teil, der aus den Rücklagen bezahlt wird.
- Info-Symbole (?) erklären die Begriffe.

## 9. Einstellungen

- **Kategorien:** anlegen mit Icon, umbenennen, Icon ändern, archivieren (bleibt in alten Monaten sichtbar) oder löschen (nur ohne Buchungen). Kategorien stehen immer alphabetisch.
- **Bankkonten:** siehe Abschnitt 6.
- **Konto:** siehe Abschnitt 1.
- **Nur für Admins:**
  - **Einladungen** erzeugen.
  - **Nutzer:** Übersicht aller Konten mit Rolle, Anlagedatum und letzter Anmeldung, ohne Finanzdaten. Nutzer zu Admins machen und umgekehrt. Der Systemadmin bleibt immer Systemadmin.

## 10. Am Handy

- Bedienbar ab 360 px Breite. Am Handy führt eine Navigationsleiste unten durch die vier Bereiche, Karten ersetzen Tabellen, Knöpfe sind groß genug zum Antippen.
- Hell- und Dunkelmodus folgen der Einstellung des Geräts.
- Auf dem iPhone zoomt die App beim Antippen eines Feldes nicht mehr hinein. Der Buchungsdialog passt auf den sichtbaren Bildschirm; Titel, Schließen-Knopf und Speichern bleiben beim Scrollen erreichbar.

## 11. Datenschutz und Sicherheit (aus Nutzersicht)

- Jede Person sieht ausschließlich ihre eigenen Finanzdaten, auch Admins sehen keine fremden.
- Daten lassen sich jederzeit vollständig exportieren und löschen.
- Impressum und Datenschutzhinweis sind ohne Anmeldung erreichbar.

---

## Noch nicht verfügbar

Diese Funktionen sind fertig entwickelt und warten auf Prüfung:
- Ladeanzeige bei längeren Wartezeiten
- Anzeige, ob man in einer Test- oder Beispielumgebung arbeitet
- „Passwort vergessen" über einen Link vom Admin
- eine einzelne Buchung nachträglich zur Serie machen oder einer Serie zuordnen; zurückgestellt, siehe unten

Diese Ideen sind geplant oder in Diskussion:
- **Plan und Ist trennen:** Nur echte Buchungen zählen als Ist; Serien werden zu erwarteten Posten für Planung, Vorschau und die Erkennung von Fixkosten. Das ist die größte offene Entscheidung.
- Wöchentliche Serien; den Rhythmus einer Serie anzeigen und ändern.
- Begriffe „Fixkosten / variabel" statt „wiederkehrend / einmalig".
- Zeitraum „Gesamter Zeitraum" in den Transaktionen; ein langer Zeitraum lädt heute keine Daten und zeigt keinen Hinweis.
- Ausgaben mit anderen teilen (wie Splitwise).
- Investment-Teil: Sparpläne mit Prognose, Einmalzahlungen, Freistellungsauftrag, Depot.
- Stärkere Verschlüsselung der gespeicherten Daten.
