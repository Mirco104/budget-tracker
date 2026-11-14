package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.importer.BankTransaction.Status;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rule R30: description, cleaned purpose, fingerprint and names of the source accounts. No Spring dependencies. */
public final class ImportRules {

    /** Longest description of a transaction (US3). */
    static final int MAX_DESCRIPTION = 100;
    static final int MAX_ACCOUNT_NAME = 50;
    static final int MAX_SOURCE_KEY = 120;

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{8,}");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s:;,.\\-–]+$");

    private ImportRules() {
    }

    /** UUIDs and digit runs of 8 or more are dropped, whitespace becomes one space, trailing punctuation goes; null if empty. */
    public static String cleanPurpose(String purpose) {
        if (purpose == null) {
            return null;
        }
        String cleaned = UUID.matcher(purpose).replaceAll(" ");
        cleaned = LONG_NUMBER.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = TRAILING_PUNCTUATION.matcher(cleaned).replaceAll("");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * The counterparty when the cleaned purpose is empty or contains it, the purpose without counterparty, else
     * "counterparty · purpose"; without both the kind, else "Buchung"; at most 100 characters.
     */
    public static String describe(String counterparty, String cleanedPurpose, String kind) {
        String name = blankToNull(counterparty);
        String purpose = blankToNull(cleanedPurpose);
        String description;
        if (name != null && (purpose == null || lower(purpose).contains(lower(name)))) {
            description = name;
        } else if (name == null && purpose != null) {
            description = purpose;
        } else if (name != null) {
            description = name + " · " + purpose;
        } else {
            description = blankToNull(kind) != null ? kind.trim() : "Buchung";
        }
        return cut(description, MAX_DESCRIPTION);
    }

    /**
     * R30: why a booking that could be read is not imported: pending, or another currency than EUR; null if it is
     * imported (decision 46: reading only normalizes, this rule decides).
     */
    public static String skipReason(BankTransaction row) {
        if (row.status() == Status.PENDING) {
            return "vorgemerkt (noch nicht gebucht)";
        }
        if (row.currency() != null && !row.currency().equalsIgnoreCase("EUR")) {
            return "andere Währung als EUR (" + row.currency() + ")";
        }
        return null;
    }

    /**
     * The values that make two rows of a file equal; equal rows are told apart by their number (R30). Without the
     * purpose for the Sparkasse and the VR-Bank (decisions 47 and 64); for every other bank exactly as before, so
     * fingerprints stay the same.
     */
    public static String sameRowKey(BankProfile profile, BankTransaction row) {
        String key = String.join("|", row.accountName(), row.bookingDate().toString(),
                row.amount().stripTrailingZeros().toPlainString(), nullToEmpty(row.counterpartyName()));
        return profile.purposeInFingerprint() ? key + "|" + nullToEmpty(row.remittanceInformation()) : key;
    }

    /**
     * R30: SHA-256 over the profile and the bank's id of the booking when the row has one, else, for every profile,
     * over the profile, the values of the row and its number among equal rows (1, 2, …).
     */
    public static String fingerprint(BankProfile profile, BankTransaction row, int number) {
        String text = row.entryReference() != null
                ? profile.name() + "|id|" + row.entryReference()
                : profile.name() + "|" + sameRowKey(profile, row) + "|" + number;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    /** "C24:Ausgabenkonto": the key an account remembers for a source of a bank file (R30). */
    public static String sourceKey(BankProfile profile, String source) {
        return cut(profile.name() + ":" + source, MAX_SOURCE_KEY, "");
    }

    /**
     * The name of a new account for a source: "C24 Ausgabenkonto", "Trade Republic" for its default account,
     * "Sparkasse …3000" or "VR-Bank …3000" with the end of the IBAN.
     */
    public static String accountName(BankProfile profile, String source) {
        boolean plain = source == null || source.isBlank()
                || (profile == BankProfile.TRADE_REPUBLIC && source.equalsIgnoreCase("DEFAULT"));
        if (!plain && (profile == BankProfile.SPARKASSE || profile == BankProfile.VR_BANK)) {
            String iban = source.replaceAll("\\s", "");
            return profile.label() + " …" + iban.substring(Math.max(0, iban.length() - 4));
        }
        return cut(plain ? profile.label() : profile.label() + " " + source.trim(), MAX_ACCOUNT_NAME, "");
    }

    private static String cut(String text, int max) {
        return cut(text, max, "…");
    }

    private static String cut(String text, int max, String ellipsis) {
        return text.length() <= max ? text : text.substring(0, max - ellipsis.length()) + ellipsis;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.GERMAN);
    }
}
