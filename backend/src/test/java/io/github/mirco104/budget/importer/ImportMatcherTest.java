package io.github.mirco104.budget.importer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.importer.ImportMatcher.Existing;
import io.github.mirco104.budget.importer.ImportMatcher.OwnAccount;
import io.github.mirco104.budget.importer.ImportMatcher.Row;
import io.github.mirco104.budget.importer.ImportMatcher.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rules R27 and R30: transfers between own accounts and possible duplicates of bookings entered by hand. */
class ImportMatcherTest {

    private static final long GIRO = 1;
    private static final long DEPOT = 2;
    // ISO 13616 documentation examples, not real accounts of anyone
    private static final String GIRO_IBAN = "DE89370400440532013000";
    private static final String DEPOT_IBAN = "GB82WEST12345698765432";

    private static final List<OwnAccount> ACCOUNTS = List.of(new OwnAccount(GIRO, GIRO_IBAN), new OwnAccount(DEPOT, DEPOT_IBAN));

    // --- R27 (a): the IBAN of an own account ---

    @Test
    void certainWhenTheCounterpartyIbanIsAnOwnAccount() {
        List<Row> rows = List.of(
                new Row(0, "TR:DEFAULT", DEPOT, date(3), "500", "de89 3704 0044 0532 0130 00"),
                new Row(1, "TR:DEFAULT", DEPOT, date(4), "-20", "LU280019400644750000"));

        Map<Integer, Transfer> transfers = ImportMatcher.transfers(rows, List.of(), ACCOUNTS);

        assertThat(transfers).containsOnlyKeys(0);
        assertThat(transfers.get(0)).isEqualTo(new Transfer(true, null, null));
    }

    @Test
    void theIbanOfTheOwnAccountOfTheRowIsNoTransfer() {
        List<Row> rows = List.of(new Row(0, "C24:Giro", GIRO, date(3), "-5", GIRO_IBAN));

        assertThat(ImportMatcher.transfers(rows, List.of(), ACCOUNTS)).isEmpty();
    }

    // --- R27 (b): a counterpart with the opposite amount ---

    @Test
    void pairsTwoRowsOfDifferentSourcesInTheFile() {
        List<Row> rows = List.of(
                new Row(0, "C24:Ausgabenkonto", null, date(2), "-100", null),
                new Row(1, "C24:Pocket Urlaub", null, date(2), "100", null),
                new Row(2, "C24:Ausgabenkonto", null, date(2), "-100", null));

        Map<Integer, Transfer> transfers = ImportMatcher.transfers(rows, List.of(), ACCOUNTS);

        assertThat(transfers).containsOnlyKeys(0, 1);
        assertThat(transfers.get(0)).isEqualTo(new Transfer(false, 1, null));
        assertThat(transfers.get(1)).isEqualTo(new Transfer(false, 0, null));
    }

    @Test
    void rowsOfTheSameSourceAreNoPair() {
        List<Row> rows = List.of(
                new Row(0, "N26:Hauptkonto", null, date(2), "-30", null),
                new Row(1, "N26:Hauptkonto", null, date(2), "30", null));

        assertThat(ImportMatcher.transfers(rows, List.of(), ACCOUNTS)).isEmpty();
    }

    @Test
    void pairsWithAStoredBookingOnAnotherAccount() {
        List<Row> rows = List.of(new Row(0, "TR:DEFAULT", DEPOT, date(5), "500", null));
        List<Existing> existing = List.of(
                new Existing(10, GIRO, date(1), "-500", true),
                new Existing(11, DEPOT, date(5), "-500", true),
                new Existing(12, GIRO, date(4), "-500", false));

        Map<Integer, Transfer> transfers = ImportMatcher.transfers(rows, existing, ACCOUNTS);

        // 10 is 4 days away, 11 is on the same account; 12 is the closest
        assertThat(transfers.get(0)).isEqualTo(new Transfer(false, null, 12L));
    }

    @Test
    void theClosestCounterpartWinsAndEachCountsOnce() {
        List<Row> rows = List.of(
                new Row(0, "TR:DEFAULT", DEPOT, date(5), "500", null),
                new Row(1, "TR:DEFAULT", DEPOT, date(6), "500", null));
        List<Existing> existing = List.of(new Existing(12, GIRO, date(6), "-500", false));

        Map<Integer, Transfer> transfers = ImportMatcher.transfers(rows, existing, ACCOUNTS);

        // Row 0 comes first by date and takes the only counterpart; row 1 finds none
        assertThat(transfers).containsOnlyKeys(0);
        assertThat(transfers.get(0).peerTransactionId()).isEqualTo(12L);
    }

    @Test
    void aCertainTransferAlsoFindsItsCounterpart() {
        List<Row> rows = List.of(new Row(0, "TR:DEFAULT", DEPOT, date(3), "500", GIRO_IBAN));
        List<Existing> existing = List.of(new Existing(12, GIRO, date(3), "-500", true));

        assertThat(ImportMatcher.transfers(rows, existing, ACCOUNTS).get(0)).isEqualTo(new Transfer(true, null, 12L));
    }

