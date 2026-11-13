package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.plan.TermService;
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
    private final CurrentUser currentUser;
    private final Clock clock;
    private final DashboardCalculator calculator = new DashboardCalculator();

    public DashboardService(
            CategoryService categoryService, TransactionRepository transactionRepository, PlanService planService,
            TermService termService, CurrentUser currentUser, Clock clock) {
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
        return calculate(month, averages.income(), averages.expenses(), currentMonth);
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
        Map<Long, BigDecimal> plan = planService.calculate(month).planByCategory();
        List<CategoryData> categories = categoryService.findAllOrdered().stream()
                .map(category -> new CategoryData(category.getId(), category.getName(), plan.get(category.getId())))
                .toList();
        return calculator.calculate(categories, entries(month, month), month, average, averageExpenses, currentMonth);
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
