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
}