    @Test
    void aRowOfANewAccountPairsWithAnyStoredAccount() {
        List<Row> rows = List.of(new Row(0, "TR:DEFAULT", null, date(3), "500", null));
        List<Existing> existing = List.of(new Existing(12, GIRO, date(3), "-500", false));

        assertThat(ImportMatcher.transfers(rows, existing, ACCOUNTS).get(0).peerTransactionId()).isEqualTo(12L);
    }

    // --- R27 (b): a stored transfer without its other side (a transfer booked by hand) ---

    @Test
    void aStoredTransferWithoutOtherSideIsACounterpart() {
        List<Row> rows = List.of(new Row(0, "C24:Giro", GIRO, date(14), "22", null));
        List<Existing> transfers = List.of(new Existing(30, DEPOT, date(14), "-22", false));

        assertThat(ImportMatcher.transfers(rows, List.of(), transfers, ACCOUNTS).get(0))
                .isEqualTo(new Transfer(false, null, 30L));
    }

    @Test
    void aStoredTransferWithItsOtherSideIsNoCounterpart() {
        List<Row> rows = List.of(new Row(0, "C24:Giro", GIRO, date(14), "22", null));
        List<Existing> transfers = List.of(
                new Existing(30, DEPOT, date(14), "-22", false),
                new Existing(31, 3, date(15), "22", false));

        assertThat(ImportMatcher.transfers(rows, List.of(), transfers, ACCOUNTS)).isEmpty();
    }

    @Test
    void anOpenStoredTransferCountsOnce() {
        List<Row> rows = List.of(
                new Row(0, "C24:Giro", GIRO, date(14), "22", null),
                new Row(1, "C24:Giro", GIRO, date(14), "22", null));
        List<Existing> transfers = List.of(new Existing(30, DEPOT, date(14), "-22", false));

        Map<Integer, Transfer> result = ImportMatcher.transfers(rows, List.of(), transfers, ACCOUNTS);

        assertThat(result).containsOnlyKeys(0);
        assertThat(result.get(0).peerTransactionId()).isEqualTo(30L);
    }

    @Test
    void aStoredTransferOnTheAccountOfTheRowIsNoCounterpart() {
        List<Row> rows = List.of(new Row(0, "C24:Giro", GIRO, date(14), "22", null));
        List<Existing> transfers = List.of(new Existing(30, GIRO, date(14), "-22", false));

        assertThat(ImportMatcher.transfers(rows, List.of(), transfers, ACCOUNTS)).isEmpty();
    }

    @Test
    void twoStoredTransfersOnTheSameAccountAreNoPair() {
        List<Row> rows = List.of(new Row(0, "C24:Giro", GIRO, date(14), "22", null));
        List<Existing> transfers = List.of(
                new Existing(30, DEPOT, date(14), "-22", false),
                new Existing(31, DEPOT, date(14), "22", false));

        // 31 is no other side of 30, so 30 stays open
        assertThat(ImportMatcher.transfers(rows, List.of(), transfers, ACCOUNTS).get(0).peerTransactionId())
                .isEqualTo(30L);
    }

    // --- R30: possible duplicates ---

    @Test
    void aBookingByHandWithTheSameAmountAndTwoDaysIsAPossibleDuplicate() {
        List<Row> rows = List.of(
                new Row(0, "N26:Hauptkonto", GIRO, date(5), "-3.50", null),
                new Row(1, "N26:Hauptkonto", GIRO, date(5), "-3.50", null),
                new Row(2, "N26:Hauptkonto", GIRO, date(5), "-9.99", null));
        List<Existing> existing = List.of(
                new Existing(20, GIRO, date(7), "-3.5", false),
                new Existing(21, GIRO, date(5), "-9.99", true),
                new Existing(22, GIRO, date(9), "-9.99", false));

        Map<Integer, Long> duplicates = ImportMatcher.possibleDuplicates(rows, existing);

        // One booking by hand stands for one row; imported bookings and 4 days away do not count
        assertThat(duplicates).containsExactlyEntriesOf(Map.of(0, 20L));
    }

    private static LocalDate date(int day) {
        return LocalDate.of(2026, 11, day);
    }

    // --- R27 (c) and the PayPal pair (decision 95) ---

    private static final long PAYPAL = 3;
    private static final String TOP_UP = "Aufladung für Beispiel Shop GmbH";
    private static final String DEBIT = "PayPal Europe S.a.r.l. et Cie S.C.A · /PP.1234.PP/. Beispiel Shop GmbH, Ihr Einkauf "
            + "bei Beispiel Shop GmbH";

    private static Row payPalRow(int index, int day, String amount, String description, boolean topUp) {
        return new Row(index, "PAYPAL:PayPal", PAYPAL, date(day), new BigDecimal(amount), null, ImportMatcher.PayPal.ACCOUNT,
                description, topUp);
    }

