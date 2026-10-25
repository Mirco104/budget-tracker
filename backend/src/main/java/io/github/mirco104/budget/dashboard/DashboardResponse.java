package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryGroup;
import java.math.BigDecimal;
import java.util.List;

/**
 * Calculated dashboard for one month (spec section 5). Fields without a value are null.
 * difference is the deviation from the target (R5, R6), unplanned is the income without a target (R4).
 * Every ...Share and share is the amount as a share of the income, null without income or amount (R8).
 * categories[].actual is the expense actual I(k), income[].actual is the net N(k) (R2, R9).
 */
public record DashboardResponse(
        String month,
        BigDecimal incomeTotal,
        BigDecimal incomeTotalShare,
        Totals expenses,
        BigDecimal unplanned,
        String unplannedLabel,
        BigDecimal surplus,
        String surplusLabel,
        BigDecimal surplusShare,
        List<GroupLine> groups,
        List<CategoryLine> categories,
        List<IncomeLine> income) {

    public record Totals(
            BigDecimal target, BigDecimal actual, BigDecimal difference,
            BigDecimal targetShare, BigDecimal actualShare, BigDecimal differenceShare) {
    }

    /**
     * Sums of the expense lines in one group (R10); target counts missing targets as 0.
     * Only groups with category lines are present; group null sums the ungrouped categories with expenses.
     */
    public record GroupLine(
            CategoryGroup group, BigDecimal target, BigDecimal actual, BigDecimal difference,
            BigDecimal targetShare, BigDecimal actualShare, BigDecimal differenceShare) {
    }

    /** group null for an ungrouped category with expenses. */
    public record CategoryLine(
            long id, String name, CategoryGroup group, BigDecimal target, BigDecimal actual, BigDecimal difference,
            BigDecimal targetShare, BigDecimal actualShare, BigDecimal differenceShare) {
    }

    public record IncomeLine(long id, String name, BigDecimal actual, BigDecimal share) {
    }
}
