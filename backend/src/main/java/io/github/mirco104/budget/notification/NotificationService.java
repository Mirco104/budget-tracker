package io.github.mirco104.budget.notification;

import io.github.mirco104.budget.bankaccount.AccountBalance;
import io.github.mirco104.budget.bankaccount.AccountOverviewService;
import io.github.mirco104.budget.bankaccount.BankAccount;
import io.github.mirco104.budget.bankaccount.BankAccountService;
import io.github.mirco104.budget.closing.MonthClosing;
import io.github.mirco104.budget.closing.MonthClosingService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.dashboard.DashboardService;
import io.github.mirco104.budget.plan.Fulfillment;
import io.github.mirco104.budget.plan.PlanCalculator;
import io.github.mirco104.budget.plan.PlanService;
import io.github.mirco104.budget.plan.PlanSuggestionService;
import io.github.mirco104.budget.plan.PlannedItem;
import io.github.mirco104.budget.plan.PlannedItemRequest;
import io.github.mirco104.budget.plan.PriceChange;
import io.github.mirco104.budget.plan.TermService;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 27h (decision 65): the notifications of the bell. Month closing of the month before the current one (P10), an
 * account that falls below its lower limit (P12), a price change of a planned item (P13) and an overplanned plan of the
 * current month (P4). The key of a notification names its subject (limit:<account>, price:<item_key>,
 * overplanned); "Erledigt" stores the subject with the value the notification had (month, amount), one row per
 * subject. A changed value brings the notification back.
 */
@Service
public class NotificationService {

    /** The subject keys of an account and of an item; their rows go when the account or the item is deleted. */
    public static final String LIMIT_PREFIX = "limit:";
    public static final String PRICE_PREFIX = "price:";

    /** How far back the price change looks for fulfilled terms. */
    private static final int PRICE_MONTHS = 13;

    private final DismissedNotificationRepository dismissedRepository;
    private final MonthClosingService closingService;
    private final DashboardService dashboardService;
    private final BankAccountService bankAccountService;
    private final AccountOverviewService accountOverviewService;
    private final TermService termService;
    private final PlanService planService;
    private final TransactionRepository transactionRepository;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final PlanSuggestionService suggestionService;

    public NotificationService(
            DismissedNotificationRepository dismissedRepository, MonthClosingService closingService,
            DashboardService dashboardService, BankAccountService bankAccountService,
            AccountOverviewService accountOverviewService, TermService termService, PlanService planService,
            TransactionRepository transactionRepository, CurrentUser currentUser, Clock clock,
            PlanSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
        this.dismissedRepository = dismissedRepository;
        this.closingService = closingService;
        this.dashboardService = dashboardService;
        this.bankAccountService = bankAccountService;
        this.accountOverviewService = accountOverviewService;
        this.termService = termService;
        this.planService = planService;
        this.transactionRepository = transactionRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public enum Kind {
        MONTH_CLOSING, LOWER_LIMIT, PRICE_CHANGE, OVERPLANNED
    }

    /**
     * One notification; only the fields of its kind are set. MONTH_CLOSING: month, amount (the leftover).
     * LOWER_LIMIT: accountId, accountName, balance (today), expected, limit. PRICE_CHANGE: itemId, itemName,
     * oldAmount, newAmount (with sign). OVERPLANNED: month, amount (how much is overplanned).
     */
    public record Notification(
            String key, Kind kind, String month, BigDecimal amount, Long accountId, String accountName,
            BigDecimal balance, BigDecimal expected, BigDecimal limit, Long itemId, String itemName,
            BigDecimal oldAmount, BigDecimal newAmount) {
    }

    /** A notification with the value that "Erledigt" stores for its subject. */
    private record Entry(Notification notification, String value) {
    }

    @Transactional(readOnly = true)
    public List<Notification> notifications() {
        Map<String, String> dismissed = dismissedRepository.findByOwner(currentUser.get()).stream()
                .collect(Collectors.toMap(DismissedNotification::getKey, DismissedNotification::getValue));
        // The month closing is answered, not dismissed
        return entries().stream()
                .filter(e -> e.notification().kind() == Kind.MONTH_CLOSING
                        || !e.value().equals(dismissed.get(e.notification().key())))
                .map(Entry::notification)
                .toList();
    }

    /** "Erledigt": the notification does not come back while its value stays the same. */
    @Transactional
    public void dismiss(String key) {
        // 27i: "Ausblenden" of a suggestion on Planung
        if (key != null && key.startsWith(PlanSuggestionService.KEY_PREFIX)) {
            suggestionService.hide(key);
            return;
        }
        Entry entry = entries().stream()
                .filter(e -> e.notification().kind() != Kind.MONTH_CLOSING && e.notification().key().equals(key))
                .findFirst()
                .orElseThrow(() -> new InvalidFieldException("key", "Diese Benachrichtigung gibt es nicht."));
        remember(key, entry.value());
    }

    /** Stores the value for the subject, one row per subject. */
    private void remember(String key, String value) {
        dismissedRepository.findByOwnerAndKey(currentUser.get(), key).ifPresentOrElse(
                row -> row.setValue(value),
                () -> dismissedRepository.save(new DismissedNotification(currentUser.get(), key, value)));
    }

    private List<Entry> entries() {
        YearMonth current = YearMonth.now(clock);
        List<Entry> result = new ArrayList<>();
        monthClosing(current).ifPresent(result::add);
        result.addAll(lowerLimits(current));
        result.addAll(priceChanges(current));
        overplanned(current).ifPresent(result::add);
        return result;
    }

    /** P13 "Übernehmen": the item gets the new amount from the current month on. */
    @Transactional
    public void acceptPriceChange(long itemId, BigDecimal amount) {
        PlannedItem item = planService.findAll().stream()
                .filter(candidate -> candidate.getId() == itemId)
                .findFirst()
                .orElseThrow(() -> new InvalidFieldException("itemId", "Diesen Posten gibt es nicht."));
        planService.update(itemId, new PlannedItemRequest(item.getName(), amount, item.isSaving(),
                item.getCategory() == null ? null : item.getCategory().getId(), item.getAccount().getId(),
                item.getRhythmUnit(), item.getRhythmCount(), item.getAnchorDate(), item.getEntryMode(),
                item.isWorkdaysOnly(), YearMonth.now(clock),
                item.getSavingGoal() == null ? null : item.getSavingGoal().getId()));
    }

    // P10, decision 65: only the month before the current one, while it is open and something is left
    private java.util.Optional<Entry> monthClosing(YearMonth current) {
        YearMonth month = MonthClosing.monthToAsk(current);
        BigDecimal leftover = closingService.leftover(month);
        boolean closed = closingService.find(month).isPresent();
        if (!MonthClosing.asks(leftover, closed, month, dashboardService.firstEntryMonth())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Entry(new Notification("closing:" + month, Kind.MONTH_CLOSING,
                month.toString(), leftover, null, null, null, null, null, null, null, null, null), month.toString()));
    }

    // P12: the balance today plus the terms of the month on the account that are still expected or missed; after
    // "Erledigt" it comes back in the next month
    private List<Entry> lowerLimits(YearMonth current) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, List<BigDecimal>> open = new HashMap<>();
        termService.terms(current.atDay(1), current.atEndOfMonth()).stream()
                .filter(e -> e.status() == Fulfillment.Status.EXPECTED || e.status() == Fulfillment.Status.MISSED)
                .forEach(e -> open.computeIfAbsent(e.item().getAccount().getId(), id -> new ArrayList<>())
                        .add(e.item().getAmount()));
        List<Entry> result = new ArrayList<>();
        for (BankAccount account : bankAccountService.findAllOrdered()) {
            BigDecimal limit = account.limit();
            if (account.isArchived() || limit == null) {
                continue;
            }
            BigDecimal balance = accountOverviewService.balance(account, today);
            BigDecimal expected = AccountBalance.expected(balance, open.getOrDefault(account.getId(), List.of()));
            if (AccountBalance.below(expected, limit)) {
                result.add(new Entry(new Notification(LIMIT_PREFIX + account.getId(), Kind.LOWER_LIMIT,
                        current.toString(), null, account.getId(), account.getName(), balance, expected,
                        limit.setScale(2, RoundingMode.HALF_UP), null, null, null, null), current.toString()));
            }
        }
        return result;
    }

