package io.github.mirco104.budget.plan;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules P2–P4 and P9 from docs/spezifikation.md, section 19: monthly share of a planned item, plan per category,
 * groups, "nicht verplant" and saving rate. Full precision, rounded only at the end. No Spring dependencies.
 */
public final class PlanCalculator {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal WEEKS_PER_MONTH = new BigDecimal(52).divide(new BigDecimal(12), MathContext.DECIMAL64);

    /** A planned item version; amount is signed (R11), categoryId null only for saving, unit null = no fixed date. */
    public record ItemData(
            long id, Long categoryId, BigDecimal amount, boolean saving, RhythmUnit unit, Integer count,
            YearMonth validFrom, YearMonth validTo) {
    }

    /** Group of an item (P4). */
    public enum Group {
        INCOME, FIXED, BUDGET, SAVING
    }

    /** All sums rounded to cents; savingRate in percent with one decimal, null without planned income. */
    public record Result(
            BigDecimal income, BigDecimal fixed, BigDecimal budgets, BigDecimal saving, BigDecimal unplanned,
            BigDecimal savingRate, BigDecimal planTotal, Map<Long, BigDecimal> planByCategory) {
    }

    private PlanCalculator() {
    }

    /** P2: monthly share without sign, rounded to cents. */
    public static BigDecimal monthlyShare(BigDecimal amount, RhythmUnit unit, Integer count) {
        return money(exactShare(amount, unit, count));
    }

    /** P4: the group of an item follows from its fields. */
    public static Group groupOf(ItemData item) {
        return groupOf(item.saving(), item.amount(), item.unit());
    }

    public static Group groupOf(boolean saving, BigDecimal amount, RhythmUnit unit) {
        if (saving) {
            return Group.SAVING;
        }
        if (amount.signum() > 0) {
            return Group.INCOME;
        }
        return unit == null ? Group.BUDGET : Group.FIXED;
    }

    /** P3: validFrom ≤ month and (validTo empty or validTo ≥ month). */
    public static boolean validIn(ItemData item, YearMonth month) {
        return !item.validFrom().isAfter(month) && (item.validTo() == null || !item.validTo().isBefore(month));
    }

    /** P3, P4, P9 for the items valid in the month. */
    public static Result of(List<ItemData> items, YearMonth month) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal fixed = BigDecimal.ZERO;
        BigDecimal budgets = BigDecimal.ZERO;
        BigDecimal saving = BigDecimal.ZERO;
        Map<Long, BigDecimal> perCategory = new LinkedHashMap<>();
        for (ItemData item : items) {
            if (!validIn(item, month)) {
                continue;
            }
            BigDecimal share = exactShare(item.amount(), item.unit(), item.count());
            switch (groupOf(item)) {
                case INCOME -> income = income.add(share);
                case SAVING -> saving = saving.add(share);
                case FIXED -> fixed = fixed.add(share);
                case BUDGET -> budgets = budgets.add(share);
            }
            if (!item.saving() && item.amount().signum() < 0 && item.categoryId() != null) {
                perCategory.merge(item.categoryId(), share, BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> planByCategory = new LinkedHashMap<>();
        perCategory.forEach((id, sum) -> planByCategory.put(id, money(sum)));
        BigDecimal unplanned = income.subtract(fixed).subtract(budgets).subtract(saving);
        BigDecimal savingRate = income.signum() == 0 ? null
                : saving.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP);
        return new Result(money(income), money(fixed), money(budgets), money(saving), money(unplanned), savingRate,
                money(fixed.add(budgets)), planByCategory);
    }

    /** Subtotal per category id of the items of one group valid in the month, rounded at the end (planning page). */
    public static Map<Long, BigDecimal> categoryTotals(List<ItemData> items, YearMonth month, Group group) {
        Map<Long, BigDecimal> sums = new LinkedHashMap<>();
        for (ItemData item : items) {
            if (validIn(item, month) && groupOf(item) == group && item.categoryId() != null) {
                sums.merge(item.categoryId(), exactShare(item.amount(), item.unit(), item.count()), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        sums.forEach((id, sum) -> totals.put(id, money(sum)));
        return totals;
    }

    // P2 without rounding: week = amount × 52/12 ÷ count, month = amount ÷ count, no fixed date = amount
    static BigDecimal exactShare(BigDecimal amount, RhythmUnit unit, Integer count) {
        BigDecimal value = amount.abs();
        if (unit == null) {
            return value;
        }
        BigDecimal perMonth = unit == RhythmUnit.WEEK ? value.multiply(WEEKS_PER_MONTH, MathContext.DECIMAL64) : value;
        return perMonth.divide(BigDecimal.valueOf(count), MathContext.DECIMAL64);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
