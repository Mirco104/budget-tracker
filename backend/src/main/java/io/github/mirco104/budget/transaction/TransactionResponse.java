package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.recurring.Occurrence;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An entry of the transaction list: a single transaction (kind SINGLE, id) or a calculated occurrence of a
 * recurring transaction (kind RECURRING, recurringId and occurrenceDate). Fields of the other kind are null.
 */
public record TransactionResponse(
        Kind kind,
        Long id,
        Long recurringId,
        LocalDate occurrenceDate,
        BigDecimal amount,
        String description,
        LocalDate date,
        Long categoryId,
        String categoryName) {

    public enum Kind { SINGLE, RECURRING }

    static TransactionResponse from(Transaction transaction) {
        Category category = transaction.getCategory();
        return new TransactionResponse(
                Kind.SINGLE,
                transaction.getId(),
                null,
                null,
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getDate(),
                category.getId(),
                category.getName());
    }

    static TransactionResponse from(Occurrence occurrence) {
        return new TransactionResponse(
                Kind.RECURRING,
                null,
                occurrence.recurringId(),
                occurrence.date(),
                occurrence.amount(),
                occurrence.description(),
                occurrence.date(),
                occurrence.categoryId(),
                occurrence.categoryName());
    }
}
