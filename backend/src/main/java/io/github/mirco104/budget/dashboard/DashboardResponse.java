package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryGroup;
import java.math.BigDecimal;
import java.util.List;

/**
 * Calculated dashboard for one month (spec section 5). Fields without a value are null.
 * difference is the deviation from the target (R5, R6), unplanned is the income without a target (R4).
 * categories[].actual is the expense actual I(k), income[].actual is the net N(k) (R2, R9).
 */
public record DashboardResponse(
        String month,
        BigDecimal incomeTotal,
        Totals expenses,
        BigDecimal unplanned,
        BigDecimal surplus,
        String surplusLabel,
        BigDecimal savingsRate,
        List<GroupLine> groups,
        List<CategoryLine> categories,
        List<IncomeLine> income) {

    public record Totals(BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    /**
     * Sums of the expense lines in one group (R10); target counts missing targets as 0.
     * group null sums the ungrouped categories with expenses and is only present if there are any.
     */
    public record GroupLine(CategoryGroup group, BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    /** group null for an ungrouped category with expenses. */
    public record CategoryLine(
            long id, String name, CategoryGroup group, BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    public record IncomeLine(long id, String name, BigDecimal actual, BigDecimal share) {
    }
}
