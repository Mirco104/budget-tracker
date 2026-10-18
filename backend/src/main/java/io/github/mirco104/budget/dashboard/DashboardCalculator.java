package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryGroup;
import io.github.mirco104.budget.category.CategoryType;
import io.github.mirco104.budget.dashboard.DashboardResponse.CategoryLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.DueLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.IncomeLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.Totals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard math, rules R1–R9 and R12 from docs/spezifikation.md, section 2. No Spring dependencies.
 * Amounts are rounded to cents per category; totals add up the rounded values.
 */
public class DashboardCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 4;
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    public record CategoryData(
            long id, String name, CategoryType type, CategoryGroup group,
            BigDecimal targetAmount, BigDecimal targetPercent, boolean system) {
    }

    public record TransactionData(long categoryId, BigDecimal amount, LocalDate date) {
    }

    /** validTo is inclusive; null means open-ended. dueDate is optional (R12). */
    public record YearlyTransactionData(
            long id, long categoryId, String description, BigDecimal amountPerYear, LocalDate dueDate,
            YearMonth validFrom, YearMonth validTo) {
    }

    public DashboardResponse calculate(
            List<CategoryData> categories, List<TransactionData> transactions,
            List<YearlyTransactionData> yearlyTransactions, YearMonth month) {
        Map<Long, BigDecimal> yearlyShares = yearlySharesByCategory(yearlyTransactions, month);
        Map<Long, BigDecimal> actuals = actualsByCategory(transactions, yearlyShares, month);
        List<CategoryData> expenseCategories = categories.stream()
                .filter(category -> category.type() == CategoryType.EXPENSE)
                .toList();
        List<CategoryData> incomeCategories = categories.stream()
                .filter(category -> category.type() == CategoryType.INCOME)
                .toList();

        // R2
        BigDecimal incomeTotal = incomeCategories.stream()
                .map(category -> actualOf(actuals, category))
                .reduce(zero(), BigDecimal::add);

        // R3 for regular categories, then R4 for the remainder
        Map<Long, BigDecimal> targets = new HashMap<>();
        BigDecimal plannedTotal = zero();
        for (CategoryData category : expenseCategories) {
            if (category.system()) {
                continue;
            }
            BigDecimal target = target(category, incomeTotal);
            if (target != null) {
                targets.put(category.id(), target);
                plannedTotal = plannedTotal.add(target);
            }
        }
        BigDecimal remainderTarget = incomeTotal.subtract(plannedTotal);

        // R5, R6
        List<CategoryLine> categoryLines = new ArrayList<>();
        BigDecimal expensesTarget = zero();
        BigDecimal expensesActual = zero();
        BigDecimal yearlyExpensesShare = zero();
        for (CategoryData category : expenseCategories) {
            BigDecimal target = category.system() ? remainderTarget : targets.get(category.id());
            BigDecimal actual = actualOf(actuals, category);
            BigDecimal actualYearly = actualOf(yearlyShares, category);
            BigDecimal difference = target == null ? null : target.subtract(actual);
            categoryLines.add(new CategoryLine(
                    category.id(), category.name(), category.group(), target, actual, actualYearly, difference));
            if (target != null) {
                expensesTarget = expensesTarget.add(target);
            }
            expensesActual = expensesActual.add(actual);
            // R12
            yearlyExpensesShare = yearlyExpensesShare.add(actualYearly);
        }

        // R7
        BigDecimal surplus = incomeTotal.subtract(expensesActual);
        String surplusLabel = surplus.signum() >= 0 ? "Monatlicher Überschuss" : "Monatlicher Verlust";

        // R8
        BigDecimal savingsRate = incomeTotal.signum() == 0
                ? null
                : surplus.divide(incomeTotal, RATIO_SCALE, RoundingMode.HALF_UP);

        // R9
        List<IncomeLine> incomeLines = incomeCategories.stream()
                .map(category -> {
                    BigDecimal actual = actualOf(actuals, category);
                    BigDecimal share = incomeTotal.signum() == 0
                            ? BigDecimal.ZERO.setScale(RATIO_SCALE)
                            : actual.divide(incomeTotal, RATIO_SCALE, RoundingMode.HALF_UP);
                    return new IncomeLine(
                            category.id(), category.name(), actual, actualOf(yearlyShares, category), share);
                })
                .toList();

        return new DashboardResponse(
                month.toString(),
                incomeTotal,
                new Totals(expensesTarget, expensesActual, expensesTarget.subtract(expensesActual)),
                surplus,
                surplusLabel,
                savingsRate,
                categoryLines,
                incomeLines,
                yearlyExpensesShare,
                dueInMonth(yearlyTransactions, categories, month));
    }

    // R1: transactions of the month plus the yearly share, per category
    private static Map<Long, BigDecimal> actualsByCategory(
            List<TransactionData> transactions, Map<Long, BigDecimal> yearlyShares, YearMonth month) {
        Map<Long, BigDecimal> monthly = new HashMap<>();
        for (TransactionData transaction : transactions) {
            if (YearMonth.from(transaction.date()).equals(month)) {
                monthly.merge(transaction.categoryId(), transaction.amount(), BigDecimal::add);
            }
        }

        Map<Long, BigDecimal> actuals = new HashMap<>();
        monthly.forEach((categoryId, sum) -> actuals.put(categoryId, money(sum)));
        yearlyShares.forEach((categoryId, share) -> actuals.merge(categoryId, share, BigDecimal::add));
        return actuals;
    }

    // R1: yearly sum / 12 of the yearly transactions valid in the month, per category (summed before rounding)
    private static Map<Long, BigDecimal> yearlySharesByCategory(
            List<YearlyTransactionData> yearlyTransactions, YearMonth month) {
        Map<Long, BigDecimal> yearly = new HashMap<>();
        for (YearlyTransactionData transaction : yearlyTransactions) {
            if (isValidIn(transaction, month)) {
                yearly.merge(transaction.categoryId(), transaction.amountPerYear(), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> shares = new HashMap<>();
        yearly.forEach((categoryId, sum) -> shares.put(categoryId, monthlyShare(sum)));
        return shares;
    }

    // R12: due in the calendar month of dueDate, every year, while valid; Feb 29 falls back to Feb 28
    private static List<DueLine> dueInMonth(
            List<YearlyTransactionData> yearlyTransactions, List<CategoryData> categories, YearMonth month) {
        Map<Long, String> categoryNames = new HashMap<>();
        categories.forEach(category -> categoryNames.put(category.id(), category.name()));
        return yearlyTransactions.stream()
                .filter(transaction -> transaction.dueDate() != null
                        && transaction.dueDate().getMonth() == month.getMonth()
                        && isValidIn(transaction, month))
                .map(transaction -> new DueLine(
                        transaction.id(),
                        transaction.description(),
                        categoryNames.get(transaction.categoryId()),
                        money(transaction.amountPerYear()),
                        transaction.dueDate().withYear(month.getYear())))
                .sorted(Comparator.comparing(DueLine::dueDate).thenComparing(DueLine::id))
                .toList();
    }

    /** R1: share of a yearly amount counted per month, amount / 12 rounded to cents. */
    public static BigDecimal monthlyShare(BigDecimal amountPerYear) {
        return amountPerYear.divide(MONTHS_PER_YEAR, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean isValidIn(YearlyTransactionData transaction, YearMonth month) {
        return !transaction.validFrom().isAfter(month)
                && (transaction.validTo() == null || !transaction.validTo().isBefore(month));
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

    private static BigDecimal actualOf(Map<Long, BigDecimal> actuals, CategoryData category) {
        return actuals.getOrDefault(category.id(), zero());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }
}
