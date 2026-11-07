package io.github.mirco104.budget.suggestion;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
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
    private final CurrentUser currentUser;

    public BookingSuggestionService(
            TransactionRepository transactionRepository, RecurringTransactionRepository recurringRepository,
            CategoryService categoryService, CurrentUser currentUser) {
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

    /** R29 for one booking. */
    @Transactional(readOnly = true)
    public Optional<Suggestion> suggest(String text, boolean income) {
        return suggester().suggest(text, income, null);
    }

    /** The loaded history; suggests for any number of bookings without loading it again. */
    public record Suggester(List<Entry> history, List<CategoryData> categories) {

        public Optional<Suggestion> suggest(String text, boolean income, String bankCategory) {
            return CategorySuggester.suggest(text, income, bankCategory, history, categories);
        }
    }
}