    private static Row bankRow(int index, int day, String amount, String description) {
        return new Row(index, "C24:Ausgabenkonto", GIRO, date(day), new BigDecimal(amount), null, ImportMatcher.PayPal.NAMED,
                description, false);
    }

    @Test
    void aTopUpOrWithdrawalOfPayPalIsCertainWithoutCounterpart() {
        Map<Integer, Transfer> transfers = ImportMatcher.transfers(
                List.of(payPalRow(0, 5, "49.90", TOP_UP, true), payPalRow(1, 5, "-49.90", "Beispiel Shop GmbH", false)),
                List.of(), ACCOUNTS);

        assertThat(transfers).containsOnlyKeys(0);
        assertThat(transfers.get(0)).isEqualTo(new Transfer(true, null, null));
    }

    @Test
    void aPayPalPairMayBeTenDaysApartAndIsCertainWithTheMerchant() {
        Existing debit = new Existing(7, GIRO, date(15), new BigDecimal("-49.90"), true, ImportMatcher.PayPal.NAMED, DEBIT);

        Map<Integer, Transfer> transfers = ImportMatcher.transfers(List.of(payPalRow(0, 5, "49.90", TOP_UP, true)),
                List.of(debit), ACCOUNTS);

        assertThat(transfers.get(0)).isEqualTo(new Transfer(true, null, 7L));
    }

    @Test
    void aPayPalPairElevenDaysApartIsNoPair() {
        Existing debit = new Existing(7, GIRO, date(16), new BigDecimal("-49.90"), true, ImportMatcher.PayPal.NAMED, DEBIT);

        Map<Integer, Transfer> transfers = ImportMatcher.transfers(List.of(payPalRow(0, 5, "49.90", TOP_UP, true)),
                List.of(debit), ACCOUNTS);

        assertThat(transfers.get(0)).isEqualTo(new Transfer(true, null, null));
    }

    // The bank row of a later file finds the stored top-up, a transfer without its other side
    @Test
    void theBankRowFindsTheStoredTopUpAndIsCertainWithTheMerchant() {
        Existing topUp = new Existing(8, PAYPAL, date(5), new BigDecimal("49.90"), true, ImportMatcher.PayPal.ACCOUNT, TOP_UP);

        Map<Integer, Transfer> transfers = ImportMatcher.transfers(List.of(bankRow(0, 12, "-49.90", DEBIT)), List.of(),
                List.of(topUp), ACCOUNTS);

        assertThat(transfers.get(0)).isEqualTo(new Transfer(true, null, 8L));
    }

    @Test
    void aPayPalPairWithoutCommonWordIsUncertain() {
        List<Row> rows = List.of(
                payPalRow(0, 3, "-96.75", "Von Nutzer eingeleitete Abbuchung", true),
                bankRow(1, 9, "96.75", "PayPal Europe S.a.r.l. et Cie S.C.A · PayPal Europe"));

        Map<Integer, Transfer> transfers = ImportMatcher.transfers(rows, List.of(), ACCOUNTS);

        assertThat(transfers.get(0)).isEqualTo(new Transfer(true, 1, null));
        assertThat(transfers.get(1)).isEqualTo(new Transfer(false, 0, null));
    }

    // Only a side on a PayPal account and a side that names PayPal have 10 days; any other pair keeps 3
    @Test
    void otherPairsKeepThreeDays() {
        List<Row> namedOnly = List.of(bankRow(0, 2, "-49.90", DEBIT),
                new Row(1, "C24:Pocket", 9L, date(8), new BigDecimal("49.90"), null, ImportMatcher.PayPal.NAMED, DEBIT, false));
        List<Row> plain = List.of(new Row(0, "C24:Ausgabenkonto", GIRO, date(2), "-49.90", null),
                new Row(1, "PAYPAL:PayPal", PAYPAL, date(6), "49.90", null));

        assertThat(ImportMatcher.transfers(namedOnly, List.of(), ACCOUNTS)).isEmpty();
        assertThat(ImportMatcher.transfers(plain, List.of(), ACCOUNTS)).isEmpty();
    }

    @Test
    void twoStoredSidesOfAPayPalPairAreNoOpenTransfer() {
        Existing topUp = new Existing(8, PAYPAL, date(5), new BigDecimal("49.90"), true, ImportMatcher.PayPal.ACCOUNT, TOP_UP);
        Existing debit = new Existing(9, GIRO, date(12), new BigDecimal("-49.90"), true, ImportMatcher.PayPal.NAMED, DEBIT);

        Map<Integer, Transfer> transfers = ImportMatcher.transfers(List.of(bankRow(0, 12, "-49.90", DEBIT)), List.of(),
                List.of(topUp, debit), ACCOUNTS);

        assertThat(transfers).isEmpty();
    }

    @SuppressWarnings("unused")
    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
