# Änderungen

Was sich am Budget-Tracker geändert hat, das Neueste zuerst. Die App zeigt diese Liste beim Klick auf die Version in der Fußzeile.

## 0.3.0-alpha (noch nicht veröffentlicht)

Einrichtung für neue Nutzer und vier Stufen, wie genau man sein Ist verfolgt.

### Neu

- **Einrichtung:** Neue Nutzer werden Schritt für Schritt durch Konten, Einkommen, Fixkosten, variable Kosten, Sparen und Sparziele geführt, wahlweise mit Vorschlägen aus einem Kontoauszug. Jeder Schritt hat ein Icon und eine kurze Frage, unten füllt sich ein Balken mit dem Plan, häufige Posten stehen als Kacheln zum Antippen bereit. Der Import kommt nach der Wahl der Stufe und speichert nur, was die Stufe braucht: in Stufe 1 keine Buchung, in Stufe 2 nur die regelmäßigen Buchungen. Liefert die Bank einen Kontostand (Deutsche Bank, VR-Bank), bietet der Import an, ihn zu übernehmen. Bestehende Nutzer sehen sie einmal mit ihren Daten; „Später einrichten“ schließt sie ohne Änderung, unter *Einstellungen › Ist verfolgen* lässt sie sich jederzeit neu starten.
- **Ist verfolgen in vier Stufen:** Nur Plan, Kontostand, Buchungen oder Buchungen mit Kategorien, jeweils mit Bild, Häkchen, Aufwand und Genauigkeit. Zwei Fragen (was du wissen willst, wie viel Zeit du investierst) empfehlen eine Stufe. In Stufe 1 trägt die App nichts ein; beim Wechsel aus Stufe 1 fragt sie, ob und ab wann sie Einkommen und Fixkosten selbst eintragen soll.
- **Budget-Vorschläge:** Die Einrichtung schlägt Budgets aus dem Durchschnitt der letzten 6 Monate je Kategorie vor.
- **Dein Monat im Plan:** Am Ende der Einrichtung zeigt ein Diagramm, wohin das Einkommen im Plan fließt.
- **Übersicht je Stufe:** Stufe 1 zeigt nur den Plan des Monats, Stufe 2 nach einem Kontostand (an jedem Tag möglich), was seit dem Kontostand davor variabel ausgegeben wurde (oder „mehr als erwartet“), Stufe 3 alle Budgets zusammen, Stufe 4 wie bisher jedes Budget einzeln.
- **Glocke fragt nach dem Kontostand:** In Stufe 2 erinnert die Glocke an den Kontostand vom letzten Tag des Vormonats.
- **Buchungen ohne Kategorie:** Im Buchungsdialog und im Import ist die Kategorie keine Pflicht mehr. Ausgaben ohne Kategorie stehen auf der Übersicht und in der Auswertung je Kategorie in einer eigenen Zeile „Ohne Kategorie“ und zählen zu keinem Budget. In Transaktionen lassen sie sich mit dem Kategorie-Filter „Ohne Kategorie“ finden. Unter Stufe 4 fragen Import und Buchungsdialog statt nach der Kategorie nur, ob eine Buchung zählt, eine Umbuchung oder Sparen ist.

### Änderungen

- Während der Einrichtung sind Glocke und Profilmenü ausgeblendet. Auf der Seite „Fertig“ wächst das Sankey-Diagramm mit der Karte statt zu scrollen, und die Karten unter „Deine Einrichtung“ sind gleich hoch.
- **Nur die nötigen Seiten:** In Stufe 1 sind *Transaktionen* und *Auswertung* ausgeblendet, die Unterseite *Auswertung › Kategorien* gibt es nur in Stufe 4.
- **Kategorien der Bank nur als Vorschlag:** Die Karte „Kategorien der Bank“ im Import und die gespeicherten Zuordnungen entfallen; die Kategorie der Bank fließt nur noch in den Vorschlag ein, gleich nach der sicheren Zuordnung aus der Gegenseite.

## 0.2.0-alpha (21.11.2026)

Nachbesserungen nach dem ersten Test im Bekanntenkreis.

### Neu