    // P13: the last two fulfilled terms of an item valid now have the same amount, other than the item's
    private List<Entry> priceChanges(YearMonth current) {
        List<PlannedItem> valid = planService.findAll().stream()
                .filter(item -> item.getRhythmUnit() != null && PlanCalculator.validIn(item.data(), current))
                .toList();
        if (valid.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Long>> fulfilledBy = new LinkedHashMap<>();
        termService.terms(current.minusMonths(PRICE_MONTHS).atDay(1), LocalDate.now(clock)).stream()
                .filter(e -> e.status() == Fulfillment.Status.FULFILLED && e.transactionId() != null)
                .forEach(e -> fulfilledBy.computeIfAbsent(e.item().getItemKey(), key -> new ArrayList<>())
                        .add(e.transactionId()));
        Map<Long, BigDecimal> amounts = new HashMap<>();
        transactionRepository.findAllById(fulfilledBy.values().stream().flatMap(List::stream).toList())
                .forEach(t -> amounts.put(t.getId(), t.getAmount()));
        List<Entry> result = new ArrayList<>();
        for (PlannedItem item : valid) {
            List<BigDecimal> fulfilled = fulfilledBy.getOrDefault(item.getItemKey(), List.of()).stream()
                    .map(amounts::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            BigDecimal newAmount = PriceChange.newAmount(item.getAmount(), fulfilled);
            if (newAmount != null) {
                BigDecimal scaled = newAmount.setScale(2, RoundingMode.HALF_UP);
                result.add(new Entry(new Notification(PRICE_PREFIX + item.getItemKey(), Kind.PRICE_CHANGE, null,
                        null, null, null, null, null, null, item.getId(), item.getName(),
                        item.getAmount().setScale(2, RoundingMode.HALF_UP), scaled), scaled.toPlainString()));
            }
        }
        return result;
    }

    // P4: the plan of the current month gives out more than the planned income
    private java.util.Optional<Entry> overplanned(YearMonth current) {
        BigDecimal unplanned = planService.calculate(current).unplanned();
        if (unplanned == null || unplanned.signum() >= 0) {
            return java.util.Optional.empty();
        }
        BigDecimal amount = unplanned.negate().setScale(2, RoundingMode.HALF_UP);
        return java.util.Optional.of(new Entry(new Notification("overplanned", Kind.OVERPLANNED, current.toString(),
                amount, null, null, null, null, null, null, null, null, null), current + ":" + amount.toPlainString()));
    }
}
