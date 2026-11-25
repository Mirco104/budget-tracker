package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.bankaccount.AccountOverviewService;
import io.github.mirco104.budget.dashboard.DashboardService;
import io.github.mirco104.budget.goal.SavingGoal;
import io.github.mirco104.budget.goal.SavingGoalRepository;
import io.github.mirco104.budget.goal.SavingGoalService;
import io.github.mirco104.budget.plan.PlanService;
import io.github.mirco104.budget.plan.PlannedItem;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Konten, 27f2: the reserve pots of the current month grouped by their savings account, with the sum of their
 * calculated balances against the app balance of the account (P8, P11); pots without an account form one group.
 * Ticket #145 (decision 101): the saving goals on an account count in its cover too; an account with goals but no
 * pots has a group of its own, goals without an account show in no group.
 */
@Service
public class ReserveAccountService {

    private final DashboardService dashboardService;
    private final PlanService planService;
    private final AccountOverviewService accountService;
    private final SavingGoalService goalService;
    private final SavingGoalRepository goalRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ReserveAccountService(
            DashboardService dashboardService, PlanService planService, AccountOverviewService accountService,
            Clock clock, SavingGoalService goalService, SavingGoalRepository goalRepository, CurrentUser currentUser) {
        this.currentUser = currentUser;
        this.goalService = goalService;
        this.goalRepository = goalRepository;
        this.dashboardService = dashboardService;
        this.planService = planService;
        this.accountService = accountService;
        this.clock = clock;
    }

    /**
     * saved and target sum the pots; goalsSaved sums the saved amounts of the goals on the account (each at least 0,
     * ticket #145); accountBalance is the app balance of the account today, missing what it lacks to cover saved and
     * goalsSaved (0 when covered), needed what the account has to hold for both. accountBalance and missing are
     * null for the pots without an account.
     */
    public record ReserveGroup(
            Long accountId, String accountName, BigDecimal saved, BigDecimal target, BigDecimal accountBalance,
            BigDecimal missing, List<ReserveCalculator.Pot> pots, BigDecimal goalsSaved, List<GoalShare> goals,
            BigDecimal needed) {
    }

    /** Ticket #145: a saving goal on the account with what it saved (P10, AP 27e). */
    public record GoalShare(long id, String name, BigDecimal saved, BigDecimal targetAmount) {
    }

    @Transactional(readOnly = true)
    public List<ReserveGroup> groups() {
        YearMonth month = YearMonth.now(clock);
        Map<Long, PlannedItem> itemById = new HashMap<>();
        planService.findAll().forEach(item -> itemById.put(item.getId(), item));
        Map<Long, List<ReserveCalculator.Pot>> byAccount = new LinkedHashMap<>();
        Map<Long, io.github.mirco104.budget.bankaccount.BankAccount> accounts = new HashMap<>();
        List<ReserveCalculator.Pot> withoutAccount = new ArrayList<>();
        for (ReserveCalculator.Pot before : dashboardService.calculate(month).reserves()) {
            ReserveCalculator.Pot pot = afterPayment(before);
            PlannedItem item = itemById.get(pot.itemId());
            if (item == null || item.getReserveAccount() == null) {
                withoutAccount.add(pot);
            } else {
                accounts.put(item.getReserveAccount().getId(), item.getReserveAccount());
                byAccount.computeIfAbsent(item.getReserveAccount().getId(), id -> new ArrayList<>()).add(pot);
            }
        }
        // Ticket #145: the goals on an account, also an account without pots
        Map<Long, List<GoalShare>> goalsByAccount = new LinkedHashMap<>();
        Map<Long, SavingGoalService.GoalResponse> goalById = new HashMap<>();
        goalService.findAll().forEach(goal -> goalById.put(goal.id(), goal));
        for (SavingGoal goal : goalRepository.findByOwnerOrderByNameAsc(currentUser.get())) {
            if (goal.getAccount() == null) {
                continue;
            }
            SavingGoalService.GoalResponse response = goalById.get(goal.getId());
            accounts.put(goal.getAccount().getId(), goal.getAccount());
            byAccount.computeIfAbsent(goal.getAccount().getId(), id -> new ArrayList<>());
            goalsByAccount.computeIfAbsent(goal.getAccount().getId(), id -> new ArrayList<>())
                    .add(new GoalShare(goal.getId(), goal.getName(), response.saved(), goal.getTargetAmount()));
        }
        List<ReserveGroup> groups = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        byAccount.forEach((id, pots) -> {
            BigDecimal saved = sum(pots, true);
            List<GoalShare> goals = goalsByAccount.getOrDefault(id, List.of());
            BigDecimal needed = AccountCover.needed(saved, goals.stream().map(GoalShare::saved).toList());
            BigDecimal balance = accountService.balance(accounts.get(id), today);
            groups.add(new ReserveGroup(id, accounts.get(id).getName(), saved, sum(pots, false), balance,
                    AccountCover.missing(needed, balance), pots, needed.subtract(saved), goals, needed));
        });
        if (!withoutAccount.isEmpty()) {
            groups.add(new ReserveGroup(null, null, sum(withoutAccount, true), sum(withoutAccount, false), null, null,
                    withoutAccount, BigDecimal.ZERO.setScale(2), List.of(), sum(withoutAccount, true)));
        }
        return groups;
    }

    // 27f4 (decision 60): on Konten a pot shows what is left in it after the payments of the month
    private static ReserveCalculator.Pot afterPayment(ReserveCalculator.Pot pot) {
        return new ReserveCalculator.Pot(pot.itemId(), pot.name(), pot.categoryId(), pot.amount(), pot.monthly(),
                pot.balance().subtract(pot.fromPot()), pot.due(), pot.nextDue(), pot.paid(), pot.fromPot(),
                pot.shortfall(), pot.months());
    }

    private static BigDecimal sum(List<ReserveCalculator.Pot> pots, boolean saved) {
        return pots.stream().map(pot -> saved ? pot.balance() : pot.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
