package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryGroup;
import io.github.mirco104.budget.category.CategoryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class DashboardCalculator {

    public record CategoryData(
            long id, String name, CategoryType type, CategoryGroup group,
            BigDecimal targetAmount, BigDecimal targetPercent, boolean system) {
    }

    public record TransactionData(long categoryId, BigDecimal amount, LocalDate date) {
    }

    public record YearlyTransactionData(long categoryId, BigDecimal amountPerYear) {
    }

    public DashboardResponse calculate(
            List<CategoryData> categories, List<TransactionData> transactions,
            List<YearlyTransactionData> yearlyTransactions, YearMonth month) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
