package io.github.mirco104.budget.importer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.importer.BankTransaction.Status;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Rule R30: description, cleaned purpose, fingerprint and the names of new accounts. */
class ImportRulesTest {

    @Test
    void cleansUuidsLongNumbersAndTrailingPunctuation() {
        assertThat(ImportRules.cleanPurpose(
                " Saveback cash reward f60809b4-17fa-40c5-bc2a-61c35543cba7 for reservation: fe2ed055-dff7-43ac-b61d-7e66758f9d16"))
                .isEqualTo("Saveback cash reward for reservation");
        assertThat(ImportRules.cleanPurpose("Lohn 10/2026 Personalnummer 12345678")).isEqualTo("Lohn 10/2026 Personalnummer");
        assertThat(ImportRules.cleanPurpose("Miete   November\n2026")).isEqualTo("Miete November 2026");
        assertThat(ImportRules.cleanPurpose("1234567890123")).isNull();
        assertThat(ImportRules.cleanPurpose(null)).isNull();
    }

    @Test
    void describesWithCounterpartyAndPurpose() {
        assertThat(ImportRules.describe("Person B", "Miete November", "Überweisung")).isEqualTo("Person B · Miete November");
        // Without purpose, or a purpose that contains the counterparty: only the counterparty
        assertThat(ImportRules.describe("SPIELE SHOP", null, "Kartenzahlung")).isEqualTo("SPIELE SHOP");
        assertThat(ImportRules.describe("REWE", "rewe sagt danke", null)).isEqualTo("REWE");
        // Without counterparty: the purpose
        assertThat(ImportRules.describe(null, "Auf Pocket Urlaub", "Umbuchung")).isEqualTo("Auf Pocket Urlaub");
        // Without both: the kind, else "Buchung"
        assertThat(ImportRules.describe(null, null, "Reward")).isEqualTo("Reward");
        assertThat(ImportRules.describe(" ", null, null)).isEqualTo("Buchung");
    }

    @Test
    void cutsTheDescriptionToOneHundredCharacters() {
        String description = ImportRules.describe("Händler", "x".repeat(200), null);

        assertThat(description).hasSize(100).endsWith("…").startsWith("Händler · xxx");
    }

    @Test
    void fingerprintOfTradeRepublicIsTheTransactionId() {
        BankTransaction row = row("DEFAULT", "-150.00", "Kauf Welt ETF", "id-1");
        BankTransaction sameIdOtherValues = row("DEFAULT", "-151.00", "Anders", "id-1");

        assertThat(ImportRules.fingerprint(BankProfile.TRADE_REPUBLIC, row, 1))
                .isEqualTo(ImportRules.fingerprint(BankProfile.TRADE_REPUBLIC, sameIdOtherValues, 2))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void fingerprintWithoutIdUsesTheValuesAndTheNumberOfEqualRows() {
        BankTransaction coffee = row("Hauptkonto", "-3.50", "Café", null);

        String first = ImportRules.fingerprint(BankProfile.N26, coffee, 1);
        assertThat(ImportRules.fingerprint(BankProfile.N26, coffee, 1)).isEqualTo(first);
        assertThat(ImportRules.fingerprint(BankProfile.N26, coffee, 2)).isNotEqualTo(first);
        assertThat(ImportRules.fingerprint(BankProfile.C24, coffee, 1)).isNotEqualTo(first);
        assertThat(ImportRules.fingerprint(BankProfile.N26, row("Space", "-3.50", "Café", null), 1)).isNotEqualTo(first);
        // The scale of the amount does not matter
        assertThat(ImportRules.fingerprint(BankProfile.N26, row("Hauptkonto", "-3.5", "Café", null), 1)).isEqualTo(first);
    }

    @Test
    void sameRowKeyCountsEqualRows() {
        BankTransaction coffee = row("Hauptkonto", "-3.50", "Café", null);

        assertThat(ImportRules.sameRowKey(coffee)).isEqualTo(ImportRules.sameRowKey(row("Hauptkonto", "-3.5", "Café", null)));
        assertThat(ImportRules.sameRowKey(coffee)).isNotEqualTo(ImportRules.sameRowKey(row("Hauptkonto", "-3.60", "Café", null)));
    }

    // Decision 46: pending rows and other currencies are read, and this rule leaves them out (R30)
    @Test
    void leavesOutPendingRowsAndOtherCurrencies() {
        LocalDate day = LocalDate.of(2026, 11, 3);
        assertThat(ImportRules.skipReason(transaction("Giro", null, "-4.20", "EUR", Status.PENDING, "Bäcker", null)))
                .isEqualTo("vorgemerkt (noch nicht gebucht)");
        assertThat(ImportRules.skipReason(transaction("Giro", day, "-12.35", "USD", Status.BOOKED, "Shop", null)))
                .isEqualTo("andere Währung als EUR (USD)");
        assertThat(ImportRules.skipReason(transaction("Giro", day, "-12.35", null, Status.BOOKED, "Shop", null))).isNull();
        assertThat(ImportRules.skipReason(transaction("Giro", day, "-12.35", "eur", Status.BOOKED, "Shop", null))).isNull();
    }

    // R30: the ID of the bank when the row has one, else the checksum, for every profile
    @Test
    void aTradeRepublicRowWithoutIdGetsTheChecksum() {
        BankTransaction interest = row("DEFAULT", "1.23", null, null);

        String first = ImportRules.fingerprint(BankProfile.TRADE_REPUBLIC, interest, 1);
        assertThat(first).hasSize(64).isEqualTo(ImportRules.fingerprint(BankProfile.TRADE_REPUBLIC, interest, 1));
        assertThat(ImportRules.fingerprint(BankProfile.TRADE_REPUBLIC, interest, 2)).isNotEqualTo(first);
        assertThat(ImportRules.fingerprint(BankProfile.TRADE_REPUBLIC, row("DEFAULT", "1.23", null, "id-9"), 1))
                .isNotEqualTo(first);
    }

    @Test
    void namesAndKeysOfSourceAccounts() {
        assertThat(ImportRules.sourceKey(BankProfile.C24, "Ausgabenkonto")).isEqualTo("C24:Ausgabenkonto");
        assertThat(ImportRules.accountName(BankProfile.C24, "Ausgabenkonto")).isEqualTo("C24 Ausgabenkonto");
        assertThat(ImportRules.accountName(BankProfile.N26, "Hauptkonto")).isEqualTo("N26 Hauptkonto");
        assertThat(ImportRules.accountName(BankProfile.TRADE_REPUBLIC, "DEFAULT")).isEqualTo("Trade Republic");
        assertThat(ImportRules.accountName(BankProfile.TRADE_REPUBLIC, "CRYPTO")).isEqualTo("Trade Republic CRYPTO");
        assertThat(ImportRules.accountName(BankProfile.N26, "")).isEqualTo("N26");
        assertThat(ImportRules.accountName(BankProfile.C24, "y".repeat(80))).hasSize(50);
        assertThat(ImportRules.sourceKey(BankProfile.C24, "y".repeat(200))).hasSize(120);
    }

    private static BankTransaction row(String source, String amount, String counterparty, String externalId) {
        return transaction(source, LocalDate.of(2026, 11, 3), amount, "EUR", Status.BOOKED, counterparty, externalId);
    }

    static BankTransaction transaction(String account, LocalDate bookingDate, String amount, String currency, Status status,
            String counterparty, String entryReference) {
        return new BankTransaction(account, null, bookingDate, null, new BigDecimal(amount), currency, status, counterparty,
                null, null, "Zweck", null, null, null, null, entryReference, null);
    }
}
