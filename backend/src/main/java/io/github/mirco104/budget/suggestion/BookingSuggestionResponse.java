package io.github.mirco104.budget.suggestion;

/**
 * R29: the suggested category, null without a suggestion; certain only when the latest entries agree. occurrence is the
 * planned occurrence the booking would replace (R28), null without one.
 */
public record BookingSuggestionResponse(Long categoryId, boolean certain, Occurrence occurrence) {

    /** amount is the amount of the series without sign. */
    public record Occurrence(
            long recurringId, java.time.LocalDate date, String description, java.math.BigDecimal amount, long categoryId) {
    }

    static final BookingSuggestionResponse NONE = new BookingSuggestionResponse(null, false, null);
}
