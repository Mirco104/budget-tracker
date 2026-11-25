# Änderungen

Was sich am Budget-Tracker geändert hat, das Neueste zuerst. Die App zeigt diese Liste beim Klick auf die Version in der Fußzeile.

## 0.5.0-alpha (unveröffentlicht)

Anmeldung mit Passkey, Sparziele auf Konten und Verbesserungen am Import.

### Neu

- **Anmelden mit Passkey:** Unter Einstellungen › Konto fügst du im Abschnitt „Passkeys“ einen Passkey für dein Gerät hinzu. Danach meldest du dich mit „Mit Passkey anmelden“ per Fingerabdruck, Gesicht oder Geräte-PIN an, ohne E-Mail und Passwort einzutippen. Dein Passwort bleibt gültig. Passkeys lassen sich umbenennen und löschen.
- **Sparziel auf einem Konto:** Ein Sparziel kann auf einem Konto liegen. *Konten* zeigt dann, ob das Konto genug für seine Rücklagen und Sparziele hat. Bezahlst du etwas aus dem Sparziel, schlägt der Buchungsdialog dieses Konto vor.
- **Alle auf- und zuklappen:** Über der Budget-Tabelle der Übersicht und über Fixkosten und Budgets in der Planung klappt „Alle aufklappen“ alle Kategorien auf einmal auf, „Alle zuklappen“ wieder zu.

### Änderungen

- **Import bleibt offen:** Wechselst du während eines Imports auf eine andere Seite, bleiben die Vorschau und alle Entscheidungen erhalten. *Transaktionen* zeigt dann „Ein Import ist noch offen“ mit einem Link zurück. Neu laden verwirft den Import wie bisher.
- **Budget-Vorschläge ausblenden:** In der Einrichtung lässt sich ein Vorschlag aus deinen Buchungen im Schritt *Variable Kosten* neben „Übernehmen“ auch „Ausblenden“.
- **Filter im Import:** Die Art heißt im Import wie auf *Transaktionen* „Fix“ und „Variabel“ statt „Wiederkehrend“ und „Einmalig“, ohne Wahl „Fix und variabel“ statt „Alle Arten“. Der Filter nach dem Schalter der Zeile heißt „Übernehmen: alle“ statt „Übernehmen und nicht“.

## 0.4.0-alpha (25.11.2026)

Import von PayPal.

### Neu

- **PayPal-Import:** Der Import liest den CSV-Export der PayPal-Aktivitäten. Gebucht wird der Nettobetrag, also was auf dem PayPal-Konto ankommt oder abgeht; ein Entgelt steht unter „Details“. Der Import bietet das Guthaben der Datei als Kontostand des PayPal-Kontos an. Einbehaltungen und Freigaben werden übersprungen.
- **PayPal-Aufladungen als Umbuchung:** Zahlst du mit PayPal per Lastschrift oder Karte, erkennt der Import die Aufladung („Aufladung für Händler“) und eine Auszahlung auf dein Bankkonto als Umbuchung. Die Lastschrift auf dem Girokonto wird ihre Gegenbuchung, auch wenn sie bis zu 10 Tage später gebucht ist; so zählt nur die Zahlung an den Händler.
- **Händler bei PayPal-Lastschriften:** Eine Lastschrift von PayPal auf dem Girokonto heißt im Import nach dem Händler („Prezi Inc · PayPal“), wenn die Bank ihn im Verwendungszweck nennt. Der Kategorie-Vorschlag richtet sich dann nach dem Händler statt nach „PayPal“, auch ohne PayPal-Import.
- **PayPal in anderer Währung:** Zahlungen in Dollar und anderen Währungen zählen mit dem Euro-Betrag der Umrechnung und dem Namen des Händlers.

## 0.3.1-alpha (23.11.2026)

Nachbesserungen nach der ersten Nutzung von 0.3.0.

### Änderungen

