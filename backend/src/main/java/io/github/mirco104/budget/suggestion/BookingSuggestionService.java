package io.github.mirco104.budget.suggestion;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.plan.PlannedItemRepository;
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
    private final PlannedItemRepository itemRepository;
    private final CategoryService categoryService;
    private final CurrentUser currentUser;

    public BookingSuggestionService(
            TransactionRepository transactionRepository, PlannedItemRepository itemRepository,
            CategoryService categoryService, CurrentUser currentUser) {
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
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
        Stream<Entry> series = itemRepository.findAllOf(owner).stream()
                .filter(i -> i.getCategory() != null)
                .map(i -> new Entry(i.getName(), i.getCategory().getId(), i.getValidFrom().atDay(1),
                        i.getAmount().signum() > 0, null));
        List<CategoryData> categories = categoryService.findAllOrdered().stream()
                .filter(category -> !category.isArchived())
                .map(category -> new CategoryData(category.getId(), category.getName()))
                .toList();
        return new Suggester(Stream.concat(singles, series).toList(), categories);
    }

    /** R29 for one booking. amount and date are accepted for the term match of AP 27c and not used yet. */
    @Transactional(readOnly = true)
    public BookingSuggestionResponse suggest(String text, boolean income, BigDecimal amount, LocalDate date) {
        return suggester().suggest(text, income, null)
                .map(s -> new BookingSuggestionResponse(s.categoryId(), s.certain()))
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
