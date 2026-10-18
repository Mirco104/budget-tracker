package io.github.mirco104.budget.yearly;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record YearlyTransactionResponse(
        Long id,
        BigDecimal amountPerYear,
        String description,
        LocalDate dueDate,
        YearMonth validFrom,
        YearMonth validTo,
        Long categoryId,
        String categoryName,
        CategoryType categoryType) {

    static YearlyTransactionResponse from(YearlyTransaction transaction) {
        Category category = transaction.getCategory();
        return new YearlyTransactionResponse(
                transaction.getId(),
                transaction.getAmountPerYear(),
                transaction.getDescription(),
                transaction.getDueDate(),
                transaction.getValidFrom(),
                transaction.getValidTo(),
                category.getId(),
                category.getName(),
                category.getType());
    }
}