- **Einrichtung abschließen:** Am Handy führte „Einrichtung abschließen“ manchmal zurück an den Anfang der Einrichtung. Das ist behoben.
- **Dein Monat im Plan am Handy:** Statt des Diagramms, dessen Beschriftungen sich am schmalen Bildschirm überlagerten, zeigt die Seite „Fertig“ am Handy eine Liste: je Bereich die Summe, ein Balken und die Kategorien darunter.
- **Balken der Einrichtung:** Das Einkommen steht über dem Balken, die Beträge darunter stehen in einem geordneten Raster. Am Handy nimmt die Leiste unten weniger Platz ein.
- **Puffer rückgängig machen:** Nach „Als Puffer in „Sonstiges““ lässt sich der Schritt in der Einrichtung und in der Planung mit „Rückgängig“ zurücknehmen.
- **Kontostand mit Datum:** Im Fenster eines Kontos stehen Kontostand und Datum zusammen in einem Kasten, darunter der letzte Kontostand. Ein neues Konto kann gleich seinen ersten Kontostand bekommen.
- **Kontoarten erklärt:** Ein Satz unter der Kontoart und ein Info-Tipp auf der Seite *Konten* sagen, was Alltag, Sparen und Depot bewirken.
- **Zahltag eines Postens:** Das Feld „Am Tag“ liest sich jetzt als Satz: „Zahltag: am 1. des Monats“.
- **Plan selbst eintragen:** Die Einrichtung sagt dazu, dass Kontoauszüge später jederzeit importiert werden können.

## 0.3.0-alpha (22.11.2026)

Einrichtung für neue Nutzer und vier Stufen, wie genau man sein Ist verfolgt.

### Neu

- **Einrichtung:** Neue Nutzer werden Schritt für Schritt durch Konten, Einkommen, Fixkosten, variable Kosten, Sparen und Sparziele geführt, wahlweise mit Vorschlägen aus einem Kontoauszug. Jeder Schritt hat ein Icon und eine kurze Frage, unten füllt sich ein Balken mit dem Plan, häufige Posten stehen als Kacheln zum Antippen bereit. Wer schon Buchungen hat, kann sich den Plan auch aus ihnen vorschlagen lassen. Der Import kommt nach der Wahl der Stufe und speichert nur, was die Stufe braucht: in Stufe 1 keine Buchung, in Stufe 2 nur die regelmäßigen Buchungen. Liefert die Bank einen Kontostand (Deutsche Bank, VR-Bank), bietet der Import an, ihn zu übernehmen. Bestehende Nutzer sehen sie einmal mit ihren Daten; „Später einrichten“ schließt sie ohne Änderung, unter *Einstellungen › Einrichtung* lässt sie sich jederzeit neu starten.
- **Tracking-Stufe in vier Stufen:** Nur Plan, Kontostand, Buchungen oder Buchungen mit Kategorien, jeweils mit Bild, Häkchen, Aufwand und Genauigkeit. Zwei Fragen (was du wissen willst, wie viel Zeit du investierst) empfehlen eine Stufe. In Stufe 1 trägt die App nichts ein; beim Wechsel aus Stufe 1 fragt sie, ob und ab wann sie Einkommen und Fixkosten selbst eintragen soll.
- **Budget-Vorschläge:** Die Einrichtung schlägt Budgets aus dem Durchschnitt der letzten 6 Monate je Kategorie vor.
- **Dein Monat im Plan:** Am Ende der Einrichtung zeigt ein Diagramm, wohin das Einkommen im Plan fließt.
- **Übersicht je Stufe:** Stufe 1 zeigt nur den Plan des Monats, Stufe 2 nach einem Kontostand (an jedem Tag möglich), was seit dem Kontostand davor variabel ausgegeben wurde (oder „mehr als erwartet“), Stufe 3 alle Budgets zusammen, Stufe 4 wie bisher jedes Budget einzeln.
- **Glocke fragt nach dem Kontostand:** In Stufe 2 erinnert die Glocke an den Kontostand vom letzten Tag des Vormonats, sobald das Konto davor schon einen Kontostand hatte. Der erste Kontostand ist der Start; die Übersicht bittet dann um den nächsten am Monatsende und kennzeichnet einen angefangenen Monat als „Teilmonat“.
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
