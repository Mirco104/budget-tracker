package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryGroup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Calculated dashboard for one month (spec section 5). Fields without a value are null.
 * actualYearly is the R1 share from yearly transactions and already part of actual (R12).
 */
public record DashboardResponse(
        String month,
        BigDecimal incomeTotal,
        Totals expenses,
        BigDecimal surplus,
        String surplusLabel,
        BigDecimal changePercent,
        String changeLabel,
        List<GroupLine> groups,
        List<CategoryLine> categories,
        List<IncomeLine> income,
        BigDecimal yearlyExpensesShare,
        List<DueLine> dueYearly) {

    public record Totals(BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    /** Sums of the expense categories in one group (R10); target counts missing targets as 0. */
    public record GroupLine(CategoryGroup group, BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    public record CategoryLine(
            long id, String name, CategoryGroup group, BigDecimal target, BigDecimal actual, BigDecimal actualYearly,
            BigDecimal difference) {
    }

    public record IncomeLine(long id, String name, BigDecimal actual, BigDecimal actualYearly, BigDecimal share) {
    }

    /** Yearly debit due in the month (R12), with its full yearly amount and the date in this month. */
    public record DueLine(long id, String description, String categoryName, BigDecimal amountPerYear,
            LocalDate dueDate) {
    }
}
