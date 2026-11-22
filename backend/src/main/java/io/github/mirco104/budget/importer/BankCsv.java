package io.github.mirco104.budget.importer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.mirco104.budget.importer.BankTransaction.Status;

/**
 * Reads the CSV export of a bank, rule R30 from docs/spezifikation.md, section 2. No Spring dependencies. The file is
 * decoded (UTF-8, else Windows-1252), split into records (";" or ","), the header found among the first 20 records
 * and every following record read with the profile of that header into a normalized BankTransaction (decision 46).
 * Only the Deutsche Bank reads the preamble before the header (the IBAN of the account) and has a footer that is
 * skipped (decision 76).
 */
public final class BankCsv {

    /** The header must be among the first records (R30). */
    static final int HEADER_SEARCH = 20;

    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    // Deutsche Bank (decision 76): "30.6.2026", leading zeros allowed ("30.06.2026")
    private static final DateTimeFormatter SHORT_DAY_MONTH_DATE = DateTimeFormatter.ofPattern("d.M.yyyy");
    // Deutsche Bank: the IBAN of the account from the preamble, under a key no column can have
    private static final String PREAMBLE_IBAN = "\u0000preamble:iban";
    private static final Pattern GERMAN_NUMBER = Pattern.compile("[+-]?\\d+(,\\d+)?");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");
    private static final String NO_AMOUNT = "ohne Betrag";
    // Sparkasse: "05.11.26" is 05.11.2026 (years 2000 to 2099)
    private static final DateTimeFormatter SHORT_GERMAN_DATE = new DateTimeFormatterBuilder()
            .appendPattern("dd.MM.")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter(Locale.ROOT);
    // SEPA tags in the purpose of CSV-MT940 ("EREF+…MREF+…CRED+…SVWZ+…")
    private static final Pattern SEPA_TAG = Pattern.compile("(EREF|KREF|MREF|CRED|DEBT|SVWZ|ABWA|ABWE|IBAN|BIC|COAM|OAMT)\\+");
    // VR-Bank (decision 64): SEPA tags with ": " after the purpose ("… EREF: … MREF: … IBAN: … BIC: …")
    private static final Pattern COLON_TAG = Pattern.compile("(?<!\\S)(EREF|KREF|MREF|CRED|DEBT|SVWZ|ABWA|ABWE|IBAN|BIC): ");
    // VR-Bank: the card data from date and time on, of a card payment ("09.11.2026 um 10:54:09 Uhr …") or at a cash
    // machine ("06.11.2026/18:25 girocard …")
    private static final Pattern CARD_DATA = Pattern.compile(
            "\\s+\\d{2}\\.\\d{2}\\.\\d{4}(?: um \\d{2}:\\d{2}:\\d{2} Uhr|/\\d{2}:\\d{2})(?:\\s.*)?$");
    // VR-Bank: the mark of a standing order ("Miete /*DA-1*")
    private static final Pattern STANDING_ORDER = Pattern.compile("\\s*/\\*DA-\\d+\\*");

    /**
     * A record after the header: its line in the file and either the normalized booking (decision 46) or why it could
     * not be read. Whether a booking is imported is up to the rules (ImportRules.skipReason).
     */
    public record Line(int number, BankTransaction row, String skipReason) {
    }

    /**
     * Decision 92: the balance of an account the file names (accountName as in its rows) at the end of a day; only the
     * Deutsche Bank (footer) and the VR-Bank (column "Saldo nach Buchung") deliver one.
     */
    public record Balance(String accountName, LocalDate date, BigDecimal amount) {
    }

    public record BankFile(BankProfile profile, List<Line> lines, List<Balance> balances) {
    }

    /** The file has no header of a known profile among its first records. */
    public static class UnknownFormatException extends RuntimeException {
        UnknownFormatException() {
            super("Das Format der Datei ist unbekannt. Unterstützt werden CSV-Exporte von C24, Trade Republic, N26, der "
                    + "Sparkasse, der VR-Bank und der Deutschen Bank.");
        }
    }

