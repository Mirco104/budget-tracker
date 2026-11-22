package io.github.mirco104.budget.bankaccount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rule P11 from docs/spezifikation.md, section 19: the app balance of an account is the last balance from the bank
 * plus all bookings of the account after its day (transfers included); without one the sum of all bookings. A new
 * balance from the bank shows the difference to the app balance on its day; the difference is not booked. P12: the
 * lower limit and the expected balance.
 * No Spring dependencies.
 */
public final class AccountBalance {

    /** A booking of the account; amount with sign (R11). */
    public record Entry(LocalDate date, BigDecimal amount) {
    }

    /** A balance from the bank at the end of its day. */
    public record Statement(LocalDate date, BigDecimal amount) {
    }

    /** Ticket #135: a balance from the bank with the app balance on its day and the difference (P11). */
    public record Reconciled(Statement statement, BigDecimal appBalance, BigDecimal difference) {
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

    /**
     * R36 (stage 2): what was spent variably in a month, from the differences (P11) of the balances on its last day of
     * all everyday accounts: the negated sum. A positive sum means more was on the accounts than expected; then amount
     * is that surplus and moreThanExpected is set (variable spending 0).
     */
    public record Variable(BigDecimal amount, boolean moreThanExpected) {
    }

    /** R36: null without a balance on the last day of the month. */
    public static Variable variable(List<BigDecimal> differences) {
        if (differences.isEmpty()) {
            return null;
        }
        BigDecimal sum = differences.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return sum.signum() > 0 ? new Variable(sum, true) : new Variable(sum.negate(), false);
    }

    /** P12: the lower limit of the mode; null when there is none (OFF, or CUSTOM without an amount). */
    public static BigDecimal limit(LowerLimitMode mode, BigDecimal custom) {
        return switch (mode) {
            case OFF -> null;
            case ZERO -> BigDecimal.ZERO;
            case CUSTOM -> custom;
        };
    }

    /** P12: the expected balance: the balance today plus the open terms of the month on the account (with sign). */
    public static BigDecimal expected(BigDecimal balance, List<BigDecimal> openTerms) {
        return openTerms.stream().reduce(balance, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** P12: a balance lies below a limit; never without a limit. */
    public static boolean below(BigDecimal balance, BigDecimal limit) {
        return limit != null && balance.compareTo(limit) < 0;
    }

    /** P11: bank balance minus the app balance on its day, from the statement before it. */
    public static BigDecimal difference(Statement previous, List<Entry> entries, Statement bank) {
        return bank.amount().subtract(balance(previous, entries, bank.date())).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Ticket #135: every balance from the bank, newest first, each with the app balance on its day from the statement
     * before it and the difference, as when it was entered (P11).
     */
    public static List<Reconciled> history(List<Statement> statements, List<Entry> entries) {
        List<Statement> sorted = statements.stream().sorted(Comparator.comparing(Statement::date)).toList();
        List<Reconciled> history = new ArrayList<>();
        Statement previous = null;
        for (Statement statement : sorted) {
            BigDecimal app = balance(previous, entries, statement.date());
            history.add(new Reconciled(statement, app, difference(previous, entries, statement)));
            previous = statement;
        }
        return history.reversed();
    }

    /**
     * R36: the differences of the balances of one account dated in the month, each against the balance before it;
     * from is the day of the balance before the first one, to the day of the last one (both null when the month has no
     * balance with one before it).
     */
    public record Period(List<BigDecimal> differences, LocalDate from, LocalDate to) {
    }

    /**
     * R36 (23.11.2026): a balance on any day of the month counts, not only on the last day. The very first balance of
     * an account is its start (P11) and measures no spending.
     */
    public static Period inMonth(java.time.YearMonth month, List<Statement> statements, List<Entry> entries) {
        List<Statement> sorted = statements.stream().sorted(Comparator.comparing(Statement::date)).toList();
        List<BigDecimal> differences = new ArrayList<>();
        LocalDate from = null;
        LocalDate to = null;
        Statement previous = null;
        for (Statement statement : sorted) {
            if (previous != null && java.time.YearMonth.from(statement.date()).equals(month)) {
                if (differences.isEmpty()) {
                    from = previous.date();
                }
                differences.add(difference(previous, entries, statement));
                to = statement.date();
            }
            previous = statement;
        }
        return new Period(differences, from, to);
    }
}
