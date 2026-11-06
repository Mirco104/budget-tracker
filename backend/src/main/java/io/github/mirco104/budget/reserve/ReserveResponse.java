package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.transaction.TransactionResponse.Kind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outlook from a month on (R14, spec section 5): the reserve pot and the forecast. monthlyReserve and
 * reserveBalance sum the reserve pot, potMonths is its course (decision 37). forecast has exactly 12 months (the
 * month and the following 11). Amounts in reserves and forecast are costs, so positive. The look back is in {@link HistoryResponse} (decision 29).
 */
public record ReserveResponse(
        String month,
        BigDecimal monthlyReserve,
        BigDecimal reserveBalance,
        List<ReserveLine> reserves,
        List<PotMonth> potMonths,
        List<ForecastMonth> forecast) {

    /** A rare expense in the pot: nextDate is its next occurrence, saved what should be put aside by now. */
    public record ReserveLine(
            long recurringId, String description, String categoryName, BigDecimal amount, RecurringInterval interval,
            LocalDate nextDate, BigDecimal monthly, BigDecimal saved) {
    }

    /**
     * Course of the pot in a month (R14, decision 37): balance is what should be in the pot before the due expenses
     * of the month are withdrawn, withdrawal what is withdrawn from it in the month. Exactly 12 months like forecast.
     */
    public record PotMonth(String month, BigDecimal balance, BigDecimal withdrawal) {
    }

    public record ForecastMonth(String month, BigDecimal total, List<ForecastEntry> entries) {
    }

    /** A single expense (kind SINGLE, id) or an occurrence of a series (kind RECURRING, recurringId). */
    public record ForecastEntry(
            Kind kind, Long id, Long recurringId, String description, LocalDate date, BigDecimal amount) {
    }
}
