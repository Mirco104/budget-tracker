package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.suggestion.CategorySuggester.KindHint;
import java.util.List;

/** The banks whose CSV export the app reads (R30, decision 38), with the columns a header must have. */
public enum BankProfile {

    C24("C24", List.of("Buchungsdatum", "Betrag", "Zahlungsempfänger", "Verwendungszweck", "Kontoname"), List.of(), true),
    TRADE_REPUBLIC("Trade Republic", List.of("date", "amount", "currency", "transaction_id"), List.of(
            new KindHint("BUY", "Investitionen"), new KindHint("SELL", "Investitionen"),
            new KindHint("SAVINGS", "Investitionen"), new KindHint("INTEREST", "Kapitalerträge"),
            new KindHint("DIVIDEND", "Kapitalerträge")), true),
    N26("N26", List.of("Booking Date", "Amount (EUR)", "Partner Name", "Account Name"), List.of(), true),
    // Decision 47: one profile for CSV-CAMT V2 and V8, CSV-MT940 and CSV - mit Kategorien; MT940 changes the purpose
    SPARKASSE("Sparkasse", List.of("Auftragskonto", "Buchungstag", "Beguenstigter/Zahlungspflichtiger", "Betrag", "Info"),
            List.of(), false),
    // Decision 64: the export of the Volks- und Raiffeisenbanken; the purpose is padded, so it is not in the checksum
    VR_BANK("VR-Bank", List.of("Bezeichnung Auftragskonto", "IBAN Auftragskonto", "Buchungstag", "Name Zahlungsbeteiligter",
            "Betrag"), List.of(), false);

    private final String label;
    private final List<String> requiredColumns;
    private final List<KindHint> kindHints;
    private final boolean purposeInFingerprint;

    BankProfile(String label, List<String> requiredColumns, List<KindHint> kindHints, boolean purposeInFingerprint) {
        this.label = label;
        this.requiredColumns = requiredColumns;
        this.kindHints = kindHints;
        this.purposeInFingerprint = purposeInFingerprint;
    }

    /** R29 (4): kinds of this bank that point to a category (decision 46); empty for banks without such kinds. */
    public List<KindHint> kindHints() {
        return kindHints;
    }

    /** The name of the bank, e.g. for a new account "C24 Ausgabenkonto". */
    public String label() {
        return label;
    }

    /** R30: whether the purpose is part of the checksum; not for the Sparkasse and the VR-Bank (decisions 47 and 64). */
    boolean purposeInFingerprint() {
        return purposeInFingerprint;
    }

    List<String> requiredColumns() {
        return requiredColumns;
    }
}
