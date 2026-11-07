package io.github.mirco104.budget.importer;

import java.util.List;

/** The banks whose CSV export the app reads (R30, decision 38), with the columns a header must have. */
public enum BankProfile {

    C24("C24", List.of("Buchungsdatum", "Betrag", "Zahlungsempfänger", "Verwendungszweck", "Kontoname")),
    TRADE_REPUBLIC("Trade Republic", List.of("date", "amount", "currency", "transaction_id")),
    N26("N26", List.of("Booking Date", "Amount (EUR)", "Partner Name", "Account Name"));

    private final String label;
    private final List<String> requiredColumns;

    BankProfile(String label, List<String> requiredColumns) {
        this.label = label;
        this.requiredColumns = requiredColumns;
    }

    /** The name of the bank, e.g. for a new account "C24 Ausgabenkonto". */
    public String label() {
        return label;
    }

    List<String> requiredColumns() {
        return requiredColumns;
    }
}
