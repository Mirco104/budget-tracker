package io.github.mirco104.budget.suggestion;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.recurring.OccurrenceMatcher;
import io.github.mirco104.budget.recurring.OccurrenceMatcher.Planned;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
import io.github.mirco104.budget.recurring.ReplacementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import io.github.mirco104.budget.suggestion.CategorySuggester.CategoryData;
import io.github.mirco104.budget.suggestion.CategorySuggester.Entry;
import io.github.mirco104.budget.suggestion.CategorySuggester.Suggestion;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Category suggestions from the signed-in user's own bookings (R15, R29). */
@Service
public class BookingSuggestionService {

    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringRepository;
    private final CategoryService categoryService;
    private final ReplacementService replacementService;
    private final CurrentUser currentUser;

    public BookingSuggestionService(
            TransactionRepository transactionRepository, RecurringTransactionRepository recurringRepository,
            CategoryService categoryService, ReplacementService replacementService, CurrentUser currentUser) {
        this.replacementService = replacementService;
        this.transactionRepository = transactionRepository;
        this.recurringRepository = recurringRepository;
        this.categoryService = categoryService;
        this.currentUser = currentUser;
    }

    /** The history of R29 and the categories that may be suggested, loaded once for many suggestions (import). */
    @Transactional(readOnly = true)
    public Suggester suggester() {
        User owner = currentUser.get();
        Stream<Entry> singles = transactionRepository.findByOwnerAndTransferFalse(owner).stream()
                .map(t -> new Entry(t.getCounterparty() != null ? t.getCounterparty() : t.getDescription(),
                        t.getCategory().getId(), t.getDate(), t.getAmount().signum() > 0, t.getBankCategory()));
        Stream<Entry> series = recurringRepository.findByOwnerOrderByStartDateAscIdAsc(owner).stream()
                .map(r -> new Entry(r.getDescription(), r.getCategory().getId(), r.getStartDate(),
                        r.getAmount().signum() > 0, null));
        List<CategoryData> categories = categoryService.findAllOrdered().stream()
                .filter(category -> !category.isArchived())
                .map(category -> new CategoryData(category.getId(), category.getName()))
                .toList();
        return new Suggester(Stream.concat(singles, series).toList(), categories);
    }

    /** R29 for one booking; with amount and date also the occurrence of R28, whose series gives the category. */
    @Transactional(readOnly = true)
    public BookingSuggestionResponse suggest(String text, boolean income, BigDecimal amount, LocalDate date) {
        Optional<Suggestion> suggestion = suggester().suggest(text, income, null);
        Planned match = null;
        if (amount != null && date != null && amount.signum() != 0 && (amount.signum() > 0) == income) {
            OccurrenceMatcher.Booking booking = new OccurrenceMatcher.Booking(0, date, amount, text,
                    suggestion.map(Suggestion::categoryId).orElse(null));
            match = OccurrenceMatcher.match(List.of(booking),
                    replacementService.plannedBetween(date.minusDays(5), date.plusDays(5))).get(0);
        }
        if (match != null) {
            return new BookingSuggestionResponse(match.categoryId(), true, new BookingSuggestionResponse.Occurrence(
                    match.recurringId(), match.date(), match.description(), match.amount().abs(), match.categoryId()));
        }
        return suggestion
                .map(s -> new BookingSuggestionResponse(s.categoryId(), s.certain(), null))
                .orElse(BookingSuggestionResponse.NONE);
    }

    /** The loaded history; suggests for any number of bookings without loading it again. */
    public record Suggester(List<Entry> history, List<CategoryData> categories) {

        public Optional<Suggestion> suggest(String text, boolean income, String bankCategory) {
            return suggest(text, income, bankCategory, List.of());
        }

        /** With the kind hints of the bank profile of an import (decision 46). */
        public Optional<Suggestion> suggest(
                String text, boolean income, String bankCategory, List<CategorySuggester.KindHint> kindHints) {
            return CategorySuggester.suggest(text, income, bankCategory, kindHints, history, categories);
        }
    }
}
