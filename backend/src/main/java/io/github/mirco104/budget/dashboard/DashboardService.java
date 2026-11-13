package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.plan.TermService;
import io.github.mirco104.budget.reserve.ReserveCalculator;
import java.util.ArrayList;
import io.github.mirco104.budget.plan.Fulfillment;
import io.github.mirco104.budget.plan.PlanCalculator;
import io.github.mirco104.budget.plan.PlanService;
import io.github.mirco104.budget.dashboard.DashboardCalculator.TransactionData;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final PlanService planService;
    private final TermService termService;
    private final io.github.mirco104.budget.bankaccount.AccountOverviewService accountOverviewService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final DashboardCalculator calculator = new DashboardCalculator();

    public DashboardService(
            CategoryService categoryService, TransactionRepository transactionRepository, PlanService planService,
            TermService termService, CurrentUser currentUser, Clock clock,
            io.github.mirco104.budget.bankaccount.AccountOverviewService accountOverviewService) {
        this.accountOverviewService = accountOverviewService;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
        this.planService = planService;
        this.termService = termService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse calculate(YearMonth month) {
        YearMonth currentMonth = YearMonth.now(clock);
        Averages averages = averages(month, currentMonth);
        DashboardResponse real = calculate(month, averages.income(), averages.expenses(), currentMonth);
        return withReserves(month, real, averages, currentMonth).withCards(savingCard(month), expectedCard(month));
    }

    /**
     * P8: the budget lines count the monthly share of every rare item of the category instead of the payments its pot
     * pays: a payment fulfilling a term of the item or chosen explicitly ("aus Rücklage bezahlen"); what the pot lacks
     * counts into the budget. A payment from a saving goal does not count in the budget at all.
     */
    private DashboardResponse withReserves(
            YearMonth month, DashboardResponse real, Averages averages, YearMonth currentMonth) {
        List<io.github.mirco104.budget.transaction.Transaction> bookings = transactionRepository
                .findCountedBetween(currentUser.get(), month.atDay(1), month.atEndOfMonth());
        List<io.github.mirco104.budget.plan.PlannedItem> items = planService.findAll();
        Map<java.util.UUID, YearMonth> firstMonth = new HashMap<>();
        items.forEach(item -> firstMonth.merge(item.getItemKey(), item.getValidFrom(),
                (a, b) -> a.isBefore(b) ? a : b));
        List<io.github.mirco104.budget.plan.PlannedItem> rare = items.stream()
                .filter(item -> item.getRhythmUnit() == io.github.mirco104.budget.plan.RhythmUnit.MONTH
                        && item.getRhythmCount() > 1 && !item.isSaving() && item.getAmount().signum() < 0
                        && PlanCalculator.validIn(item.data(), month))
                .toList();
        java.util.Set<java.util.UUID> rareKeys = new java.util.HashSet<>();
        rare.forEach(item -> rareKeys.add(item.getItemKey()));

        // Which booking each pot pays: the fulfilling one of a term of the item, or one chosen explicitly
        Map<Long, java.util.UUID> potOf = new HashMap<>();
        termService.terms(month.atDay(1), month.atEndOfMonth()).stream()
                .filter(e -> e.transactionId() != null && rareKeys.contains(e.item().getItemKey()))
                .forEach(e -> potOf.put(e.transactionId(), e.item().getItemKey()));
        bookings.stream()
                .filter(t -> t.getReserveItemKey() != null && rareKeys.contains(t.getReserveItemKey()))
                .forEach(t -> potOf.put(t.getId(), t.getReserveItemKey()));
        Map<java.util.UUID, BigDecimal> paid = new HashMap<>();
        java.util.Set<Long> excluded = new java.util.HashSet<>();
        for (io.github.mirco104.budget.transaction.Transaction t : bookings) {
            java.util.UUID key = potOf.get(t.getId());
            if (key != null && t.getAmount().signum() < 0) {
                paid.merge(key, t.getAmount().negate(), BigDecimal::add);
                excluded.add(t.getId());
            } else if (t.getSavingGoal() != null) {
                excluded.add(t.getId());
            }
        }

        List<ReserveCalculator.Pot> pots = new ArrayList<>();
        Set<Long> fix = bookings.isEmpty() ? Set.of() : termService.fixIds(month.atDay(1), month.atEndOfMonth());
        List<TransactionData> entries = new ArrayList<>();
        for (io.github.mirco104.budget.transaction.Transaction t : bookings) {
            if (!excluded.contains(t.getId())) {
                entries.add(new TransactionData(t.getCategory().getId(), t.getAmount(), t.getDate(),
                        fix.contains(t.getId())));
            }
        }
        for (io.github.mirco104.budget.plan.PlannedItem item : rare) {
            ReserveCalculator.Pot pot = ReserveCalculator.pot(
                    new ReserveCalculator.PotItem(item.getId(), item.getName(), item.getCategory().getId(),
                            item.getAmount().abs(), item.getRhythmCount(), item.schedule(),
                            firstMonth.get(item.getItemKey())),
                    month, paid.getOrDefault(item.getItemKey(), BigDecimal.ZERO),
                    item.getReserveAccount() == null ? null
                            : accountOverviewService.balance(item.getReserveAccount(), month.atDay(1).minusDays(1)));
            pots.add(pot);
            entries.add(new TransactionData(pot.categoryId(), pot.monthly().negate(), month.atDay(1), true));
            if (pot.shortfall().signum() > 0) {
                entries.add(new TransactionData(pot.categoryId(), pot.shortfall().negate(), month.atDay(1), true));
            }
        }
        if (pots.isEmpty() && excluded.isEmpty()) {
            return real;
        }
        DashboardResponse budget = calculator.calculate(
                categoriesOf(month), entries, month, averages.income(), averages.expenses(), currentMonth);
        return real.withBudget(budget.categories(), budget.expenses(), pots);
    }

    // P9: the saving items valid in the month with their monthly share
    private DashboardResponse.SavingCard savingCard(YearMonth month) {
        PlanCalculator.Result plan = planService.calculate(month);
        List<DashboardResponse.SavingLine> items = planService.findAll().stream()
                .filter(item -> item.isSaving() && PlanCalculator.validIn(item.data(), month))
                .map(item -> new DashboardResponse.SavingLine(item.getName(),
                        PlanCalculator.monthlyShare(item.getAmount(), item.getRhythmUnit(), item.getRhythmCount())))
                .toList();
        // P1: what was saved in the month, the bookings of the kind Sparen from everyday accounts
        BigDecimal actual = transactionRepository.findSavingBetween(currentUser.get(), month.atDay(1), month.atEndOfMonth())
                .stream().map(t -> t.getAmount().negate()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardResponse.SavingCard(plan.saving(), plan.savingRate(), items,
                actual.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    // P5: the terms of the month that are still expected or missed
    private DashboardResponse.ExpectedCard expectedCard(YearMonth month) {
        List<TermService.Evaluated> open = termService.terms(month.atDay(1), month.atEndOfMonth()).stream()
                .filter(e -> e.status() == Fulfillment.Status.EXPECTED || e.status() == Fulfillment.Status.MISSED)
                .toList();
        BigDecimal total = open.stream().map(e -> e.item().getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardResponse.ExpectedCard(open.size(), total.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /** Ē(M) and Emin(M) (R23), for orientation on the planning page (P4). */
    @Transactional(readOnly = true)
    public IncomeAverage.Result incomeAverage(YearMonth month) {
        return averages(month, YearMonth.now(clock)).income();
    }

    /**
     * Income, expenses and actuals of a month without Ē(M) and Ā(M) (R23), for the analysis (R14): the averages
     * would cost twelve more months per month.
     */
    @Transactional(readOnly = true)
    public DashboardResponse actuals(YearMonth month) {
        return calculate(month, IncomeAverage.Result.EMPTY, null, YearMonth.now(clock));
    }

    /** Month of the earliest booking, null without any (R14, R23); planned items are no actuals (P1). */
    @Transactional(readOnly = true)
    public YearMonth firstEntryMonth() {
        LocalDate first = transactionRepository.findMinDate(currentUser.get());
        return first == null ? null : YearMonth.from(first);
    }

    // P3: the plan of a category is the target of the dashboard line (R5, R6)
    private DashboardResponse calculate(
            YearMonth month, IncomeAverage.Result average, BigDecimal averageExpenses, YearMonth currentMonth) {
        return calculator.calculate(
                categoriesOf(month), entries(month, month), month, average, averageExpenses, currentMonth);
    }

    private List<CategoryData> categoriesOf(YearMonth month) {
        Map<Long, BigDecimal> plan = planService.calculate(month).planByCategory();
        return categoryService.findAllOrdered().stream()
                .map(category -> new CategoryData(category.getId(), category.getName(), plan.get(category.getId())))
                .toList();
    }

    private record Averages(IncomeAverage.Result income, BigDecimal expenses) {
    }

    // R23: E and A of every month that counts, from one query
    private Averages averages(YearMonth month, YearMonth currentMonth) {
        YearMonth firstEntryMonth = firstEntryMonth();
        IncomeAverage.Result window = IncomeAverage.window(firstEntryMonth, month, currentMonth);
        if (window.months() == 0) {
            return new Averages(IncomeAverage.Result.EMPTY, null);
        }
        List<TransactionData> entries = entries(window.from(), window.to());
        Map<YearMonth, BigDecimal> income = new HashMap<>();
        Map<YearMonth, BigDecimal> expenses = new HashMap<>();
        for (YearMonth m = window.from(); !m.isAfter(window.to()); m = m.plusMonths(1)) {
            income.put(m, DashboardCalculator.incomeOf(entries, m));
            expenses.put(m, DashboardCalculator.expensesOf(entries, m));
        }
        return new Averages(
                IncomeAverage.of(income, firstEntryMonth, month, currentMonth),
                IncomeAverage.of(expenses, firstEntryMonth, month, currentMonth).average());
    }

    // P1: only real bookings count; R27: no transfers. A booking that fulfils a term is fix (P5); the flag
    // "recurring" of the calculator carries it
    private List<TransactionData> entries(YearMonth from, YearMonth to) {
        List<io.github.mirco104.budget.transaction.Transaction> bookings = transactionRepository
                .findCountedBetween(currentUser.get(), from.atDay(1), to.atEndOfMonth());
        Set<Long> fix = bookings.isEmpty() ? Set.of() : termService.fixIds(from.atDay(1), to.atEndOfMonth());
        return bookings.stream()
                .map(transaction -> new TransactionData(
                        transaction.getCategory().getId(), transaction.getAmount(), transaction.getDate(),
                        fix.contains(transaction.getId())))
                .toList();
    }
}
