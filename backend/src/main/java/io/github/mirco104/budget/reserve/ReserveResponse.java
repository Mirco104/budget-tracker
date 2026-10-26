package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.recurring.RecurringInterval;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reserve analysis for one month (R14, spec section 5). series are the expense series active in the month,
 * forecast has exactly 12 months (the month and the following 11). Amounts in forecast are costs, so positive.
 */
public record ReserveResponse(
        String month,
        BigDecimal averageMonthlyCosts,
        BigDecimal reserve,
        List<SeriesLine> series,
        List<ForecastMonth> forecast) {

    /** amount is signed (negative); monthlyEquivalent is |amount| / interval in months, rounded to cents. */
    public record SeriesLine(
            long id, String description, String categoryName, BigDecimal amount, RecurringInterval interval,
            BigDecimal monthlyEquivalent) {
    }

    public record ForecastMonth(String month, BigDecimal total, List<ForecastOccurrence> occurrences) {
    }

    public record ForecastOccurrence(long recurringId, String description, LocalDate date, BigDecimal amount) {
    }
}
