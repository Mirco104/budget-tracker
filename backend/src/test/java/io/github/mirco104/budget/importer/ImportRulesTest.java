package io.github.mirco104.budget.importer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.importer.BankTransaction.Status;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

        assertThat(ImportRules.sameRowKey(BankProfile.N26, coffee))
                .isEqualTo(ImportRules.sameRowKey(BankProfile.N26, row("Hauptkonto", "-3.5", "Café", null)));
        assertThat(ImportRules.sameRowKey(BankProfile.N26, coffee))
                .isNotEqualTo(ImportRules.sameRowKey(BankProfile.N26, row("Hauptkonto", "-3.60", "Café", null)));
    }

    // Rows imported before AP 26h must keep their fingerprint, else they would come again
    @Test
    void theFingerprintOfTheOtherBanksIsUnchanged() {
        assertThat(ImportRules.fingerprint(BankProfile.N26, row("Hauptkonto", "-3.50", "Café", null), 1))
                .isEqualTo("a3aad7c2159bb310d74c7d9f0d409bf9f669eaea9cf33b0e3dd9d42595fb3ebb");
    }

    // Decision 47: the purpose is not part of a Sparkasse fingerprint, because CSV-MT940 changes it
    @Test
    void aSparkasseBookingHasTheSameFingerprintInEveryExport() {
        BankCsv.BankFile camt = BankCsv.read(BankCsvTest.SPARKASSE_CAMT.getBytes(StandardCharsets.US_ASCII));
        BankCsv.BankFile mt940 = BankCsv.read(BankCsvTest.SPARKASSE_MT940.getBytes(StandardCharsets.US_ASCII));
        BankCsv.BankFile categories = BankCsv.read(
                BankCsvTest.SPARKASSE_CATEGORIES.getBytes(Charset.forName("windows-1252")));

        for (int line : new int[] {2, 3}) {
            BankTransaction row = row(camt, line);
            assertThat(ImportRules.sameRowKey(BankProfile.SPARKASSE, row(mt940, line)))
                    .isEqualTo(ImportRules.sameRowKey(BankProfile.SPARKASSE, row));
            String fingerprint = ImportRules.fingerprint(BankProfile.SPARKASSE, row, 1);
            assertThat(ImportRules.fingerprint(BankProfile.SPARKASSE, row(mt940, line), 1)).isEqualTo(fingerprint);
            assertThat(ImportRules.fingerprint(BankProfile.SPARKASSE, row(categories, line), 1)).isEqualTo(fingerprint);
        }
        assertThat(ImportRules.fingerprint(BankProfile.SPARKASSE, row(camt, 2), 2))
                .isNotEqualTo(ImportRules.fingerprint(BankProfile.SPARKASSE, row(camt, 2), 1));
    }

    @Test
    void namesASparkasseAccountByTheEndOfItsIban() {
        assertThat(ImportRules.sourceKey(BankProfile.SPARKASSE, "DE89370400440532013000"))
                .isEqualTo("SPARKASSE:DE89370400440532013000");
        assertThat(ImportRules.accountName(BankProfile.SPARKASSE, "DE89370400440532013000")).isEqualTo("Sparkasse …3000");
        assertThat(ImportRules.accountName(BankProfile.SPARKASSE, "")).isEqualTo("Sparkasse");
    }

    // Decision 64: the purpose is not part of a VR-Bank fingerprint either
    @Test
    void aVrBankFingerprintLeavesOutThePurpose() {
        BankTransaction row = row(BankCsv.read(BankCsvTest.VR_BANK.getBytes(StandardCharsets.UTF_8)), 4);
        BankTransaction otherPurpose = new BankTransaction(row.accountName(), row.accountIban(), row.bookingDate(),
                row.valueDate(), row.amount(), row.currency(), row.status(), row.counterpartyName(),
                row.counterpartyIban(), row.counterpartyBic(), "Abo  11/2026", row.endToEndId(), row.mandateId(),
                row.creditorId(), row.bankTransactionCode(), row.entryReference(), row.bankCategory());

        assertThat(ImportRules.sameRowKey(BankProfile.VR_BANK, otherPurpose))
                .isEqualTo(ImportRules.sameRowKey(BankProfile.VR_BANK, row));
        assertThat(ImportRules.fingerprint(BankProfile.VR_BANK, otherPurpose, 1))
                .isEqualTo(ImportRules.fingerprint(BankProfile.VR_BANK, row, 1))
                .isNotEqualTo(ImportRules.fingerprint(BankProfile.SPARKASSE, row, 1));
    }

    @Test
    void describesVrBankRowsWithoutCardData() {
        BankCsv.BankFile file = BankCsv.read(BankCsvTest.VR_BANK.getBytes(StandardCharsets.UTF_8));

        assertThat(describe(row(file, 2))).isEqualTo("Baeckerei Beispiel");
        assertThat(describe(row(file, 3))).isEqualTo("Beispielbank eG");
        assertThat(describe(row(file, 4))).isEqualTo("Beispiel Medien GmbH · Abo 11/2026 Kunde 4711");
        assertThat(describe(row(file, 5))).isEqualTo("Person B · Miete");
    }

    @Test
    void namesAVrBankAccountByTheEndOfItsIban() {
        assertThat(ImportRules.sourceKey(BankProfile.VR_BANK, "DE89370400440532013000"))
                .isEqualTo("VR_BANK:DE89370400440532013000");
        assertThat(ImportRules.accountName(BankProfile.VR_BANK, "DE89370400440532013000")).isEqualTo("VR-Bank …3000");
        assertThat(ImportRules.accountName(BankProfile.VR_BANK, "")).isEqualTo("VR-Bank");
    }

    // Decision 76: the Deutsche Bank like the Sparkasse and the VR-Bank
    @Test
    void namesADeutscheBankAccountByTheEndOfItsIban() {
        assertThat(ImportRules.sourceKey(BankProfile.DEUTSCHE_BANK, "DE89370400440532013000"))
                .isEqualTo("DEUTSCHE_BANK:DE89370400440532013000");
        assertThat(ImportRules.accountName(BankProfile.DEUTSCHE_BANK, "DE89370400440532013000"))
                .isEqualTo("Deutsche Bank …3000");
        assertThat(ImportRules.accountName(BankProfile.DEUTSCHE_BANK, "")).isEqualTo("Deutsche Bank");
    }

    // Decision 95: one fixed source, a new account is just "PayPal"; the fingerprint is the Transaktionscode
    @Test
    void namesThePayPalAccountAndFingerprintsByTheTransactionCode() {
        assertThat(ImportRules.sourceKey(BankProfile.PAYPAL, "PayPal")).isEqualTo("PAYPAL:PayPal");
        assertThat(ImportRules.accountName(BankProfile.PAYPAL, "PayPal")).isEqualTo("PayPal");

        BankTransaction row = row(BankCsv.read(BankCsvTest.PAYPAL.getBytes(StandardCharsets.UTF_8)), 2);
        BankTransaction otherName = new BankTransaction(row.accountName(), row.accountIban(), row.bookingDate(),
                row.valueDate(), row.amount(), row.currency(), row.status(), "Person B",
                row.counterpartyIban(), row.counterpartyBic(), "anders", row.endToEndId(), row.mandateId(),
                row.creditorId(), row.bankTransactionCode(), row.entryReference(), row.bankCategory());
        assertThat(ImportRules.fingerprint(BankProfile.PAYPAL, otherName, 2))
                .isEqualTo(ImportRules.fingerprint(BankProfile.PAYPAL, row, 1));
    }

    // R27 (c): the kinds of PayPal that move money between PayPal and a bank account or card
    @Test
    void knowsThePayPalKindsThatAreTransfers() {
        assertThat(ImportRules.isPayPalTransferKind("Bankgutschrift auf PayPal-Konto")).isTrue();
        assertThat(ImportRules.isPayPalTransferKind("Allgemeine Gutschrift auf Kreditkarte")).isTrue();
        assertThat(ImportRules.isPayPalTransferKind("allgemeine gutschrift auf debitkarte")).isTrue();
        assertThat(ImportRules.isPayPalTransferKind(" Von Nutzer eingeleitete Abbuchung ")).isTrue();
        assertThat(ImportRules.isPayPalTransferKind("Abbuchung auf Bankkonto")).isTrue();
        assertThat(ImportRules.isPayPalTransferKind("Allgemeine Zahlung")).isFalse();
        assertThat(ImportRules.isPayPalTransferKind(null)).isFalse();
    }

    // Decision 95: a side names PayPal by its counterparty, its description or the BIC of its counterparty
    @Test
    void knowsWhenABookingNamesPayPal() {
        assertThat(ImportRules.namesPayPal("PayPal Europe S.a.r.l. et Cie S.C.A", null, null)).isTrue();
        assertThat(ImportRules.namesPayPal(null, "Beispiel Shop GmbH · PayPal", null)).isTrue();
        assertThat(ImportRules.namesPayPal("Beispiel", "Beispiel", "pplxlul2")).isTrue();
        assertThat(ImportRules.namesPayPal("Beispiel Shop", "Kauf", "COBADEFFXXX")).isFalse();
    }

    @Test
    void aDeutscheBankFingerprintLeavesOutThePurpose() {
        BankTransaction row = row(BankCsv.read(BankCsvTest.DEUTSCHE_BANK.getBytes(StandardCharsets.UTF_8)), 9);
        BankTransaction otherPurpose = new BankTransaction(row.accountName(), row.accountIban(), row.bookingDate(),
                row.valueDate(), row.amount(), row.currency(), row.status(), row.counterpartyName(),
                row.counterpartyIban(), row.counterpartyBic(), "Abo 11/2026", row.endToEndId(), row.mandateId(),
                row.creditorId(), row.bankTransactionCode(), row.entryReference(), row.bankCategory());

        assertThat(ImportRules.fingerprint(BankProfile.DEUTSCHE_BANK, otherPurpose, 1))
                .isEqualTo(ImportRules.fingerprint(BankProfile.DEUTSCHE_BANK, row, 1))
                .isNotEqualTo(ImportRules.fingerprint(BankProfile.VR_BANK, row, 1));
    }

    @Test
    void describesDeutscheBankRows() {
        BankCsv.BankFile file = BankCsv.read(BankCsvTest.DEUTSCHE_BANK.getBytes(StandardCharsets.UTF_8));

        assertThat(describe(row(file, 9))).isEqualTo("Beispiel Medien GmbH · Abo 11/2026 Kunde 4711");
        assertThat(describe(row(file, 12))).isEqualTo("Depotpreis 07.2026-09.2026");
    }

    private static String describe(BankTransaction row) {
        return ImportRules.describe(row.counterpartyName(), ImportRules.cleanPurpose(row.remittanceInformation()),
                row.bankTransactionCode());
    }

    private static BankTransaction row(BankCsv.BankFile file, int number) {
        return file.lines().stream().filter(l -> l.number() == number).findFirst().orElseThrow().row();
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
