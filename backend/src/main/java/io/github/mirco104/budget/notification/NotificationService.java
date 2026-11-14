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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 27h (decision 65): the notifications of the bell. Month closing of the month before the current one (P10), an
 * account that falls below its lower limit (P12), a price change of a planned item (P13) and an overplanned plan of the
 * current month (P4). "Erledigt" stores the key; a key holds what the notification is about, so a changed amount
 * brings it back.
 */
@Service
public class NotificationService {

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

    public NotificationService(
            DismissedNotificationRepository dismissedRepository, MonthClosingService closingService,
            DashboardService dashboardService, BankAccountService bankAccountService,
            AccountOverviewService accountOverviewService, TermService termService, PlanService planService,
            TransactionRepository transactionRepository, CurrentUser currentUser, Clock clock) {
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

    @Transactional(readOnly = true)
    public List<Notification> notifications() {
        YearMonth current = YearMonth.now(clock);
        List<Notification> result = new ArrayList<>();
        monthClosing(current).ifPresent(result::add);
        result.addAll(lowerLimits(current));
        result.addAll(priceChanges(current));
        overplanned(current).ifPresent(result::add);
        Set<String> dismissed = dismissedRepository.findByOwner(currentUser.get()).stream()
                .map(DismissedNotification::getKey)
                .collect(Collectors.toSet());
        // The month closing is answered, not dismissed
        return result.stream()
                .filter(n -> n.kind() == Kind.MONTH_CLOSING || !dismissed.contains(n.key()))
                .toList();
    }

    /** "Erledigt": the notification with this key does not come back. */
    @Transactional
    public void dismiss(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new InvalidFieldException("key", "Diese Benachrichtigung gibt es nicht.");
        }
        if (!dismissedRepository.existsByOwnerAndKey(currentUser.get(), key)) {
            dismissedRepository.save(new DismissedNotification(currentUser.get(), key));
        }
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
    private java.util.Optional<Notification> monthClosing(YearMonth current) {
        YearMonth month = MonthClosing.monthToAsk(current);
        BigDecimal leftover = closingService.leftover(month);
        boolean closed = closingService.find(month).isPresent();
        if (!MonthClosing.asks(leftover, closed, month, dashboardService.firstEntryMonth())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Notification("closing:" + month, Kind.MONTH_CLOSING, month.toString(),
                leftover, null, null, null, null, null, null, null, null, null));
    }

    // P12: the balance today plus the terms of the month on the account that are still expected or missed
    private List<Notification> lowerLimits(YearMonth current) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, List<BigDecimal>> open = new HashMap<>();
        termService.terms(current.atDay(1), current.atEndOfMonth()).stream()
                .filter(e -> e.status() == Fulfillment.Status.EXPECTED || e.status() == Fulfillment.Status.MISSED)
                .forEach(e -> open.computeIfAbsent(e.item().getAccount().getId(), id -> new ArrayList<>())
                        .add(e.item().getAmount()));
        List<Notification> result = new ArrayList<>();
        for (BankAccount account : bankAccountService.findAllOrdered()) {
            BigDecimal limit = account.limit();
            if (account.isArchived() || limit == null) {
                continue;
            }
            BigDecimal balance = accountOverviewService.balance(account, today);
            BigDecimal expected = AccountBalance.expected(balance, open.getOrDefault(account.getId(), List.of()));
            if (AccountBalance.below(expected, limit)) {
                result.add(new Notification("limit:" + account.getId() + ":" + current, Kind.LOWER_LIMIT,
                        current.toString(), null, account.getId(), account.getName(), balance, expected,
                        limit.setScale(2, RoundingMode.HALF_UP), null, null, null, null));
            }
        }
        return result;
    }

    // P13: the last two fulfilled terms of an item valid now have the same amount, other than the item's
    private List<Notification> priceChanges(YearMonth current) {
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
        List<Notification> result = new ArrayList<>();
        for (PlannedItem item : valid) {
            List<BigDecimal> fulfilled = fulfilledBy.getOrDefault(item.getItemKey(), List.of()).stream()
                    .map(amounts::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            BigDecimal newAmount = PriceChange.newAmount(item.getAmount(), fulfilled);
            if (newAmount != null) {
                BigDecimal scaled = newAmount.setScale(2, RoundingMode.HALF_UP);
                result.add(new Notification("price:" + item.getItemKey() + ":" + scaled.toPlainString(),
                        Kind.PRICE_CHANGE, null, null, null, null, null, null, null, item.getId(), item.getName(),
                        item.getAmount().setScale(2, RoundingMode.HALF_UP), scaled));
            }
        }
        return result;
    }

    // P4: the plan of the current month gives out more than the planned income
    private java.util.Optional<Notification> overplanned(YearMonth current) {
        BigDecimal unplanned = planService.calculate(current).unplanned();
        if (unplanned == null || unplanned.signum() >= 0) {
            return java.util.Optional.empty();
        }
        BigDecimal amount = unplanned.negate().setScale(2, RoundingMode.HALF_UP);
        return java.util.Optional.of(new Notification("overplanned:" + current + ":" + amount.toPlainString(),
                Kind.OVERPLANNED, current.toString(), amount, null, null, null, null, null, null, null, null, null));
    }
}
