package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.dashboard.DashboardResponse.CategoryLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.IncomeLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.MonthState;
import io.github.mirco104.budget.dashboard.DashboardResponse.Totals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard math, rules R1–R7, R9–R12 and R23 from docs/spezifikation.md, section 2. No Spring dependencies.
 * Amounts carry their sign (R11). The net per category is rounded to cents; totals add up the rounded values.
 */
public class DashboardCalculator {

    private static final int MONEY_SCALE = 2;

    public record CategoryData(long id, String name, BigDecimal targetAmount) {
    }

    /**
     * amount is signed: positive is income, negative is expense (R11).
     * recurring is true for an occurrence of a recurring transaction (R12), false for a single transaction.
     */
    public record TransactionData(long categoryId, BigDecimal amount, LocalDate date, boolean recurring) {
    }

    /**
     * average is Ē(M) from {@link IncomeAverage} (R23); averageExpenses is Ā(M) over the same months, null when
     * no month counts; currentMonth is the month of today (R7).
     */
    public DashboardResponse calculate(
            List<CategoryData> categories, List<TransactionData> transactions, YearMonth month,
            IncomeAverage.Result average, BigDecimal averageExpenses, YearMonth currentMonth) {
        Map<Long, BigDecimal> nets = netsByCategory(transactions, month);
        Map<Long, BigDecimal> recurringExpenses = recurringExpensesByCategory(transactions, month);

        // R2
        BigDecimal incomeTotal = positiveSum(nets);
        BigDecimal averageIncome = average.average();

        // R3, R5, R6; R10: every category with a target or expenses, in input order
        List<CategoryLine> categoryLines = new ArrayList<>();
        BigDecimal expensesTarget = zero();
        BigDecimal expensesActual = zero();
        BigDecimal expensesRecurring = zero();
        for (CategoryData category : categories) {
            BigDecimal actual = expenseOf(netOf(nets, category));
            // R10: W(k) = min(I(k), V(k)), so a refund lowers the one-off part first
            expensesRecurring = expensesRecurring.add(
                    actual.min(recurringExpenses.getOrDefault(category.id(), zero())));
            BigDecimal target = target(category);
            if (target == null && actual.signum() == 0) {
                continue;
            }
            BigDecimal difference = target == null ? null : target.subtract(actual);
            categoryLines.add(new CategoryLine(category.id(), category.name(), target, actual, difference));
            if (target != null) {
                expensesTarget = expensesTarget.add(target);
            }
            expensesActual = expensesActual.add(actual);
        }
        BigDecimal expensesDifference = expensesTarget.subtract(expensesActual);
        BigDecimal expensesOneOff = expensesActual.subtract(expensesRecurring);

        // R4: based on the average and on the minimum income (decision 36); empty without them
        BigDecimal minimumIncome = average.minimum();
        BigDecimal unplanned = averageIncome == null ? null : money(averageIncome).subtract(expensesTarget);
        BigDecimal unplannedMinimum = minimumIncome == null ? null : money(minimumIncome).subtract(expensesTarget);

        // R7: a past month has a surplus, the current and later months show what is still free
        BigDecimal surplus = incomeTotal.subtract(expensesActual);
        MonthState monthState = month.isBefore(currentMonth) ? MonthState.PAST
                : month.equals(currentMonth) ? MonthState.CURRENT : MonthState.FUTURE;
        String surplusLabel = monthState == MonthState.PAST
                ? surplus.signum() >= 0 ? "Monatlicher Überschuss" : "Monatlicher Verlust"
                : surplus.signum() >= 0 ? "Noch frei" : "Überschritten";

        // R9: every category with a positive net
        List<IncomeLine> incomeLines = categories.stream()
                .filter(category -> netOf(nets, category).signum() > 0)
                .map(category -> new IncomeLine(category.id(), category.name(), netOf(nets, category)))
                .toList();

        return new DashboardResponse(
                month.toString(),
                monthState,
                incomeTotal,
                averageIncome == null ? null : money(averageIncome),
                minimumIncome == null ? null : money(minimumIncome),
                averageExpenses == null ? null : money(averageExpenses),
                average.months(),
                average.from() == null ? null : average.from().toString(),
                average.to() == null ? null : average.to().toString(),
                new Totals(expensesTarget, expensesActual, expensesDifference, expensesRecurring, expensesOneOff),
                unplanned,
                unplannedLabel(unplanned),
                unplannedMinimum,
                unplannedLabel(unplannedMinimum),
                surplus,
                surplusLabel,
                categoryLines,
                incomeLines,
                null,
                null,
                null,
                List.of());
    }

    // R1: sum of the signed amounts of the month, per category
    private static Map<Long, BigDecimal> netsByCategory(List<TransactionData> transactions, YearMonth month) {
        Map<Long, BigDecimal> sums = new HashMap<>();
        for (TransactionData transaction : transactions) {
            if (YearMonth.from(transaction.date()).equals(month)) {
                sums.merge(transaction.categoryId(), transaction.amount(), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> nets = new HashMap<>();
        sums.forEach((categoryId, sum) -> nets.put(categoryId, money(sum)));
        return nets;
    }

    // R10: V(k) = sum of |amount| of the occurrences with a negative amount in the month, per category
    private static Map<Long, BigDecimal> recurringExpensesByCategory(
            List<TransactionData> transactions, YearMonth month) {
        Map<Long, BigDecimal> sums = new HashMap<>();
        for (TransactionData transaction : transactions) {
            if (transaction.recurring() && transaction.amount().signum() < 0
                    && YearMonth.from(transaction.date()).equals(month)) {
                sums.merge(transaction.categoryId(), transaction.amount().negate(), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> expenses = new HashMap<>();
        sums.forEach((categoryId, sum) -> expenses.put(categoryId, money(sum)));
        return expenses;
    }

    // R2: I(k) = -N(k) for a negative net, otherwise 0
    private static BigDecimal expenseOf(BigDecimal net) {
        return net.signum() < 0 ? net.negate() : zero();
    }

    /** Income E of a month (R2): the sum of all positive nets per category; for Ē(M) (R23). */
    public static BigDecimal incomeOf(List<TransactionData> transactions, YearMonth month) {
        return positiveSum(netsByCategory(transactions, month));
    }

    /** Expenses A of a month (R2): the sum of all I(k); for Ā(M) (R23). */
    public static BigDecimal expensesOf(List<TransactionData> transactions, YearMonth month) {
        return netsByCategory(transactions, month).values().stream()
                .map(DashboardCalculator::expenseOf)
                .reduce(zero(), BigDecimal::add);
    }

    private static BigDecimal positiveSum(Map<Long, BigDecimal> nets) {
        return nets.values().stream()
                .filter(net -> net.signum() > 0)
                .reduce(zero(), BigDecimal::add);
    }

    // R4: "Nicht verplant" at or above zero, otherwise "Überplant"
    private static String unplannedLabel(BigDecimal unplanned) {
        return unplanned == null ? null : unplanned.signum() >= 0 ? "Nicht verplant" : "Überplant";
    }

    // R3: the target is the euro amount; only euro since decision 36
    private static BigDecimal target(CategoryData category) {
        return category.targetAmount() == null ? null : money(category.targetAmount());
    }

    private static BigDecimal netOf(Map<Long, BigDecimal> nets, CategoryData category) {
        return nets.getOrDefault(category.id(), zero());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }
}
