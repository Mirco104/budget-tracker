# Vorschläge zu den Tickets #79, #86 und #89

Stand 09.11.2026, Nachtlauf. Diese drei Tickets ändern Regeln der Spezifikation oder die Architektur. Deshalb steht hier nur ein Vorschlag mit offenen Fragen, noch kein Code. Wenn die Fragen beantwortet sind, wandert die Entscheidung in `docs/spezifikation.md` (Abschnitt 11 und die betroffenen Regeln), danach folgt die Umsetzung.

---

## #79 Wöchentlich wiederkehrende Transaktionen

### Ausgangslage
Eine Serie kennt nur Intervalle in Monaten (R12: monatlich, vierteljährlich, halbjährlich, jährlich). `RecurringInterval` speichert die Zahl der Monate, und `RecurrenceSchedule` rechnet mit `plusMonths`. Die Datenbank erlaubt nur diese vier Werte (`ck_recurring_transactions_interval` in V1).

### Vorschlag
1. **Neues Intervall `WEEKLY` ("wöchentlich")**, optional zusätzlich `BIWEEKLY` ("alle 2 Wochen").
   - R12: Die Vorkommen liegen am Startdatum plus k × 7 Tage (bzw. 14 Tage). Eine Monatsende-Regel braucht es dafür nicht.
   - `RecurringInterval` bekommt eine Einheit (Monate oder Tage). `RecurrenceSchedule` rechnet je nach Einheit mit `plusMonths` oder `plusDays`. `isOccurrence` und `previousOccurrence` funktionieren dann wie bisher.
2. **Monatsanteil** (`monthlyEquivalent`, R25 Fixkosten, "Pro Monat" in R14): |Betrag| × 52 / 12, bei zwei Wochen × 26 / 12. Gerundet wird erst am Ende (Skala 2, HALF_UP), wie bei den anderen Intervallen. Wie schon besprochen.
3. **Ist im Monat (R1, R10):** Ein Monat hat 4 oder 5 Vorkommen und zählt sie alle mit vollem Betrag. Das entspricht dem echten Geldfluss (Entscheidung 6). Die Planung nutzt den Monatsanteil.
4. **Rücklagen (R14):** Wöchentliche Serien kommen nicht in den Topf, er ist nur für Intervalle über einem Monat. Wie schon besprochen.
5. **Ist ersetzt Plan (R28):** Bei 7 Tagen Abstand würde die Toleranz von ±5 Tagen das Nachbarvorkommen treffen. Vorschlag: Toleranz = min(5, Abstand in Tagen ÷ 2, abgerundet), also ±3 Tage bei wöchentlich.
6. **Serienerkennung im Import (R31):** Sie erkennt weiter nur monatliche Serien. Eine wöchentliche Erkennung ist ein eigener zweiter Schritt (`SeriesDetector` mit 7-Tage-Raster).
7. **Datenbank:** neue Migration V13, die den Check-Constraint um `WEEKLY` (und ggf. `BIWEEKLY`) erweitert. V1 bleibt unverändert, gemergte Migrationen werden nie geändert.
8. **Oberfläche:** `INTERVAL_LABELS` um "wöchentlich" erweitern. In der Rücklagen-Ansicht taucht eine wöchentliche Serie nicht auf.

### Tests (zuerst)
- `RecurrenceSchedule`: wöchentlich über Monatsgrenzen und Jahreswechsel, Enddatum einschließlich, Ausnahmen.
- `FixedCosts`: 10 € wöchentlich → 43,33 €/Monat.
- `ReserveCalculator`: eine wöchentliche Serie bleibt draußen.
- `OccurrenceMatcher`: ±3 Tage bei wöchentlich, ±5 bei monatlich.

### Aufwand
Mittel bis groß, etwa ein Nachtlauf (Backend-Rechenklassen, Migration, Frontend-Label, Tests).

### Offene Fragen
- Auch "alle 2 Wochen"? (Taschengeld, Putzhilfe)
- Ist ±3 Tage für den Treffer bei wöchentlich in Ordnung?
- Soll der Import später auch wöchentliche Serien vorschlagen?

---

## #86 Fixkosten und variable Kosten statt "wiederkehrend"

### Ausgangslage
Entscheidung 17: Fix oder flexibel hängt an der Zahlung, nicht an der Kategorie. Was regelmäßig abgebucht wird, ist eine Serie (R12). Die Übersicht zeigt "davon wiederkehrend" und "davon einmalig" (R10). Der Filter *Art* in den Transaktionen heißt "Wiederkehrend" / "Einmalig". Eine Buchung, die ein Vorkommen ersetzt (R28), zählt als wiederkehrend.

### Vorschlag
1. **Nur die Begriffe ändern, die Rechnung bleibt (klein):**
   - Übersicht: "davon Fixkosten" / "davon variabel" statt "wiederkehrend" / "einmalig". Ein Info-Tipp erklärt: "Fixkosten sind Ausgaben aus Serien, z. B. Miete, Versicherungen, Abos."
   - Filter *Art* in Transaktionen und Import: "Fix (Serie)" / "Variabel". Bei Einnahmen steht dort "Regelmäßig" / "Einmalig", weil "Fixkosten" für Einnahmen falsch klingt.
   - Planung: "Fixe Kosten" heißt dort schon so (R25), das passt dann zusammen.
