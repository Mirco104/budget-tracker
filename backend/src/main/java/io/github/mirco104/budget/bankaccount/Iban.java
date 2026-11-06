package io.github.mirco104.budget.bankaccount;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

/** R26: IBANs without spaces in upper case, checked for structure and ISO 13616 check digits (mod 97). */
public final class Iban {

    // Country code, two check digits, 11 to 30 letters or digits (15 to 34 characters in all)
    private static final Pattern STRUCTURE = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    /** Without whitespace in upper case; null for null or blank. */
    public static String normalize(String iban) {
        if (iban == null) {
            return null;
        }
        String normalized = iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    /** True for a normalized IBAN with the right structure and check digits. */
    public static boolean isValid(String iban) {
        if (iban == null || !STRUCTURE.matcher(iban).matches()) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
