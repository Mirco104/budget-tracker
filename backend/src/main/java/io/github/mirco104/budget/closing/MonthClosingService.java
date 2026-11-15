package io.github.mirco104.budget.closing;

import io.github.mirco104.budget.bankaccount.AccountOverviewService;
import io.github.mirco104.budget.bankaccount.BankAccount;
import io.github.mirco104.budget.bankaccount.BankAccountService;
import io.github.mirco104.budget.common.BadRequestException;
import io.github.mirco104.budget.dashboard.DashboardResponse;
import io.github.mirco104.budget.dashboard.DashboardService;
import io.github.mirco104.budget.goal.SavingGoal;
import io.github.mirco104.budget.goal.SavingGoalService;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** P10 and decision 65: the leftover of a month, closing it with or without a saving goal, and the past-month view. */
@Service
public class MonthClosingService {

    private final ClosedMonthRepository closedMonthRepository;
    private final DashboardService dashboardService;
    private final TransactionRepository transactionRepository;
    private final SavingGoalService savingGoalService;
    private final BankAccountService bankAccountService;
    private final AccountOverviewService accountOverviewService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public MonthClosingService(
            ClosedMonthRepository closedMonthRepository, DashboardService dashboardService,
            TransactionRepository transactionRepository, SavingGoalService savingGoalService,
            BankAccountService bankAccountService, AccountOverviewService accountOverviewService,
            CurrentUser currentUser, Clock clock) {
        this.closedMonthRepository = closedMonthRepository;
        this.dashboardService = dashboardService;
        this.transactionRepository = transactionRepository;
        this.savingGoalService = savingGoalService;
        this.bankAccountService = bankAccountService;
        this.accountOverviewService = accountOverviewService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** The change of an account's app balance (P11) in the month. */
    public record AccountChange(long accountId, String name, BigDecimal change) {
    }

    /**
     * A past month (decision 59): what was left (P10, negative is a shortfall), whether it is closed and to which
     * goal, and per account the change of its balance.
     */
    public record MonthView(
            String month, BigDecimal leftover, boolean closed, Long savingGoalId, String savingGoalName,
            List<AccountChange> accounts) {
    }

    /** P10: income − expenses − saving of the month (actuals, P1). */
    @Transactional(readOnly = true)
    public BigDecimal leftover(YearMonth month) {
        DashboardResponse actuals = dashboardService.actuals(month);
        BigDecimal saving = transactionRepository
                .findSavingBetween(currentUser.get(), month.atDay(1), month.atEndOfMonth()).stream()
                .map(t -> t.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return MonthClosing.leftover(actuals.incomeTotal(), actuals.expenses().actual(), saving);
    }

    @Transactional(readOnly = true)
    public Optional<ClosedMonth> find(YearMonth month) {
        return closedMonthRepository.findByOwnerAndMonth(currentUser.get(), month.atDay(1));
    }

    @Transactional(readOnly = true)
    public MonthView view(YearMonth month) {
        checkPast(month);
        Optional<ClosedMonth> closed = find(month);
        List<AccountChange> accounts = bankAccountService.findAllOrdered().stream()
                .filter(account -> !account.isArchived())
                .map(account -> new AccountChange(account.getId(), account.getName(), change(account, month)))
                .toList();
        return new MonthView(month.toString(), leftover(month), closed.isPresent(),
                closed.map(c -> c.getSavingGoal() == null ? null : c.getSavingGoal().getId()).orElse(null),
                closed.map(c -> c.getSavingGoal() == null ? null : c.getSavingGoal().getName()).orElse(null),
                accounts);
    }

    /** P10: closes a past month with its leftover; with a goal the amount counts to it. A second close replaces. */
    @Transactional
    public MonthView close(YearMonth month, Long savingGoalId) {
        checkPast(month);
        SavingGoal goal = savingGoalService.findUsable(savingGoalId, "savingGoalId");
        BigDecimal amount = leftover(month);
        find(month).ifPresent(closedMonthRepository::delete);
        closedMonthRepository.flush();
        closedMonthRepository.save(new ClosedMonth(currentUser.get(), month.atDay(1), amount, goal));
        return view(month);
    }

    /** P10: "Rückgängig"; the month is open again. */
    @Transactional
    public void undo(YearMonth month) {
        find(month).ifPresent(closedMonthRepository::delete);
    }

    private BigDecimal change(BankAccount account, YearMonth month) {
        return accountOverviewService.balance(account, month.atEndOfMonth())
                .subtract(accountOverviewService.balance(account, month.minusMonths(1).atEndOfMonth()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void checkPast(YearMonth month) {
        if (!month.isBefore(YearMonth.now(clock))) {
            throw new BadRequestException("Nur ein vergangener Monat kann abgeschlossen werden.");
        }
    }
}
