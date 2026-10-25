package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryGroup;
import io.github.mirco104.budget.dashboard.DashboardResponse.CategoryLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.GroupLine;
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
import java.util.Optional;

/**
 * Dashboard math, rules R1–R11 from docs/spezifikation.md, section 2. No Spring dependencies.
 * Amounts carry their sign (R11). The net per category is rounded to cents; totals add up the rounded values.
 */
public class DashboardCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 4;

    /** group null means "ohne Gruppe". */
    public record CategoryData(
            long id, String name, CategoryGroup group, BigDecimal targetAmount, BigDecimal targetPercent) {
    }

    /** amount is signed: positive is income, negative is expense (R11). */
    public record TransactionData(long categoryId, BigDecimal amount, LocalDate date) {
    }

    public DashboardResponse calculate(
            List<CategoryData> categories, List<TransactionData> transactions, YearMonth month) {
        Map<Long, BigDecimal> nets = netsByCategory(transactions, month);

        // R2
        BigDecimal incomeTotal = categories.stream()
                .map(category -> netOf(nets, category))
                .filter(net -> net.signum() > 0)
                .reduce(zero(), BigDecimal::add);

        // R3, R5, R6; R10: categories with a group and a target or expenses, plus ungrouped ones with expenses
        List<CategoryLine> categoryLines = new ArrayList<>();
        BigDecimal expensesTarget = zero();
        BigDecimal expensesActual = zero();
        for (CategoryData category : categories) {
            BigDecimal actual = expenseOf(netOf(nets, category));
            BigDecimal target = category.group() == null ? null : target(category, incomeTotal);
            if (target == null && actual.signum() == 0) {
                continue;
            }
            BigDecimal difference = target == null ? null : target.subtract(actual);
            categoryLines.add(new CategoryLine(
                    category.id(), category.name(), category.group(), target, actual, difference,
                    share(target, incomeTotal), share(actual, incomeTotal), share(difference, incomeTotal)));
            if (target != null) {
                expensesTarget = expensesTarget.add(target);
            }
            expensesActual = expensesActual.add(actual);
        }
        BigDecimal expensesDifference = expensesTarget.subtract(expensesActual);

        // R4
        BigDecimal unplanned = incomeTotal.subtract(expensesTarget);

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
                        share(expensesActual, incomeTotal), share(expensesDifference, incomeTotal)),
                unplanned,
                surplus,
                surplusLabel,
                share(surplus, incomeTotal),
                groupLines(categoryLines, incomeTotal),
                categoryLines,
                incomeLines);
    }

    // R10: every group with category lines in enum order, summed from them; a missing target counts as 0 (like R6).
    // Expenses of ungrouped categories get a line with group null, so the group totals add up to the expenses totals.
    private static List<GroupLine> groupLines(List<CategoryLine> categoryLines, BigDecimal incomeTotal) {
        List<GroupLine> groupLines = new ArrayList<>();
        for (CategoryGroup group : CategoryGroup.values()) {
            groupLine(group, categoryLines, incomeTotal).ifPresent(groupLines::add);
        }
        groupLine(null, categoryLines, incomeTotal).ifPresent(groupLines::add);
        return groupLines;
    }

    private static Optional<GroupLine> groupLine(
            CategoryGroup group, List<CategoryLine> categoryLines, BigDecimal incomeTotal) {
        List<CategoryLine> lines = categoryLines.stream().filter(line -> line.group() == group).toList();
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal target = zero();
        BigDecimal actual = zero();
        for (CategoryLine line : lines) {
            if (line.target() != null) {
                target = target.add(line.target());
            }
            actual = actual.add(line.actual());
        }
        BigDecimal difference = target.subtract(actual);
        return Optional.of(new GroupLine(group, target, actual, difference, share(target, incomeTotal),
                share(actual, incomeTotal), share(difference, incomeTotal)));
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
