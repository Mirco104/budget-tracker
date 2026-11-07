package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.TransactionData;
import io.github.mirco104.budget.recurring.OccurrenceService;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
import io.github.mirco104.budget.recurring.ReplacementService;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringRepository;
    private final OccurrenceService occurrenceService;
    private final ReplacementService replacementService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final DashboardCalculator calculator = new DashboardCalculator();

    public DashboardService(
            CategoryService categoryService, TransactionRepository transactionRepository,
            RecurringTransactionRepository recurringRepository, OccurrenceService occurrenceService,
            ReplacementService replacementService, CurrentUser currentUser, Clock clock) {
        this.replacementService = replacementService;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
        this.recurringRepository = recurringRepository;
        this.occurrenceService = occurrenceService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse calculate(YearMonth month) {
        YearMonth currentMonth = YearMonth.now(clock);
        Averages averages = averages(month, currentMonth);
        return calculate(month, averages.income(), averages.expenses(), currentMonth);
    }

    /**
     * Income, expenses and actuals of a month without Ē(M) and Ā(M) (R23), for the analysis (R14): it needs no
     * targets, and the averages would cost twelve more months per month.
     */
    @Transactional(readOnly = true)
    public DashboardResponse actuals(YearMonth month) {
        return calculate(month, IncomeAverage.Result.EMPTY, null, YearMonth.now(clock));
    }

    /** Month of the earliest transaction or series start, null without any entry (R14, R23). */
    @Transactional(readOnly = true)
    public YearMonth firstEntryMonth() {
        return Stream.of(
                        transactionRepository.findMinDate(currentUser.get()),
                        recurringRepository.findMinStartDate(currentUser.get()))
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .map(YearMonth::from)
                .orElse(null);
    }

    private DashboardResponse calculate(
            YearMonth month, IncomeAverage.Result average, BigDecimal averageExpenses, YearMonth currentMonth) {
        Map<Long, TargetEntry> targets = categoryService.targetsInEffect(month);
        List<CategoryData> categories = categoryService.findAllOrdered().stream()
                .map(category -> {
                    TargetEntry target = targets.get(category.getId());
                    return new CategoryData(
                            category.getId(),
                            category.getName(),
                            target == null ? null : target.targetAmount());
                })
                .toList();
        return calculator.calculate(categories, entries(month, month), month, average, averageExpenses, currentMonth);
    }

    private record Averages(IncomeAverage.Result income, BigDecimal expenses) {
    }

    // R23: E and A of every month that counts, from one query for the single transactions
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

    // R1: an occurrence of a recurring transaction counts with its full amount, like a single transaction; R27: no
    // transfers
    private List<TransactionData> entries(YearMonth from, YearMonth to) {
        // R28: a transaction that replaces an occurrence counts as recurring
        java.util.Set<Long> replacing = replacementService.replacingIds();
        Stream<TransactionData> singles = transactionRepository
                .findCountedBetween(currentUser.get(), from.atDay(1), to.atEndOfMonth())
                .stream()
                .map(transaction -> new TransactionData(
                        transaction.getCategory().getId(), transaction.getAmount(), transaction.getDate(),
                        replacing.contains(transaction.getId())));
        Stream<TransactionData> occurrences = Stream.iterate(from, m -> !m.isAfter(to), m -> m.plusMonths(1))
                .flatMap(m -> occurrenceService.occurrencesIn(m).stream())
                .map(occurrence -> new TransactionData(
                        occurrence.categoryId(), occurrence.amount(), occurrence.date(), true));
        return Stream.concat(singles, occurrences).toList();
    }
}
