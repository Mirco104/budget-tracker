package io.github.mirco104.budget.suggestion;

/** R29: the suggested category, null without a suggestion; certain only when the latest entries agree. */
public record BookingSuggestionResponse(Long categoryId, boolean certain) {

    static final BookingSuggestionResponse NONE = new BookingSuggestionResponse(null, false);
}
