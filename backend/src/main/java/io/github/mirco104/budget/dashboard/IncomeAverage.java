package io.github.mirco104.budget.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Rule R23 from docs/spezifikation.md: the average income Ē(M) of the 12 months before M, counting only months
 * before the current month H and from the month of the first entry on. The same rule averages the expenses Ā(M)
 * when it gets A per month instead of E (decision 34). No Spring dependencies.
 */
public final class IncomeAverage {

    static final int MONTHS = 12;

    private static final int MONEY_SCALE = 2;

    /** average is null and from/to are null when no month counts (n = 0). */
    public record Result(BigDecimal average, int months, YearMonth from, YearMonth to) {

        public static final Result EMPTY = new Result(null, 0, null, null);
    }

    private IncomeAverage() {
    }

    /**
     * income holds E (or A) per month (R2); a missing month counts as 0. firstEntryMonth is null without any entry.
     */
    public static Result of(
            Map<YearMonth, BigDecimal> income, YearMonth firstEntryMonth, YearMonth month, YearMonth currentMonth) {
        if (firstEntryMonth == null) {
            return Result.EMPTY;
        }
        YearMonth from = later(month.minusMonths(MONTHS), firstEntryMonth);
        YearMonth to = earlier(month.minusMonths(1), currentMonth.minusMonths(1));
        if (from.isAfter(to)) {
            return Result.EMPTY;
        }
        int months = (int) ChronoUnit.MONTHS.between(from, to) + 1;
        BigDecimal sum = BigDecimal.ZERO;
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            sum = sum.add(income.getOrDefault(m, BigDecimal.ZERO));
        }
        BigDecimal average = sum.divide(BigDecimal.valueOf(months), MONEY_SCALE, RoundingMode.HALF_UP);
        return new Result(average, months, from, to);
    }

    /** The months whose income {@link #of} needs; empty when none counts. */
    public static Result window(YearMonth firstEntryMonth, YearMonth month, YearMonth currentMonth) {
        return of(Map.of(), firstEntryMonth, month, currentMonth);
    }

    private static YearMonth later(YearMonth a, YearMonth b) {
        return a.isAfter(b) ? a : b;
    }

    private static YearMonth earlier(YearMonth a, YearMonth b) {
        return a.isBefore(b) ? a : b;
    }
}
