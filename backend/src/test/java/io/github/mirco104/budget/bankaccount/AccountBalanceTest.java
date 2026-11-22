package io.github.mirco104.budget.bankaccount;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.bankaccount.AccountBalance.Entry;
import io.github.mirco104.budget.bankaccount.AccountBalance.Statement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rule P11: app balance of an account and the difference to a balance from the bank. */
class AccountBalanceTest {

    private static Entry entry(String date, String amount) {
        return new Entry(LocalDate.parse(date), new BigDecimal(amount));
    }

    private static final List<Entry> ENTRIES = List.of(
            entry("2026-10-28", "-50"), entry("2026-11-01", "2500"), entry("2026-11-03", "-900"),
            entry("2026-11-10", "-40.10"));

    @Test
    void withoutAStatementTheBalanceIsTheSumOfAllBookings() {
        assertThat(AccountBalance.balance(null, ENTRIES, LocalDate.parse("2026-11-13"))).isEqualByComparingTo("1509.90");
    }

    @Test
    void theLastStatementPlusTheBookingsAfterItsDay() {
        Statement statement = new Statement(LocalDate.parse("2026-11-01"), new BigDecimal("3000"));

        // The bookings of the statement day are in the statement already
        assertThat(AccountBalance.balance(statement, ENTRIES, LocalDate.parse("2026-11-13"))).isEqualByComparingTo("2059.90");
        assertThat(AccountBalance.balance(statement, ENTRIES, LocalDate.parse("2026-11-05"))).isEqualByComparingTo("2100.00");
    }

    @Test
    void theDifferenceOfANewStatementIsNotBooked() {
        Statement previous = new Statement(LocalDate.parse("2026-11-01"), new BigDecimal("3000"));
        Statement bank = new Statement(LocalDate.parse("2026-11-10"), new BigDecimal("2000"));

        // App on 10 November: 3000 - 900 - 40.10 = 2059.90; the bank says 2000
        assertThat(AccountBalance.difference(previous, ENTRIES, bank)).isEqualByComparingTo("-59.90");
        assertThat(AccountBalance.difference(null, ENTRIES, new Statement(LocalDate.parse("2026-11-01"),
                new BigDecimal("2450")))).isEqualByComparingTo("0.00");
    }

    @Test
    void theHistoryShowsEveryStatementNewestFirstWithTheDifferenceFromTheOneBefore() {
        // Ticket #135: entered out of order on purpose
        List<Statement> statements = List.of(
                new Statement(LocalDate.parse("2026-11-10"), new BigDecimal("2000")),
                new Statement(LocalDate.parse("2026-11-01"), new BigDecimal("2450")));

        List<AccountBalance.Reconciled> history = AccountBalance.history(statements, ENTRIES);

        assertThat(history).extracting(r -> r.statement().date())
                .containsExactly(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-01"));
        // 10 November: app from the statement of 1 November is 2450 - 900 - 40.10 = 1509.90
        assertThat(history.get(0).appBalance()).isEqualByComparingTo("1509.90");
        assertThat(history.get(0).difference()).isEqualByComparingTo("490.10");
        // 1 November: no statement before, app is the sum of the bookings up to that day
        assertThat(history.get(1).appBalance()).isEqualByComparingTo("2450.00");
        assertThat(history.get(1).difference()).isEqualByComparingTo("0.00");
    }

    @Test
    void anEmptyHistoryStaysEmpty() {
        assertThat(AccountBalance.history(List.of(), ENTRIES)).isEmpty();
    }

    // --- R36: variable spending from the balance on the last day of a month (stage 2) ---

    @Test
    void variableSpendingIsTheAppBalanceMinusTheBankBalanceOverAllEverydayAccounts() {
        Statement previous = new Statement(LocalDate.of(2026, 9, 30), new BigDecimal("1000.00"));
        List<Entry> entries = List.of(
                new Entry(LocalDate.of(2026, 10, 1), new BigDecimal("2800.00")),
                new Entry(LocalDate.of(2026, 10, 3), new BigDecimal("-900.00")),
                new Entry(LocalDate.of(2026, 11, 2), new BigDecimal("-50.00")));
        Statement bank = new Statement(LocalDate.of(2026, 10, 31), new BigDecimal("2400.00"));
        BigDecimal giro = AccountBalance.difference(previous, entries, bank);
        BigDecimal second = AccountBalance.difference(null, List.of(), new Statement(LocalDate.of(2026, 10, 31),
                new BigDecimal("-100.00")));

        AccountBalance.Variable variable = AccountBalance.variable(List.of(giro, second));

        assertThat(variable.amount()).isEqualByComparingTo("600.00");
        assertThat(variable.moreThanExpected()).isFalse();
    }

    @Test
    void moreOnTheAccountThanExpectedGivesNoVariableSpending() {
        AccountBalance.Variable variable = AccountBalance.variable(List.of(new BigDecimal("25.50")));

        assertThat(variable.amount()).isEqualByComparingTo("25.50");
        assertThat(variable.moreThanExpected()).isTrue();
    }

    @Test
    void withoutABalanceOnTheLastDayThereIsNoVariableSpending() {
        assertThat(AccountBalance.variable(List.of())).isNull();
    }
}