    private record Record(int line, List<String> fields) {
    }

    private BankCsv() {
    }

    public static BankFile read(byte[] bytes) {
        String text = decode(bytes);
        for (char delimiter : new char[] {';', ','}) {
            List<Record> records = parse(text, delimiter);
            for (int i = 0; i < Math.min(HEADER_SEARCH, records.size()); i++) {
                List<String> header = records.get(i).fields().stream().map(BankCsv::clean).toList();
                for (BankProfile profile : BankProfile.values()) {
                    if (hasColumns(header, profile.requiredColumns())) {
                        List<Balance> balances = new ArrayList<>();
                        return new BankFile(profile, readLines(profile, header, records.subList(0, i),
                                records.subList(i + 1, records.size()), balances), balances);
                    }
                }
            }
        }
        throw new UnknownFormatException();
    }

    // --- decoding and records ---

    // UTF-8 (a byte order mark is dropped) when the bytes are valid UTF-8, else Windows-1252
    static String decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    /** The fields of every record that is not empty; quoted fields may hold the delimiter, "" and line breaks. */
    static List<List<String>> records(String text, char delimiter) {
        return parse(text, delimiter).stream().map(Record::fields).toList();
    }

    private static List<Record> parse(String text, char delimiter) {
        List<Record> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int recordLine = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\r') {
                // Line breaks are \n or \r\n
            } else if (c == '\n') {
                fields.add(field.toString());
                field.setLength(0);
                addRecord(records, recordLine, fields);
                fields = new ArrayList<>();
                line++;
                recordLine = line;
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            addRecord(records, recordLine, fields);
        }
        return records;
    }

    // Empty lines (only empty fields) are skipped
    private static void addRecord(List<Record> records, int line, List<String> fields) {
        if (fields.stream().anyMatch(value -> !value.isBlank())) {
            records.add(new Record(line, List.copyOf(fields)));
        }
    }

    private static String clean(String value) {
        return value.replace("﻿", "").trim();
    }

    private static boolean hasColumns(List<String> header, List<String> required) {
        return required.stream().allMatch(column -> header.stream().anyMatch(name -> name.equalsIgnoreCase(column)));
    }

    // --- rows per profile ---

    // balances collects the balances the file delivers (decision 92)
    private static List<Line> readLines(BankProfile profile, List<String> header, List<Record> preamble,
            List<Record> records, List<Balance> balances) {
        String preambleIban = profile == BankProfile.DEUTSCHE_BANK ? preambleIban(preamble) : null;
        List<Line> lines = new ArrayList<>();
        // VR-Bank: per account the "Saldo nach Buchung" of its newest booked row; on the same day the first one, as
        // the export lists the newest first
        Map<String, Balance> newest = new java.util.LinkedHashMap<>();
        for (Record record : records) {
            if (profile == BankProfile.DEUTSCHE_BANK && clean(record.fields().getFirst()).equalsIgnoreCase("Kontostand")) {
                // the footer with the balance is no booking (decision 76), but the balance of the account (decision 92)
                footerBalance(record, preambleIban).ifPresent(balances::add);
                continue;
            }
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                values.put(header.get(i).toLowerCase(Locale.ROOT), i < record.fields().size() ? record.fields().get(i).trim() : "");
            }
            if (preambleIban != null) {
                values.put(PREAMBLE_IBAN, preambleIban);
            }
            try {
                BankTransaction row = read(profile, values);
                lines.add(new Line(record.line(), row, null));
                if (profile == BankProfile.VR_BANK && row.bookingDate() != null) {
                    String text = get(values, "Saldo nach Buchung");
                    BigDecimal saldo = text == null ? null : germanAmount(text);
                    Balance known = newest.get(row.accountName());
                    if (saldo != null && (known == null || row.bookingDate().isAfter(known.date()))) {
                        newest.put(row.accountName(), new Balance(row.accountName(), row.bookingDate(), saldo));
                    }
                }
            } catch (SkipException skip) {
                lines.add(new Line(record.line(), null, skip.getMessage()));
            }
        }
        balances.addAll(newest.values());
        return lines;
    }

    // Deutsche Bank footer "Kontostand;<Tag>;;;<Betrag>;EUR" (decision 92); empty when it cannot be read
    private static java.util.Optional<Balance> footerBalance(Record record, String account) {
        List<String> fields = record.fields();
        if (account == null || fields.size() < 5) {
            return java.util.Optional.empty();
        }
        BigDecimal amount = germanAmount(clean(fields.get(4)));
        try {
            LocalDate date = LocalDate.parse(clean(fields.get(1)), SHORT_DAY_MONTH_DATE);
            return amount == null ? java.util.Optional.empty() : java.util.Optional.of(new Balance(account, date, amount));
        } catch (java.time.format.DateTimeParseException e) {
            return java.util.Optional.empty();
        }
    }

    // Deutsche Bank: the preamble has a line with the column names "Konto;Filial-/Kontonummer;IBAN;Währung" and the
    // values in the line below; null without such lines
    private static String preambleIban(List<Record> preamble) {
        for (int r = 0; r + 1 < preamble.size(); r++) {
            List<String> names = preamble.get(r).fields();
            for (int i = 0; i < names.size(); i++) {
                List<String> values = preamble.get(r + 1).fields();
                if (clean(names.get(i)).equalsIgnoreCase("IBAN") && i < values.size() && !values.get(i).isBlank()) {
                    return values.get(i).replaceAll("\\s", "");
                }
            }
        }
        return null;
    }

    private static final class SkipException extends RuntimeException {
        SkipException(String reason) {
            super(reason, null, false, false);
        }
    }

    private static BankTransaction read(BankProfile profile, Map<String, String> values) {
        return switch (profile) {
            case C24 -> c24(values);
            case TRADE_REPUBLIC -> tradeRepublic(values);
            case N26 -> n26(values);
            case SPARKASSE -> sparkasse(values);
            case VR_BANK -> vrBank(values);
            case DEUTSCHE_BANK -> deutscheBank(values);
        };
    }

    // Deutsche Bank (decision 76): d.M.yyyy, signed German amounts in Betrag (empty: Haben, or Soll as a debit), the
    // account from the preamble; the export holds no pending bookings
    private static BankTransaction deutscheBank(Map<String, String> v) {
        LocalDate date = date(get(v, "Buchungstag"), SHORT_DAY_MONTH_DATE);
        String valueText = get(v, "Wert");
        LocalDate valueDate = valueText == null ? null : date(valueText, SHORT_DAY_MONTH_DATE);
        String account = orEmpty(v.get(PREAMBLE_IBAN));
        String kind = get(v, "Umsatzart");
        String currency = get(v, "Währung");
        return new BankTransaction(
                account, account.isEmpty() ? null : account,
                date, valueDate, deutscheBankAmount(v), currency != null ? currency.toUpperCase(Locale.ROOT) : "EUR",
                Status.BOOKED,
                get(v, "Begünstigter / Auftraggeber"), get(v, "IBAN / Kontonummer"), get(v, "BIC"),
                get(v, "Verwendungszweck"),
                get(v, "Kundenreferenz"), get(v, "Mandatsreferenz"), get(v, "Gläubiger ID"),
                kind, null, kind);
    }

    private static BigDecimal deutscheBankAmount(Map<String, String> v) {
        String betrag = get(v, "Betrag");
        if (betrag != null) {
            return amount(betrag, true);
        }
        String haben = get(v, "Haben");
        if (haben != null) {
            return amount(haben, true).abs();
        }
        String soll = get(v, "Soll");
        return soll == null ? amount(null, true) : amount(soll, true).abs().negate();
    }

    // C24: Buchungsdatum (empty: pending), German amounts in EUR, Kontoname (else Kontonummer) as account
    private static BankTransaction c24(Map<String, String> v) {
        String dateText = get(v, "Buchungsdatum");
        LocalDate date = dateText == null ? null : date(dateText, GERMAN_DATE);
        BigDecimal amount = amount(get(v, "Betrag"), true);
        String account = get(v, "Kontoname");
        String category = get(v, "Kategorie");
        String subcategory = get(v, "Unterkategorie");
        String bankCategory = category == null ? null : subcategory == null ? category : category + " › " + subcategory;
        String purpose = get(v, "Verwendungszweck");
        return new BankTransaction(
                account != null ? account : orEmpty(get(v, "Kontonummer")), null,
                date, null, amount, "EUR",
                date == null ? Status.PENDING : Status.BOOKED,
                get(v, "Zahlungsempfänger"), get(v, "IBAN"), get(v, "BIC"),
                purpose != null ? purpose : get(v, "Beschreibung"),
                null, null, null,
                get(v, "Transaktionstyp"), null, bankCategory);
    }

    // Trade Republic: ISO dates, plain amounts in the given currency, the transaction_id as the bank's id
    private static BankTransaction tradeRepublic(Map<String, String> v) {
        LocalDate date = date(get(v, "date"), DateTimeFormatter.ISO_LOCAL_DATE);
        BigDecimal amount = amount(get(v, "amount"), false);
        String type = get(v, "type");
        String security = get(v, "name");
        String counterparty = security != null ? tradePrefix(type) + security : get(v, "counterparty_name");
        String purpose = get(v, "payment_reference");
        String account = get(v, "account_type");
        String currency = get(v, "currency");
        return new BankTransaction(
                account != null ? account : "DEFAULT", null,
                date, null, amount, currency != null ? currency.toUpperCase(Locale.ROOT) : "EUR", Status.BOOKED,
                counterparty, get(v, "counterparty_iban"), null,
                purpose != null ? purpose : get(v, "description"),
                null, null, null,
                type, get(v, "transaction_id"), type);
    }

    // R30: "Kauf ", "Verkauf " or "Dividende " before the name of a security
    private static String tradePrefix(String type) {
        String kind = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (kind.contains("BUY") || kind.contains("SAVINGS")) {
            return "Kauf ";
        }
        if (kind.contains("SELL")) {
            return "Verkauf ";
        }
        return kind.contains("DIVIDEND") ? "Dividende " : "";
    }

    // N26: ISO booking and value date, plain amounts in EUR
    private static BankTransaction n26(Map<String, String> v) {
        LocalDate date = date(get(v, "Booking Date"), DateTimeFormatter.ISO_LOCAL_DATE);
        String valueText = get(v, "Value Date");
        LocalDate valueDate = valueText == null ? null : date(valueText, DateTimeFormatter.ISO_LOCAL_DATE);
        BigDecimal amount = amount(get(v, "Amount (EUR)"), false);
        String type = get(v, "Type");
        return new BankTransaction(
                orEmpty(get(v, "Account Name")), null,
                date, valueDate, amount, "EUR", Status.BOOKED,
                get(v, "Partner Name"), get(v, "Partner Iban"), null,
                get(v, "Payment Reference"),
                null, null, null,
                type, null, type);
    }

    // Sparkasse (decision 47): one reader for all four exports, led by the columns the file has
    private static BankTransaction sparkasse(Map<String, String> v) {
        String dateText = get(v, "Buchungstag");
        LocalDate date = dateText == null ? null : date(dateText, SHORT_GERMAN_DATE);
        String valueText = get(v, "Valutadatum");
        LocalDate valueDate = valueText == null ? null : date(valueText, SHORT_GERMAN_DATE);
        BigDecimal amount = amount(get(v, "Betrag"), true);
        Status status = sparkasseStatus(get(v, "Info"), date);
        String purpose = get(v, "Verwendungszweck");
        Map<String, String> tags = sepaTags(purpose);
        String account = orEmpty(get(v, "Auftragskonto"));
        String kind = get(v, "Buchungstext");
        String category = get(v, "Kategorie");
        String currency = get(v, "Waehrung");
        return new BankTransaction(
                account, account.isEmpty() ? null : account,
                date, valueDate, amount, currency != null ? currency.toUpperCase(Locale.ROOT) : "EUR", status,
                get(v, "Beguenstigter/Zahlungspflichtiger"),
                firstOf(get(v, "Kontonummer/IBAN"), get(v, "Kontonummer")),
                firstOf(get(v, "BIC (SWIFT-Code)"), get(v, "BLZ")),
                tags.isEmpty() ? purpose : tags.get("SVWZ"),
                // "mit Kategorien" puts the end-to-end reference into Sammlerreferenz
                tags.isEmpty() ? firstOf(get(v, "Kundenreferenz (End-to-End)"), get(v, "Sammlerreferenz")) : tags.get("EREF"),
                tags.isEmpty() ? get(v, "Mandatsreferenz") : tags.get("MREF"),
                tags.isEmpty() ? get(v, "Glaeubiger ID") : tags.get("CRED"),
                kind, null, category != null ? category : kind);
    }

    // No export had a pending row (decision 47): "vorgemerkt" or no booking date is pending, another state is not read
    private static Status sparkasseStatus(String info, LocalDate date) {
        if (date == null || (info != null && info.toLowerCase(Locale.ROOT).contains("vorgemerkt"))) {
            return Status.PENDING;
        }
        if (info == null || info.equalsIgnoreCase("Umsatz gebucht")) {
            return Status.BOOKED;
        }
        throw new SkipException("Status unbekannt: " + info);
    }

    /**
     * VR-Bank (decision 64): dd.MM.yyyy, signed German amounts, the IBAN of the account as source. The purpose ends
     * before the first SEPA tag with ":" and loses card data and the mark of a standing order; a card payment has the
     * merchant before the first "/" of the purpose as counterparty, because the column often names the payment provider.
     */
    private static BankTransaction vrBank(Map<String, String> v) {
        String dateText = get(v, "Buchungstag");
        LocalDate date = dateText == null ? null : date(dateText, GERMAN_DATE);
        String valueText = get(v, "Valutadatum");
        LocalDate valueDate = valueText == null ? null : date(valueText, GERMAN_DATE);
        BigDecimal amount = amount(get(v, "Betrag"), true);
        String kind = get(v, "Buchungstext");
        Map<String, String> tags = colonTags(get(v, "Verwendungszweck"));
        String purpose = tags.get("");
        if (purpose != null) {
            purpose = STANDING_ORDER.matcher(CARD_DATA.matcher(purpose).replaceFirst("")).replaceAll("").trim();
        }
        String counterparty = get(v, "Name Zahlungsbeteiligter");
        if (purpose != null && kind != null && kind.toLowerCase(Locale.ROOT).startsWith("kartenzahlung")) {
            String merchant = purpose.split("/", 2)[0].trim();
            counterparty = merchant.isEmpty() ? counterparty : merchant;
        }
        String account = orEmpty(get(v, "IBAN Auftragskonto")).replaceAll("\\s", "");
        String currency = get(v, "Waehrung");
        return new BankTransaction(
                account, account.isEmpty() ? null : account,
                date, valueDate, amount, currency != null ? currency.toUpperCase(Locale.ROOT) : "EUR",
                date == null ? Status.PENDING : Status.BOOKED,
                counterparty, get(v, "IBAN Zahlungsbeteiligter"), get(v, "BIC (SWIFT-Code) Zahlungsbeteiligter"),
                purpose == null || purpose.isEmpty() ? null : purpose,
                tags.get("EREF"), get(v, "Mandatsreferenz"), get(v, "Glaeubiger ID"),
                kind, null, kind);
    }

    /**
     * VR-Bank: the SEPA tags with ": " in a purpose, each with the text up to the next tag, and under "" the text before
     * the first one; a tag only counts after whitespace or at the start. Blank values are left out.
     */
    static Map<String, String> colonTags(String purpose) {
        if (purpose == null) {
            return Map.of();
        }
        Map<String, String> tags = new HashMap<>();
        Matcher matcher = COLON_TAG.matcher(purpose);
        String tag = "";
        int start = 0;
        while (matcher.find()) {
            putTag(tags, tag, purpose.substring(start, matcher.start()));
            tag = matcher.group(1);
            start = matcher.end();
        }
        putTag(tags, tag, purpose.substring(start));
        return tags;
    }

    /**
     * The SEPA tags of a purpose that starts with one (CSV-MT940), each with the text up to the next tag; empty for any
     * other purpose. A tag only counts with its name in capitals and "+" ("Abo+" is none).
     */
    static Map<String, String> sepaTags(String purpose) {
        if (purpose == null) {
            return Map.of();
        }
        Matcher matcher = SEPA_TAG.matcher(purpose);
        if (!matcher.lookingAt()) {
            return Map.of();
        }
        Map<String, String> tags = new HashMap<>();
        String tag = matcher.group(1);
        int start = matcher.end();
        while (matcher.find()) {
            putTag(tags, tag, purpose.substring(start, matcher.start()));
            tag = matcher.group(1);
            start = matcher.end();
        }
        putTag(tags, tag, purpose.substring(start));
        return tags;
    }

    private static void putTag(Map<String, String> tags, String tag, String value) {
        if (!value.isBlank()) {
            tags.putIfAbsent(tag, value.trim());
        }
    }

    private static String firstOf(String first, String second) {
        return first != null ? first : second;
    }

    // The value of a column, null when the column is missing or empty
    private static String get(Map<String, String> values, String column) {
        String value = values.get(column.toLowerCase(Locale.ROOT));
        return value == null || value.isBlank() ? null : value;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static LocalDate date(String text, DateTimeFormatter format) {
        if (text == null) {
            throw new SkipException("Datum fehlt");
        }
        try {
            return LocalDate.parse(text, format);
        } catch (DateTimeParseException e) {
            throw new SkipException("Datum unlesbar: " + text);
        }
    }

    private static BigDecimal amount(String text, boolean german) {
        if (text == null) {
            throw new SkipException(NO_AMOUNT);
        }
        BigDecimal amount = german ? germanAmount(text) : plainAmount(text);
        if (amount == null) {
            throw new SkipException("Betrag unlesbar: " + text);
        }
        if (amount.signum() == 0) {
            throw new SkipException(NO_AMOUNT);
        }
        return amount;
    }

    /** "-1.234,56 €" → -1234.56; "€", spaces and thousands dots are allowed; null if it is no number. */
    static BigDecimal germanAmount(String text) {
        String compact = text.replace("€", "").replace("EUR", "").replaceAll("[\\s\\u00A0]", "").replace(".", "");
        if (!GERMAN_NUMBER.matcher(compact).matches()) {
            return null;
        }
        return new BigDecimal(compact.replace(",", ".")).setScale(2, RoundingMode.HALF_UP);
    }

    /** "-150.000000" → -150.00, any number of decimals rounded HALF_UP to 2; null if it is no number. */
    static BigDecimal plainAmount(String text) {
        String compact = text.replaceAll("[\\s\\u00A0]", "");
        if (!PLAIN_NUMBER.matcher(compact).matches()) {
            return null;
        }
        return new BigDecimal(compact).setScale(2, RoundingMode.HALF_UP);
    }
}
