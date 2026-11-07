package io.github.mirco104.budget.suggestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
     * amount, else it is an expense. With amount and date also the occurrence the booking would replace (R28, US35).
     */
    @GetMapping
    public BookingSuggestionResponse suggest(
            @RequestParam(defaultValue = "") String text,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) Boolean income,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        boolean positive = income != null ? income : amount != null && amount.signum() > 0;
        return suggestionService.suggest(text, positive, amount, date);
    }
}
