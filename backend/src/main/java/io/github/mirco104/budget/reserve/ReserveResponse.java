package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.transaction.TransactionResponse.Kind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Analysis for one month (R14, spec section 5). averageIncome, averageExpenses and averageSurplus average the
 * completed months counted (averageMonths, 0 to the chosen period), null without such a month; history lists these months.
 * totalIncome, totalExpenses and totalSurplus sum them, also null without such a month; incomeShare,
 * expensesShare and surplusShare are the shares of the totals (and so of the averages) in totalIncome, null when
 * it is 0.
 * monthlyReserve and reserveBalance sum the reserve pot. forecast has exactly 12 months (the month and the
 * following 11). Amounts in reserves and forecast are costs, so positive.
 */
public record ReserveResponse(
        String month,
        BigDecimal averageIncome,
        BigDecimal averageExpenses,
        BigDecimal averageSurplus,
        int averageMonths,
        List<HistoryMonth> history,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalSurplus,
        BigDecimal incomeShare,
        BigDecimal expensesShare,
        BigDecimal surplusShare,
        BigDecimal monthlyReserve,
        BigDecimal reserveBalance,
        List<ReserveLine> reserves,
        List<ForecastMonth> forecast) {

    /**
     * A past month: income E, expenses A and surplus E - A (R2, R7), with the amounts per category. The shares
     * are parts of the income of the month, null when it is 0.
     */
    public record HistoryMonth(
            String month, BigDecimal income, BigDecimal expenses, BigDecimal surplus,
            BigDecimal incomeShare, BigDecimal expensesShare, BigDecimal surplusShare,
            List<CategoryAmount> incomeCategories, List<CategoryAmount> expenseCategories) {
    }

    public record CategoryAmount(String name, BigDecimal amount, BigDecimal share) {
    }

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
