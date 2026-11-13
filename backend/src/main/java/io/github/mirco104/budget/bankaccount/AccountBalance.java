package io.github.mirco104.budget.bankaccount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Rule P11 from docs/spezifikation.md, section 19: the app balance of an account is the last balance from the bank
 * plus all bookings of the account after its day (transfers included); without one the sum of all bookings. A new
 * balance from the bank shows the difference to the app balance on its day; the difference is not booked.
 * No Spring dependencies.
 */
public final class AccountBalance {

    /** A booking of the account; amount with sign (R11). */
    public record Entry(LocalDate date, BigDecimal amount) {
    }

    /** A balance from the bank at the end of its day. */
    public record Statement(LocalDate date, BigDecimal amount) {
    }

    private AccountBalance() {
    }

    /** App balance at the end of the day; statement is the last one on or before the day, null without one. */
    public static BigDecimal balance(Statement statement, List<Entry> entries, LocalDate day) {
        BigDecimal sum = statement == null ? BigDecimal.ZERO : statement.amount();
        for (Entry entry : entries) {
            boolean afterStatement = statement == null || entry.date().isAfter(statement.date());
            if (afterStatement && !entry.date().isAfter(day)) {
                sum = sum.add(entry.amount());
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** P11: bank balance minus the app balance on its day, from the statement before it. */
    public static BigDecimal difference(Statement previous, List<Entry> entries, Statement bank) {
        return bank.amount().subtract(balance(previous, entries, bank.date())).setScale(2, RoundingMode.HALF_UP);
    }
}
