package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.transaction.TransactionResponse.Kind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Analysis for one month (R14, spec section 5). averageExpenses is the average expense actual of the completed
 * months counted (averageMonths, 0 to 12), null without such a month. monthlyReserve and reserveBalance sum the
 * reserve pot. forecast has exactly 12 months (the month and the following 11). Amounts in reserves and forecast
 * are costs, so positive.
 */
public record ReserveResponse(
        String month,
        BigDecimal averageExpenses,
        int averageMonths,
        BigDecimal monthlyReserve,
        BigDecimal reserveBalance,
        List<ReserveLine> reserves,
        List<ForecastMonth> forecast) {

    /** A rare expense in the pot: nextDate is its next occurrence, saved what should be put aside by now. */
    public record ReserveLine(
            long recurringId, String description, String categoryName, BigDecimal amount, RecurringInterval interval,
            LocalDate nextDate, BigDecimal monthly, BigDecimal saved) {
    }

    public record ForecastMonth(String month, BigDecimal total, List<ForecastEntry> entries) {
    }

    /** A single expense (kind SINGLE, id) or an occurrence of a series (kind RECURRING, recurringId). */
    public record ForecastEntry(
            Kind kind, Long id, Long recurringId, String description, LocalDate date, BigDecimal amount) {
    }
}
