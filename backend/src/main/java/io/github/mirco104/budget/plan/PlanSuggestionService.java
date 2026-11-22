package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.common.InvalidFieldException;
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
 * R31 among the bookings that fulfil no term yet become suggested items when they still run (a booking in the current
 * or the previous month), unless an item with the same name is valid now or the user hid the suggestion
 * ("Ausblenden", one row per series in the dismissed notifications). The row of a hidden series goes once the series
 * had no booking for 12 months.
 */
@Service
public class PlanSuggestionService {

    static final int MONTHS = 12;
    public static final String KEY_PREFIX = "suggestion:";
    private static final String HIDDEN = "hidden";

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

    /** The suggestions; on the way it deletes the rows of hidden series without a booking in the last 12 months. */
    @Transactional
    public List<PlanSuggestion> suggestions() {
        List<Transaction> counted = counted();
        Set<String> booked = counted.stream().map(PlanSuggestionService::keyOf).collect(Collectors.toSet());
        Set<String> hidden = new java.util.HashSet<>();
        for (DismissedNotification row : dismissedRepository.findByOwner(currentUser.get())) {
            if (!row.getKey().startsWith(KEY_PREFIX)) {
                continue;
            }
            if (booked.contains(row.getKey())) {
                hidden.add(row.getKey());
            } else {
                dismissedRepository.delete(row);
            }
        }
        return detect(counted).stream().filter(s -> !hidden.contains(s.key())).toList();
    }

    /** "Ausblenden": the suggestion with this key does not come back while its series has bookings. */
    @Transactional
    public void hide(String key) {
        if (detect(counted()).stream().noneMatch(s -> s.key().equals(key))) {
            throw new InvalidFieldException("key", "Diesen Vorschlag gibt es nicht.");
        }
        if (dismissedRepository.findByOwnerAndKey(currentUser.get(), key).isEmpty()) {
            dismissedRepository.save(new DismissedNotification(currentUser.get(), key, HIDDEN));
        }
    }

    /**
     * R38: budget suggestions for the setup wizard from the bookings of the 6 full months before the current one,
     * without saving and without bookings that fulfil a term; categories with a budget item valid now drop out.
     */
    @Transactional(readOnly = true)
    public List<BudgetSuggester.Suggestion> budgetSuggestions() {
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        LocalDate from = current.minusMonths(BudgetSuggester.MONTHS).atDay(1);
        LocalDate to = current.minusMonths(1).atEndOfMonth();
        Set<Long> fulfilling = termService.fixIds(from, to);
        List<BudgetSuggester.Booking> bookings = transactionRepository.findCountedBetween(currentUser.get(), from, to)
                .stream()
                .filter(t -> !t.isSaving() && !fulfilling.contains(t.getId()))
                .map(t -> {
                    Category plan = t.getCategory() == null ? null : planCategory(t.getCategory());
                    return new BudgetSuggester.Booking(t.getDate(), t.getAmount(), plan == null ? null : plan.getId(),
                            plan == null ? null : plan.getName(), t.getBankCategory());
                })
                .toList();
        Set<Long> budgeted = planService.findAll().stream()
                .filter(item -> PlanCalculator.validIn(item.data(), current)
                        && PlanCalculator.groupOf(item.data()) == PlanCalculator.Group.BUDGET
                        && item.getCategory() != null)
                .map(item -> planCategory(item.getCategory()).getId())
                .collect(Collectors.toSet());
        return BudgetSuggester.suggest(bookings, current, budgeted);
    }

    // R32: a subcategory counts as its parent
    private static Category planCategory(Category category) {
        return category.getParent() != null ? category.getParent() : category;
    }

    // The bookings of the last 12 months that count, with a category and not saving
    private List<Transaction> counted() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = YearMonth.from(today).minusMonths(MONTHS).atDay(1);
        return transactionRepository.findCountedBetween(currentUser.get(), from, today).stream()
                .filter(t -> t.getCategory() != null && !t.isSaving())
                .toList();
    }

    // The series among the bookings that fulfil no term, still run and have no item of the same name valid now
    private List<PlanSuggestion> detect(List<Transaction> counted) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = YearMonth.from(today).minusMonths(MONTHS).atDay(1);
        Set<Long> fulfilling = termService.fixIds(from, today);
        List<Transaction> bookings = counted.stream()
                .filter(t -> !fulfilling.contains(t.getId()))
                .sorted(Comparator.comparing(Transaction::getDate))
                .toList();
        List<SeriesDetector.Row> rows = new ArrayList<>();
        for (int i = 0; i < bookings.size(); i++) {
            Transaction t = bookings.get(i);
            rows.add(new SeriesDetector.Row(i, t.getDate(), t.getAmount(), CategorySuggester.key(textOf(t)),
                    t.getDescription()));
        }
        Set<String> itemNames = planService.findAll().stream()
                .filter(item -> PlanCalculator.validIn(item.data(), YearMonth.from(today)))
                .map(item -> item.getName().trim().toLowerCase(Locale.GERMAN))
                .collect(Collectors.toSet());
        List<PlanSuggestion> result = new ArrayList<>();
        for (SeriesDetector.Suggestion series : SeriesDetector.detect(rows)) {
            List<Transaction> of = series.indices().stream().map(bookings::get).toList();
            Transaction latest = of.getLast();
            // Only a series that still runs: its latest booking lies in the current or the previous month
            if (latest.getDate().isBefore(YearMonth.from(today).minusMonths(1).atDay(1))
                    || itemNames.contains(series.description().trim().toLowerCase(Locale.GERMAN))) {
                continue;
            }
            Category category = mostFrequent(of);
            result.add(new PlanSuggestion(keyOf(latest), series.description(),
                    series.amount().setScale(2, RoundingMode.HALF_UP), category.getId(), category.getPath(),
                    latest.getAccount().getId(), latest.getDate(), of.size(),
                    series.amount().signum() > 0 ? PlanCalculator.Group.INCOME : PlanCalculator.Group.FIXED));
        }
        return result;
    }

    // The key of the series a booking belongs to: R29 key of its text and its sign
    private static String keyOf(Transaction t) {
        return KEY_PREFIX + CategorySuggester.key(textOf(t)) + "|" + t.getAmount().signum();
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
