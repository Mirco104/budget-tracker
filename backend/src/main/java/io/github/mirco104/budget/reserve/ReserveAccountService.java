package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.bankaccount.AccountOverviewService;
import io.github.mirco104.budget.dashboard.DashboardService;
import io.github.mirco104.budget.plan.PlanService;
import io.github.mirco104.budget.plan.PlannedItem;
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
 */
@Service
public class ReserveAccountService {

    private final DashboardService dashboardService;
    private final PlanService planService;
    private final AccountOverviewService accountService;
    private final Clock clock;

    public ReserveAccountService(
            DashboardService dashboardService, PlanService planService, AccountOverviewService accountService,
            Clock clock) {
        this.dashboardService = dashboardService;
        this.planService = planService;
        this.accountService = accountService;
        this.clock = clock;
    }

    /**
     * saved and target sum the pots; accountBalance is the app balance of the account today, missing what it lacks
     * to cover saved (0 when covered). Both null for the pots without an account.
     */
    public record ReserveGroup(
            Long accountId, String accountName, BigDecimal saved, BigDecimal target, BigDecimal accountBalance,
            BigDecimal missing, List<ReserveCalculator.Pot> pots) {
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
        List<ReserveGroup> groups = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        byAccount.forEach((id, pots) -> {
            BigDecimal saved = sum(pots, true);
            BigDecimal balance = accountService.balance(accounts.get(id), today);
            BigDecimal missing = saved.subtract(balance).max(BigDecimal.ZERO);
            groups.add(new ReserveGroup(id, accounts.get(id).getName(), saved, sum(pots, false), balance,
                    missing.setScale(2, RoundingMode.HALF_UP), pots));
        });
        if (!withoutAccount.isEmpty()) {
            groups.add(new ReserveGroup(null, null, sum(withoutAccount, true), sum(withoutAccount, false), null, null,
                    withoutAccount));
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
