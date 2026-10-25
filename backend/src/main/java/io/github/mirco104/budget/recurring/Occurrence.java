package io.github.mirco104.budget.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One calculated occurrence of a series (R12); amount is signed (R11). */
public record Occurrence(
        long recurringId, BigDecimal amount, String description, LocalDate date, long categoryId,
        String categoryName) {
}
