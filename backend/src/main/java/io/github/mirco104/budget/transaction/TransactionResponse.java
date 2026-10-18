package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        BigDecimal amount,
        String description,
        LocalDate date,
        Long categoryId,
        String categoryName,
        CategoryType categoryType) {

    static TransactionResponse from(Transaction transaction) {
        Category category = transaction.getCategory();
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getDate(),
                category.getId(),
                category.getName(),
                category.getType());
    }
}
