package io.github.mirco104.budget.suggestion;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/booking-suggestion")
public class BookingSuggestionController {

    private final BookingSuggestionService suggestionService;

    public BookingSuggestionController(BookingSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * R29: the suggested category for the text of a booking (US32). The sign comes from {@code income}, else from the
     * amount, else it is an expense.
     */
    @GetMapping
    public BookingSuggestionResponse suggest(
            @RequestParam(defaultValue = "") String text,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) Boolean income) {
        boolean positive = income != null ? income : amount != null && amount.signum() > 0;
        return suggestionService.suggest(text, positive)
                .map(suggestion -> new BookingSuggestionResponse(suggestion.categoryId(), suggestion.certain()))
                .orElse(BookingSuggestionResponse.NONE);
    }
}