2. **"Zuverlässig erkannt"** hängt daran, dass jede regelmäßige Zahlung auch als Serie angelegt ist. Die Hebel dafür:
   - schon da: Serienvorschlag im Import (R31) und Treffer auf bestehende Serien (R28);
   - in diesem Nachtlauf: "Gehört zu einer Serie" (#91) und "Buchung zur Serie machen" (#76);
   - neu vorgeschlagen (mittel): **Serien im ganzen Verlauf erkennen** (offener Punkt in Abschnitt 16). Die *Planung* zeigt eine Karte "Diese Buchungen sehen regelmäßig aus", berechnet mit `SeriesDetector` über die letzten 12 Monate. Ein Klick legt daraus eine Serie an (wie #76).
3. **Filter "Fixkosten" in den Transaktionen:** Das ist der Filter *Art* mit dem neuen Namen. Einen eigenen Filter braucht es nicht.

### Aufwand
Teil 1 klein (Texte, Tests anpassen, Spec R10, US8, US26). Teil 2 mittel (Erkennung im Verlauf, neue Karte in der Planung).

### Offene Fragen
- Sind Sparraten (ETF-Sparplan, Kategorie *Investitionen*) Fixkosten? Heute zählen sie als wiederkehrende Ausgabe. Vorschlag: Sie bleiben Fixkosten, weil sie fest abgehen; mit AP 21 (Investment) bekommen sie eine eigene Zeile.
- Sollen auch die Einnahmen in "regelmäßig" und "einmalig" aufgeteilt werden?
- Soll Teil 2 (Erkennung im Verlauf) gleich mitkommen oder später?

---

## #89 Datenbankeinträge verschlüsseln

### Was das Ticket verlangt
"Zugriff auf die Finanzdaten selbst bei kompromittiertem Server unmöglich." Das geht nur mit **Ende-zu-Ende-Verschlüsselung**: Die Schlüssel kennt nur der Browser, der Server speichert nur Geheimtext.

### Warum das hier nicht passt
- Alle Rechnungen liegen im Backend (Dashboard, Rücklagen, Fixkosten, Import mit Dubletten-, Umbuchungs- und Serienerkennung, Kategorie-Vorschlag, Zeitraumfilter). Mit Ende-zu-Ende-Verschlüsselung müssten sie alle in den Browser wandern. Das widerspricht der Architektur ("Frontend ohne Geschäftsregeln") und wäre praktisch ein Neubau.
- Ein kompromittierter Server liefert auch das JavaScript aus. Er kann den Browser also dazu bringen, den Schlüssel zu verraten. Gegen einen übernommenen Server schützt deshalb auch das bei einer Web-App nur teilweise.
- Passwort vergessen (#81) hieße dann: Die Daten sind weg, außer es gibt einen Wiederherstellungsschlüssel.

### Vorschlag in Stufen
1. **Jetzt, klein: Daten im Ruhezustand und Backups schützen.**
   - Server-Festplatte bzw. Volume verschlüsseln (LUKS, beim Hoster "encrypted volume"). Schützt bei gestohlener Platte oder ausgemustertem Server. Gehört in `docs/betrieb.md`.
   - Backups verschlüsseln: `scripts/backup.sh` verschlüsselt jeden Dump mit `age` und einem öffentlichen Schlüssel aus `.env`, bevor `BACKUP_COPY_CMD` ihn kopiert. `scripts/restore.sh` entschlüsselt mit dem privaten Schlüssel, der nicht auf dem Server liegt. Schützt bei geleakten Backups beim Cloud-Speicher.
   - Prüfen: Die Datenbank ist nur im Docker-Netz erreichbar (in `docker-compose.yml` hat `db` keine Ports) und die App nur über Caddy (`APP_BIND=127.0.0.1`). Das ist schon so.
2. **Bei öffentlicher Nutzung, mittel bis groß: Spalten in der App verschlüsseln.**
   - Freitext und Kontodaten (Beschreibung, Gegenseite, IBAN, Bankkategorie, Kontoname) per AES-GCM über einen JPA-`AttributeConverter`. Der Schlüssel kommt aus einer Umgebungsvariablen (`APP_DATA_KEY`) und liegt nie in der Datenbank.
   - Beträge und Daten bleiben im Klartext, weil Zeitraumabfragen und Sortierung sie brauchen. Ohne Text sind sie wenig aussagekräftig.
   - Schützt bei einem Datenbank-Dump, einem Zugriff nur auf die Datenbank oder einem Hoster-Admin mit Blick in die DB. Schützt nicht, wenn die App selbst übernommen wird, denn sie kennt den Schlüssel.
   - Aufwand: Converter, eine Flyway-Java-Migration, die bestehende Daten verschlüsselt, ein Konzept für Schlüsselwechsel. Keine Abfrage nutzt heute `like`; Suche und Kategorie-Vorschlag laufen schon in der App und funktionieren deshalb weiter.
3. **Nicht empfohlen: Ende-zu-Ende (Zero Knowledge).** Sehr groß, siehe oben.

### Offene Fragen
- Welche Gefahr ist dir am wichtigsten: gestohlenes Backup, neugieriger Hoster, gehackter Server?
- Wo läuft die App (Entscheidung 21 ist noch offen)? Davon hängt Stufe 1 ab (verschlüsseltes Volume beim Hoster oder LUKS selbst).
- Wäre Datenverlust bei vergessenem Passwort akzeptabel? Nur dann ist Stufe 3 überhaupt denkbar.