- **Liste der Änderungen:** Ein Klick auf die Version in der Fußzeile zeigt diese Liste.
- **Auswertung je Kategorie:** Die neue Unterseite *Kategorien* zeigt für den Zeitraum des Rückblicks je Kategorie Summe und Durchschnitt pro Monat, für Ausgaben oder Einnahmen, und den Verlauf einer gewählten Kategorie als Diagramm.
- **Filter „Importiert“:** Auf *Transaktionen* lassen sich Buchungen danach filtern, ob sie aus einem Import stammen.
- **Mehr Filter im Import:** Einnahmen oder Ausgaben, übernommen oder nicht, und „Filter aktualisieren“, wenn eine bearbeitete Zeile nicht mehr zum Filter passt.
- **Gleiche Zeilen im Import:** Nach der Wahl einer Kategorie bietet der Import an, sie auch den weiteren Zeilen mit derselben Beschreibung (und IBAN) zu geben.

### Änderungen

- **Import als Tabelle:** Die Zeilen des Imports stehen in festen Spalten; ein Schalter am Ende jeder Zeile sagt, ob sie übernommen wird, getrennt vom Auswählen mehrerer Zeilen.
- **Bessere Erkennung geplanter Termine im Import:** Der Verwendungszweck zählt mit (z. B. „Lohn/Gehalt“), ein Gehalt darf bis zu 10 Tage vor dem Termin kommen und wird nicht mehr doppelt gebucht, und Fixkosten mit festem Betrag werden auch ohne passenden Namen erkannt.
- **Schon importierte Datei:** Bringt eine Datei nichts Neues, sagt der Import das deutlich, statt nur ausgegraute Zeilen zu zeigen.
- **Abhaken als Vorgabe:** Neue Posten werden standardmäßig abgehakt statt automatisch eingetragen; bestehende Posten bleiben, wie sie sind.
- **Verständliche Fehlermeldung:** Bei einem unerwarteten Fehler des Servers steht „Etwas ist schiefgelaufen. Bitte versuche es noch einmal.“ statt „Unbekannter Fehler“.
- **Kleinere Korrekturen:** Der Knopf „Monatlich sparen“ bleibt beim Darüberfahren lesbar; bei der lokalen Entwicklung braucht es nach einer neuen Datenbank-Version keinen Neustart mehr.

## 0.1.0-alpha (15.11.2026)

Die erste Version für den Test im Bekanntenkreis.

### Neu

- **Anmeldung und Konto:** Anmelden mit E-Mail und Passwort, Einladung per Link, Passwort vergessen über einen Link vom Admin, eigene Daten herunterladen oder löschen. Jede Person sieht nur ihre eigenen Daten.
- **Übersicht:** Der Monat auf einen Blick mit Einnahmen, Ausgaben und Plan gegen Ist je Kategorie; eine Glocke meldet Kontostände unter der Untergrenze, geänderte Preise und eine überplante Planung.
- **Buchen:** Einnahmen, Ausgaben, Sparen und Umbuchungen zwischen eigenen Konten, mit Kategorie-Vorschlag aus den eigenen Buchungen; mehrere Buchungen auf einmal einer Kategorie zuordnen.
- **Planung:** Ein normaler Monat aus einzelnen Posten (Einkommen, Fixkosten, Budgets, Sparen) mit Rhythmus wöchentlich bis jährlich, Rücklagen für seltene Ausgaben und Sparziele; Vorschläge aus den eigenen Buchungen.
- **Erwartet und Abhaken:** Geplante Termine erscheinen als erwartet und werden abgehakt, automatisch eingetragen oder von einer importierten Buchung erfüllt.
- **Konten:** Alltags-, Spar- und Depotkonten mit Kontostand, Verlauf der Kontostände und Untergrenze; zwei Konten lassen sich zusammenführen.
- **Import von Kontoauszügen:** CSV von C24, Trade Republic, N26, Sparkasse, VR-Bank und Deutscher Bank mit Vorschau, Erkennung von Duplikaten, Umbuchungen und Serien, Kategorien der Bank und Rückgängig je Import.
- **Kategorien:** Mit Icon und Unterkategorien, sortierbar, archivieren oder löschen.
- **Auswertung:** Rückblick über einen frei wählbaren Zeitraum und Vorausschau mit Rücklagen.
- **Am Handy:** Bedienbar ab 360 px Breite, Hell- und Dunkelmodus nach Einstellung des Geräts.
