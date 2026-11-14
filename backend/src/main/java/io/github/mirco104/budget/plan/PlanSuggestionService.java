package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.importer.SeriesDetector;
import io.github.mirco104.budget.notification.DismissedNotification;
import io.github.mirco104.budget.notification.DismissedNotificationRepository;
import io.github.mirco104.budget.suggestion.CategorySuggester;
import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 27i (decision 65): suggestions on the page Planung from the bookings of the last 12 months. The monthly series of
 * R31 among the bookings that fulfil no term yet become suggested items, unless an item with the same name is valid
 * now or the user hid the suggestion ("Ausblenden", stored like a dismissed notification).
 */
@Service
public class PlanSuggestionService {

    static final int MONTHS = 12;
    static final String KEY_PREFIX = "suggestion:";

    private final TransactionRepository transactionRepository;
    private final TermService termService;
    private final PlanService planService;
    private final DismissedNotificationRepository dismissedRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public PlanSuggestionService(
            TransactionRepository transactionRepository, TermService termService, PlanService planService,
            DismissedNotificationRepository dismissedRepository, CurrentUser currentUser, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.termService = termService;
        this.planService = planService;
        this.dismissedRepository = dismissedRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * A suggested item: key hides it, amount is the median of its bookings with sign, anchorDate the date of its latest
     * booking, months how many months in a row it was found; the category (also a subcategory) is the most frequent of
     * its bookings, the account that of its latest booking.
     */
    public record PlanSuggestion(
            String key, String name, BigDecimal amount, Long categoryId, String categoryName, Long accountId,
            LocalDate anchorDate, int months, PlanCalculator.Group group) {
    }

    @Transactional(readOnly = true)
    public List<PlanSuggestion> suggestions() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = YearMonth.from(today).minusMonths(MONTHS).atDay(1);
        Set<Long> fulfilling = termService.fixIds(from, today);
        List<Transaction> bookings = transactionRepository.findCountedBetween(currentUser.get(), from, today).stream()
                .filter(t -> !fulfilling.contains(t.getId()) && t.getCategory() != null && !t.isSaving())
                .sorted(Comparator.comparing(Transaction::getDate))
                .toList();
        List<SeriesDetector.Row> rows = new ArrayList<>();
        for (int i = 0; i < bookings.size(); i++) {
            Transaction t = bookings.get(i);
            rows.add(new SeriesDetector.Row(i, t.getDate(), t.getAmount(), CategorySuggester.key(textOf(t)),
                    t.getDescription()));
        }
        Set<String> dismissed = dismissedRepository.findByOwner(currentUser.get()).stream()
                .map(DismissedNotification::getKey)
                .collect(Collectors.toSet());
        Set<String> itemNames = planService.findAll().stream()
                .filter(item -> PlanCalculator.validIn(item.data(), YearMonth.from(today)))
                .map(item -> item.getName().trim().toLowerCase(Locale.GERMAN))
                .collect(Collectors.toSet());
        List<PlanSuggestion> result = new ArrayList<>();
        for (SeriesDetector.Suggestion series : SeriesDetector.detect(rows)) {
            List<Transaction> of = series.indices().stream().map(bookings::get).toList();
            Transaction latest = of.getLast();
            String key = KEY_PREFIX + CategorySuggester.key(textOf(latest)) + "|" + series.amount().signum();
            if (dismissed.contains(key) || itemNames.contains(series.description().trim().toLowerCase(Locale.GERMAN))) {
                continue;
            }
            Category category = mostFrequent(of);
            result.add(new PlanSuggestion(key, series.description(), series.amount().setScale(2, RoundingMode.HALF_UP),
                    category.getId(), category.getPath(), latest.getAccount().getId(), latest.getDate(),
                    of.size(), series.amount().signum() > 0 ? PlanCalculator.Group.INCOME : PlanCalculator.Group.FIXED));
        }
        return result;
    }

    // R29: the text of a booking is the counterparty when it is known, else the description
    private static String textOf(Transaction t) {
        return t.getCounterparty() != null && !t.getCounterparty().isBlank() ? t.getCounterparty() : t.getDescription();
    }

    // The most frequent category of the bookings; on a tie the one of the latest booking
    private static Category mostFrequent(List<Transaction> bookings) {
        Map<Long, Integer> counts = new HashMap<>();
        bookings.forEach(t -> counts.merge(t.getCategory().getId(), 1, Integer::sum));
        Category best = bookings.getLast().getCategory();
        for (Transaction t : bookings.reversed()) {
            if (counts.get(t.getCategory().getId()) > counts.get(best.getId())) {
                best = t.getCategory();
            }
        }
        return best;
    }
}
