package io.github.mirco104.budget.recurring;

import io.github.mirco104.budget.category.Category;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** monthlyEquivalent is |amount| / interval in months, rounded to cents (spec section 5). */
public record RecurringTransactionResponse(
        Long id,
        UUID seriesId,
        BigDecimal amount,
        String description,
        Long categoryId,
        String categoryName,
        RecurringInterval interval,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyEquivalent) {

    static RecurringTransactionResponse from(RecurringTransaction recurring) {
        Category category = recurring.getCategory();
        return new RecurringTransactionResponse(
                recurring.getId(),
                recurring.getSeriesId(),
                recurring.getAmount(),
                recurring.getDescription(),
                category.getId(),
                category.getName(),
                recurring.getInterval(),
                recurring.getStartDate(),
                recurring.getEndDate(),
                RecurrenceSchedule.monthlyEquivalent(recurring.getAmount(), recurring.getInterval()));
    }
}
