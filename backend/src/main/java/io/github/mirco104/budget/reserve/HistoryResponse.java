package io.github.mirco104.budget.reserve;

import java.math.BigDecimal;
import java.util.List;

/**
 * Look back from month {@code from} to month {@code to} (R14, spec section 5). {@code from} and {@code to} are the
 * requested period; {@code months} is n, the months counted from the first entry on, and {@code history} lists
 * exactly these months, oldest first. The averages and {@code total} are null when n is 0. All amounts are in euros.
 */
public record HistoryResponse(
        String from,
        String to,
        int months,
        BigDecimal averageIncome,
        BigDecimal averageExpenses,
        BigDecimal averageSurplus,
        Total total,
        List<HistoryMonth> history) {

    /** Sums of income E, expenses A and surplus E - A over the counted months. */
    public record Total(BigDecimal income, BigDecimal expenses, BigDecimal surplus) {
    }

    /** A past month: income E, expenses A and surplus E - A (R2, R7), with the amounts per category. */
    public record HistoryMonth(
            String month, BigDecimal income, BigDecimal expenses, BigDecimal surplus,
            List<CategoryAmount> incomeCategories, List<CategoryAmount> expenseCategories) {
    }

    /** Income N(k) or expenses I(k) of a category in the month, positive, with its icon (US28). */
    public record CategoryAmount(long id, String name, String icon, BigDecimal amount) {
    }
}
