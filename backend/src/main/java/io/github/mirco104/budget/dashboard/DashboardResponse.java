package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryGroup;
import java.math.BigDecimal;
import java.util.List;

/**
 * Calculated dashboard for one month (spec section 5). Fields without a value are null.
 */
public record DashboardResponse(
        String month,
        BigDecimal incomeTotal,
        Totals expenses,
        BigDecimal surplus,
        String surplusLabel,
        BigDecimal savingsRate,
        List<CategoryLine> categories,
        List<IncomeLine> income) {

    public record Totals(BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    public record CategoryLine(
            long id, String name, CategoryGroup group, BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    public record IncomeLine(long id, String name, BigDecimal actual, BigDecimal share) {
    }
}
