package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryGroup;
import java.math.BigDecimal;
import java.util.List;

/**
 * Calculated dashboard for one month (spec section 5). Fields without a value are null.
 * categories[].actual is the expense actual I(k), income[].actual is the net N(k) (R2, R9).
 */
public record DashboardResponse(
        String month,
        BigDecimal incomeTotal,
        Totals expenses,
        BigDecimal surplus,
        String surplusLabel,
        BigDecimal savingsRate,
        List<GroupLine> groups,
        List<CategoryLine> categories,
        List<IncomeLine> income) {

    public record Totals(BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    /** Sums of the expense lines in one group (R10); target counts missing targets as 0. */
    public record GroupLine(CategoryGroup group, BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    /** group null for an ungrouped category with expenses; it counts towards REMAINDER in the groups. */
    public record CategoryLine(
            long id, String name, CategoryGroup group, BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    public record IncomeLine(long id, String name, BigDecimal actual, BigDecimal share) {
    }
}
