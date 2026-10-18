package io.github.mirco104.budget.category;

import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Target values are the ones in effect for the requested month (R3); targetValidFrom is the month they were set
 * for, or null without a target entry.
 */
public record CategoryResponse(
        Long id,
        String name,
        CategoryType type,
        CategoryGroup group,
        int sortOrder,
        BigDecimal targetAmount,
        BigDecimal targetPercent,
        YearMonth targetValidFrom,
        boolean system) {

    static CategoryResponse from(Category category, TargetEntry target) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getGroup(),
                category.getSortOrder(),
                target == null ? null : target.targetAmount(),
                target == null ? null : target.targetPercent(),
                target == null ? null : target.validFrom(),
                category.isSystem());
    }
}
