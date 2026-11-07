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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the CSV export of a bank, rule R30 from docs/spezifikation.md, section 2. No Spring dependencies. The file is
 * decoded (UTF-8, else Windows-1252), split into records (";" or ","), the header found among the first 20 records
 * and every following record read with the profile of that header.
 */
public final class BankCsv {

    /** The header must be among the first records (R30). */
    static final int HEADER_SEARCH = 20;

    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern GERMAN_NUMBER = Pattern.compile("[+-]?\\d+(,\\d+)?");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");
    private static final String PENDING = "vorgemerkt (noch nicht gebucht)";
    private static final String NO_AMOUNT = "ohne Betrag";

    /**
     * One booking of the file. source is the account of the bank it belongs to (C24 Kontoname, N26 Account Name,
     * Trade Republic account_type); amount is signed (R11); the other fields are null when the file has no value.
     */
    public record BankRow(
            String source, LocalDate date, BigDecimal amount, String counterparty, String counterpartyIban,
            String purpose, String bankCategory, String kind, String externalId) {
    }

    /** A record after the header: its line in the file and either the booking or why it is not imported. */
    public record Line(int number, BankRow row, String skipReason) {
    }

    public record BankFile(BankProfile profile, List<Line> lines) {
    }

    /** The file has no header of a known profile among its first records. */
    public static class UnknownFormatException extends RuntimeException {
        UnknownFormatException() {
            super("Das Format der Datei ist unbekannt. Unterstützt werden CSV-Exporte von C24, Trade Republic und N26.");
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
                        return new BankFile(profile, readLines(profile, header, records.subList(i + 1, records.size())));
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

    private static List<Line> readLines(BankProfile profile, List<String> header, List<Record> records) {
        List<Line> lines = new ArrayList<>();
        for (Record record : records) {
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                values.put(header.get(i).toLowerCase(Locale.ROOT), i < record.fields().size() ? record.fields().get(i).trim() : "");
            }
            try {
                lines.add(new Line(record.line(), read(profile, values), null));
            } catch (SkipException skip) {
                lines.add(new Line(record.line(), null, skip.getMessage()));
            }
        }
        return lines;
    }

    private static final class SkipException extends RuntimeException {
        SkipException(String reason) {
            super(reason, null, false, false);
        }
    }

    private static BankRow read(BankProfile profile, Map<String, String> values) {
        return switch (profile) {
            case C24 -> c24(values);
            case TRADE_REPUBLIC -> tradeRepublic(values);
            case N26 -> n26(values);
        };
    }

    private static BankRow c24(Map<String, String> v) {
        String dateText = get(v, "Buchungsdatum");
        if (dateText == null) {
            throw new SkipException(PENDING);
        }
        LocalDate date = date(dateText, GERMAN_DATE);
        BigDecimal amount = amount(get(v, "Betrag"), true);
        String source = get(v, "Kontoname");
        String category = get(v, "Kategorie");
        String subcategory = get(v, "Unterkategorie");
        String bankCategory = category == null ? null : subcategory == null ? category : category + " › " + subcategory;
        String purpose = get(v, "Verwendungszweck");
        return new BankRow(
                source != null ? source : orEmpty(get(v, "Kontonummer")),
                date, amount,
                get(v, "Zahlungsempfänger"),
                get(v, "IBAN"),
                purpose != null ? purpose : get(v, "Beschreibung"),
                bankCategory,
                get(v, "Transaktionstyp"),
                null);
    }

    private static BankRow tradeRepublic(Map<String, String> v) {
        LocalDate date = date(get(v, "date"), DateTimeFormatter.ISO_LOCAL_DATE);
        String amountText = get(v, "amount");
        if (amountText == null) {
            throw new SkipException(NO_AMOUNT);
        }
        BigDecimal amount = plainAmount(amountText);
        if (amount == null) {
            throw new SkipException("Betrag unlesbar: " + amountText);
        }
        String currency = get(v, "currency");
        if (currency != null && !currency.equalsIgnoreCase("EUR")) {
            throw new SkipException("andere Währung als EUR (" + currency + ")");
        }
        if (amount.signum() == 0) {
            throw new SkipException(NO_AMOUNT);
        }
        String type = get(v, "type");
        String security = get(v, "name");
        String counterparty = security != null ? tradePrefix(type) + security : get(v, "counterparty_name");
        String purpose = get(v, "payment_reference");
        String source = get(v, "account_type");
        return new BankRow(
                source != null ? source : "DEFAULT",
                date, amount, counterparty,
                get(v, "counterparty_iban"),
                purpose != null ? purpose : get(v, "description"),
                type, type,
                get(v, "transaction_id"));
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

    private static BankRow n26(Map<String, String> v) {
        LocalDate date = date(get(v, "Booking Date"), DateTimeFormatter.ISO_LOCAL_DATE);
        BigDecimal amount = amount(get(v, "Amount (EUR)"), false);
        String type = get(v, "Type");
        return new BankRow(
                orEmpty(get(v, "Account Name")),
                date, amount,
                get(v, "Partner Name"),
                get(v, "Partner Iban"),
                get(v, "Payment Reference"),
                type, type, null);
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
            throw new SkipException(PENDING);
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
