package io.github.mirco104.budget.recurring;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule R25 from docs/spezifikation.md: the fixed costs F(k, M) of a category are the monthly shares |amount| / interval
 * of its series with a negative amount that run on the last day of M (decision 36). Exceptions (R13) do not count,
 * the share is a planning value and not an actual. No Spring dependencies.
 */
public final class FixedCosts {

    private static final int MONEY_SCALE = 2;

    /** A series as R12 describes it; endDate is null for an open-ended series. */
    public record SeriesData(
            long id, long categoryId, String description, BigDecimal amount, int intervalMonths, LocalDate startDate,
            LocalDate endDate) {
    }

    /** One series of a category: amount without sign, monthly its share per month, both rounded to cents. */
    public record Item(long recurringId, String description, BigDecimal amount, int intervalMonths, BigDecimal monthly) {
    }

    /** total is the sum of the unrounded shares, rounded once at the end. */
    public record Result(BigDecimal total, List<Item> items) {
    }

    private FixedCosts() {
    }

    /** Per category id the fixed costs in the month; categories without a counted series are missing. */
    public static Map<Long, Result> of(List<SeriesData> series, YearMonth month) {
        LocalDate lastDay = month.atEndOfMonth();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        Map<Long, List<Item>> items = new LinkedHashMap<>();
        for (SeriesData s : series) {
            if (s.amount().signum() >= 0 || !runsOn(s, lastDay)) {
                continue;
            }
            BigDecimal amount = s.amount().abs();
            // Full precision for the total; 100 / 3 must not lose its fraction before the sum
            BigDecimal share = amount.divide(BigDecimal.valueOf(s.intervalMonths()), MathContext.DECIMAL64);
            totals.merge(s.categoryId(), share, BigDecimal::add);
            items.computeIfAbsent(s.categoryId(), id -> new ArrayList<>()).add(new Item(
                    s.id(), s.description(), money(amount), s.intervalMonths(), money(share)));
        }
        Map<Long, Result> result = new LinkedHashMap<>();
        totals.forEach((categoryId, total) ->
                result.put(categoryId, new Result(money(total), List.copyOf(items.get(categoryId)))));
        return result;
    }

    private static boolean runsOn(SeriesData series, LocalDate day) {
        return !series.startDate().isAfter(day) && (series.endDate() == null || !series.endDate().isBefore(day));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
