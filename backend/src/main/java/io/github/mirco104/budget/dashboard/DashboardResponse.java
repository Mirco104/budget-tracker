package io.github.mirco104.budget.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calculated dashboard for one month (spec section 5). Fields without a value are null.
 * difference is the deviation from the target (R5, R6), unplanned is the income without a target (R4).
 * Every ...Share and share is the amount as a share of the income, null without income or amount (R8).
 * categories[].actual is the expense actual I(k), income[].actual is the net N(k) (R2, R9).
 * expenses.recurring is W, expenses.oneOff is A - W (R10).
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
        List<CategoryLine> categories,
        List<IncomeLine> income) {

    public record Totals(
            BigDecimal target, BigDecimal actual, BigDecimal difference,
            BigDecimal targetShare, BigDecimal actualShare, BigDecimal differenceShare,
            BigDecimal recurring, BigDecimal oneOff, BigDecimal recurringShare, BigDecimal oneOffShare) {
    }

    public record CategoryLine(
            long id, String name, BigDecimal target, BigDecimal actual, BigDecimal difference,
            BigDecimal targetShare, BigDecimal actualShare, BigDecimal differenceShare) {
    }

    public record IncomeLine(long id, String name, BigDecimal actual, BigDecimal share) {
    }
}
