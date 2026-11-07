package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.suggestion.CategorySuggester.KindHint;
import java.util.List;

/** The banks whose CSV export the app reads (R30, decision 38), with the columns a header must have. */
public enum BankProfile {

    C24("C24", List.of("Buchungsdatum", "Betrag", "Zahlungsempfänger", "Verwendungszweck", "Kontoname"), List.of()),
    TRADE_REPUBLIC("Trade Republic", List.of("date", "amount", "currency", "transaction_id"), List.of(
            new KindHint("BUY", "Investitionen"), new KindHint("SELL", "Investitionen"),
            new KindHint("SAVINGS", "Investitionen"), new KindHint("INTEREST", "Kapitalerträge"),
            new KindHint("DIVIDEND", "Kapitalerträge"))),
    N26("N26", List.of("Booking Date", "Amount (EUR)", "Partner Name", "Account Name"), List.of());

    private final String label;
    private final List<String> requiredColumns;
    private final List<KindHint> kindHints;

    BankProfile(String label, List<String> requiredColumns, List<KindHint> kindHints) {
        this.label = label;
        this.requiredColumns = requiredColumns;
        this.kindHints = kindHints;
    }

    /** R29 (4): kinds of this bank that point to a category (decision 46); empty for banks without such kinds. */
    public List<KindHint> kindHints() {
        return kindHints;
    }

    /** The name of the bank, e.g. for a new account "C24 Ausgabenkonto". */
    public String label() {
        return label;
    }

    List<String> requiredColumns() {
        return requiredColumns;
    }
}
