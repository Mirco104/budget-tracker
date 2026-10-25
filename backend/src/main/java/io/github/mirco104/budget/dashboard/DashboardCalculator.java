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

/**
 * Dashboard math, rules R1–R11 from docs/spezifikation.md, section 2. No Spring dependencies.
 * Amounts carry their sign (R11). The net per category is rounded to cents; totals add up the rounded values.
 */
public class DashboardCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 4;

    /** group null means "ohne Gruppe". */
    public record CategoryData(
            long id, String name, CategoryGroup group, BigDecimal targetAmount, BigDecimal targetPercent,
            boolean system) {
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

        // R3 for the categories of FIXED, FLEXIBLE and SAVINGS, then R4 for the remainder
        Map<Long, BigDecimal> targets = new HashMap<>();
        BigDecimal plannedTotal = zero();
        for (CategoryData category : categories) {
            if (category.system() || category.group() == null) {
                continue;
            }
            BigDecimal target = target(category, incomeTotal);
            if (target != null) {
                targets.put(category.id(), target);
                plannedTotal = plannedTotal.add(target);
            }
        }
        BigDecimal remainderTarget = incomeTotal.subtract(plannedTotal);

        // R5, R6; R10: every category with a group, plus ungrouped ones with expenses
        List<CategoryLine> categoryLines = new ArrayList<>();
        BigDecimal expensesTarget = zero();
        BigDecimal expensesActual = zero();
        for (CategoryData category : categories) {
            BigDecimal actual = expenseOf(netOf(nets, category));
            if (category.group() == null && actual.signum() == 0) {
                continue;
            }
            BigDecimal target = category.system() ? remainderTarget : targets.get(category.id());
            BigDecimal difference = target == null ? null : target.subtract(actual);
            categoryLines.add(new CategoryLine(
                    category.id(), category.name(), category.group(), target, actual, difference));
            if (target != null) {
                expensesTarget = expensesTarget.add(target);
            }
            expensesActual = expensesActual.add(actual);
        }

        // R7
        BigDecimal surplus = incomeTotal.subtract(expensesActual);
        String surplusLabel = surplus.signum() >= 0 ? "Monatlicher Überschuss" : "Monatlicher Verlust";

        // R8
        BigDecimal savingsRate = incomeTotal.signum() == 0
                ? null
                : surplus.divide(incomeTotal, RATIO_SCALE, RoundingMode.HALF_UP);

        // R9: every ungrouped category, plus categories with a group and a positive net
        List<IncomeLine> incomeLines = categories.stream()
                .filter(category -> category.group() == null || netOf(nets, category).signum() > 0)
                .map(category -> {
                    BigDecimal net = netOf(nets, category);
                    BigDecimal share = net.signum() <= 0 || incomeTotal.signum() == 0
                            ? BigDecimal.ZERO.setScale(RATIO_SCALE)
                            : net.divide(incomeTotal, RATIO_SCALE, RoundingMode.HALF_UP);
                    return new IncomeLine(category.id(), category.name(), net, share);
                })
                .toList();

        return new DashboardResponse(
                month.toString(),
                incomeTotal,
                new Totals(expensesTarget, expensesActual, expensesTarget.subtract(expensesActual)),
                surplus,
                surplusLabel,
                savingsRate,
                groupLines(categoryLines),
                categoryLines,
                incomeLines);
    }

    // R10: every group in enum order, summed from the category lines; a missing target counts as 0 (like R6).
    // Expenses of ungrouped categories count towards REMAINDER, so the group actuals add up to the expenses total.
    private static List<GroupLine> groupLines(List<CategoryLine> categoryLines) {
        List<GroupLine> groupLines = new ArrayList<>();
        for (CategoryGroup group : CategoryGroup.values()) {
            BigDecimal target = zero();
            BigDecimal actual = zero();
            for (CategoryLine line : categoryLines) {
                CategoryGroup lineGroup = line.group() == null ? CategoryGroup.REMAINDER : line.group();
                if (lineGroup == group) {
                    if (line.target() != null) {
                        target = target.add(line.target());
                    }
                    actual = actual.add(line.actual());
                }
            }
            groupLines.add(new GroupLine(group, target, actual, target.subtract(actual)));
        }
        return groupLines;
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
