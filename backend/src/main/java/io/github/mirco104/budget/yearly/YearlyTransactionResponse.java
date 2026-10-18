package io.github.mirco104.budget.yearly;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryType;
import io.github.mirco104.budget.dashboard.DashboardCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** amountPerMonth is the share counted per month (R1); the dashboard sums per category before rounding. */
public record YearlyTransactionResponse(
        Long id,
        BigDecimal amountPerYear,
        BigDecimal amountPerMonth,
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
                DashboardCalculator.monthlyShare(transaction.getAmountPerYear()),
                transaction.getDescription(),
                transaction.getDueDate(),
                transaction.getValidFrom(),
                transaction.getValidTo(),
                category.getId(),
                category.getName(),
                category.getType());
    }
}
