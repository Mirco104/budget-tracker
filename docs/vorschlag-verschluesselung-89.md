# Vorschlag zu Ticket #89: Datenbankeinträge verschlüsseln

Stand 13.11.2026. Ursprünglich Teil der Vorschläge zu #79, #86 und #89 vom 09.11.2026. #79 (wöchentlich) und #86 (fix/variabel) sind inzwischen im Konzept „Plan und Ist trennen" gelöst (`docs/konzept-plan-ist.md`, PR #105). Hier bleibt nur #89. Es ist ein Vorschlag mit offenen Fragen, noch kein Code.

---

## Was das Ticket verlangt
„Zugriff auf die Finanzdaten selbst bei kompromittiertem Server unmöglich." Das geht nur mit **Ende-zu-Ende-Verschlüsselung**: Die Schlüssel kennt nur der Browser, der Server speichert nur Geheimtext.

## Warum das hier nicht passt
- Alle Rechnungen liegen im Backend: Übersicht, Planung, Rücklagen, Import mit Dubletten-, Umbuchungs- und Serienerkennung, Kategorie-Vorschlag, Zeitraumfilter, und mit dem neuen Konzept auch die Erfüllung geplanter Termine und die automatischen Einträge. Mit Ende-zu-Ende-Verschlüsselung müssten sie alle in den Browser wandern. Das widerspricht der Architektur („Frontend ohne Geschäftsregeln") und wäre praktisch ein Neubau.
- Ein kompromittierter Server liefert auch das JavaScript aus. Er kann den Browser also dazu bringen, den Schlüssel zu verraten. Gegen einen übernommenen Server schützt deshalb auch das bei einer Web-App nur teilweise.
- Passwort vergessen (Reset-Link durch den Admin, #81) hieße dann: Die Daten sind weg, außer es gibt einen Wiederherstellungsschlüssel.

## Vorschlag in Stufen
1. **Jetzt, klein: Daten im Ruhezustand und Backups schützen.** Unabhängig vom Konzept, lässt sich jederzeit vorziehen.
   - Server-Festplatte bzw. Volume verschlüsseln (LUKS, beim Hoster „encrypted volume"). Schützt bei gestohlener Platte oder ausgemustertem Server. Gehört in `docs/betrieb.md`.
   - Backups verschlüsseln: `scripts/backup.sh` verschlüsselt jeden Dump mit `age` und einem öffentlichen Schlüssel aus `.env`, bevor `BACKUP_COPY_CMD` ihn kopiert. `scripts/restore.sh` entschlüsselt mit dem privaten Schlüssel, der nicht auf dem Server liegt. Schützt bei geleakten Backups beim Cloud-Speicher.
   - Prüfen: Die Datenbank ist nur im Docker-Netz erreichbar (in `docker-compose.yml` hat `db` keine Ports) und die App nur über Caddy (`APP_BIND=127.0.0.1`). Das ist schon so.
2. **Bei öffentlicher Nutzung, mittel bis groß: Spalten in der App verschlüsseln.**
   - Freitext und Kontodaten per AES-GCM über einen JPA-`AttributeConverter`:
     - heute: Beschreibung, Gegenseite, IBAN, Bankkategorie, Kontoname;
     - neu mit dem Konzept: Namen der geplanten Posten, der Sparziele und der Rücklagentöpfe.
   - Der Schlüssel kommt aus einer Umgebungsvariablen (`APP_DATA_KEY`) und liegt nie in der Datenbank.
   - Beträge, Daten und Kontostände bleiben im Klartext, weil Zeitraumabfragen, Sortierung und Rechnungen sie brauchen. Ohne Text sind sie wenig aussagekräftig.
   - Schützt bei einem Datenbank-Dump, einem Zugriff nur auf die Datenbank oder einem Hoster-Admin mit Blick in die DB. Schützt nicht, wenn die App selbst übernommen wird, denn sie kennt den Schlüssel.
   - Aufwand: Converter, eine Flyway-Java-Migration, die bestehende Daten verschlüsselt, ein Konzept für Schlüsselwechsel. Keine Abfrage nutzt heute `like`; Suche, Kategorie-Vorschlag und die Treffer-Regel für geplante Termine laufen in der App und funktionieren deshalb weiter.
3. **Nicht empfohlen: Ende-zu-Ende (Zero Knowledge).** Sehr groß, siehe oben.

## Offene Fragen
- Welche Gefahr ist dir am wichtigsten: gestohlenes Backup, neugieriger Hoster, gehackter Server?
- Wo läuft die App (Entscheidung 21 ist noch offen)? Davon hängt Stufe 1 ab (verschlüsseltes Volume beim Hoster oder LUKS selbst).
- Wäre Datenverlust bei vergessenem Passwort akzeptabel? Nur dann ist Stufe 3 überhaupt denkbar.
