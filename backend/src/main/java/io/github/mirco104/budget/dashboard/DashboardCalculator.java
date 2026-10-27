package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.dashboard.DashboardResponse.CategoryLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.IncomeLine;
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
 * Dashboard math, rules R1–R12 from docs/spezifikation.md, section 2. No Spring dependencies.
 * Amounts carry their sign (R11). The net per category is rounded to cents; totals add up the rounded values.
 */
public class DashboardCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 4;

    public record CategoryData(long id, String name, BigDecimal targetAmount, BigDecimal targetPercent) {
    }

    /**
     * amount is signed: positive is income, negative is expense (R11).
     * recurring is true for an occurrence of a recurring transaction (R12), false for a single transaction.
     */
    public record TransactionData(long categoryId, BigDecimal amount, LocalDate date, boolean recurring) {
    }

    public DashboardResponse calculate(
            List<CategoryData> categories, List<TransactionData> transactions, YearMonth month) {
        Map<Long, BigDecimal> nets = netsByCategory(transactions, month);
        Map<Long, BigDecimal> recurringExpenses = recurringExpensesByCategory(transactions, month);

        // R2
        BigDecimal incomeTotal = categories.stream()
                .map(category -> netOf(nets, category))
                .filter(net -> net.signum() > 0)
                .reduce(zero(), BigDecimal::add);

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
            BigDecimal target = target(category, incomeTotal);
            if (target == null && actual.signum() == 0) {
                continue;
            }
            BigDecimal difference = target == null ? null : target.subtract(actual);
            categoryLines.add(new CategoryLine(
                    category.id(), category.name(), target, actual, difference,
                    share(target, incomeTotal), share(actual, incomeTotal), share(difference, incomeTotal)));
            if (target != null) {
                expensesTarget = expensesTarget.add(target);
            }
            expensesActual = expensesActual.add(actual);
        }
        BigDecimal expensesDifference = expensesTarget.subtract(expensesActual);
        BigDecimal expensesOneOff = expensesActual.subtract(expensesRecurring);

        // R4
        BigDecimal unplanned = incomeTotal.subtract(expensesTarget);
        String unplannedLabel = unplanned.signum() >= 0 ? "Nicht verplant" : "Überplant";

        // R7
        BigDecimal surplus = incomeTotal.subtract(expensesActual);
        String surplusLabel = surplus.signum() >= 0 ? "Monatlicher Überschuss" : "Monatlicher Verlust";

        // R9: every category with a positive net
        List<IncomeLine> incomeLines = categories.stream()
                .filter(category -> netOf(nets, category).signum() > 0)
                .map(category -> {
                    BigDecimal net = netOf(nets, category);
                    return new IncomeLine(category.id(), category.name(), net, share(net, incomeTotal));
                })
                .toList();

        // R8: every amount also as share of the income
        return new DashboardResponse(
                month.toString(),
                incomeTotal,
                share(incomeTotal, incomeTotal),
                new Totals(expensesTarget, expensesActual, expensesDifference, share(expensesTarget, incomeTotal),
                        share(expensesActual, incomeTotal), share(expensesDifference, incomeTotal),
                        expensesRecurring, expensesOneOff, share(expensesRecurring, incomeTotal),
                        share(expensesOneOff, incomeTotal)),
                unplanned,
                unplannedLabel,
                surplus,
                surplusLabel,
                share(surplus, incomeTotal),
                categoryLines,
                incomeLines);
    }

    // R8: share of the income, empty without an amount or without income
    private static BigDecimal share(BigDecimal value, BigDecimal incomeTotal) {
        if (value == null || incomeTotal.signum() == 0) {
            return null;
        }
        return value.divide(incomeTotal, RATIO_SCALE, RoundingMode.HALF_UP);
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

    // R3: euro target wins, otherwise income × percent, otherwise no target
    private static BigDecimal target(CategoryData category, BigDecimal incomeTotal) {
        if (category.targetAmount() != null) {
            return money(category.targetAmount());
        }
        if (category.targetPercent() != null) {
            return money(incomeTotal.multiply(category.targetPercent()));
        }
        return null;
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
