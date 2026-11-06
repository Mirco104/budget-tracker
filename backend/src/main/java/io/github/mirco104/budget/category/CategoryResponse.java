package io.github.mirco104.budget.category;

import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import io.github.mirco104.budget.recurring.FixedCosts;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Target values are the ones in effect for the requested month (R3); targetValidFrom is the month they were set
 * for, or null without a target entry. incomeOnly: the category has entries and all of them are positive (R24).
 * fixedCosts is F(k, M) of the requested month, null without a series; fixedCostItems are its series (R25).
 */
public record CategoryResponse(
        Long id,
        String name,
        String icon,
        int sortOrder,
        BigDecimal targetAmount,
        YearMonth targetValidFrom,
        boolean archived,
        boolean incomeOnly,
        BigDecimal fixedCosts,
        List<FixedCostItem> fixedCostItems) {

    /** One series: amount without sign, interval in months, monthly its share per month (R25). */
    public record FixedCostItem(
            long recurringId, String description, BigDecimal amount, int interval, BigDecimal monthly) {

        static FixedCostItem from(FixedCosts.Item item) {
            return new FixedCostItem(
                    item.recurringId(), item.description(), item.amount(), item.intervalMonths(), item.monthly());
        }
    }

    static CategoryResponse from(
            Category category, TargetEntry target, boolean incomeOnly, FixedCosts.Result fixedCosts) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.getSortOrder(),
                target == null ? null : target.targetAmount(),
                target == null ? null : target.validFrom(),
                category.isArchived(),
                incomeOnly,
                fixedCosts == null ? null : fixedCosts.total(),
                fixedCosts == null ? List.of() : fixedCosts.items().stream().map(FixedCostItem::from).toList());
    }
}
