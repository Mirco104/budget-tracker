package io.github.mirco104.budget.category;

import java.math.BigDecimal;

public record CategoryResponse(
        Long id,
        String name,
        CategoryType type,
        CategoryGroup group,
        int sortOrder,
        BigDecimal targetAmount,
        BigDecimal targetPercent,
        boolean system) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getGroup(),
                category.getSortOrder(),
                category.getTargetAmount(),
                category.getTargetPercent(),
                category.isSystem());
    }
}
