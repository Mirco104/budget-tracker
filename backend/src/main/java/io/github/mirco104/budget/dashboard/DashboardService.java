package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.TransactionData;
import io.github.mirco104.budget.recurring.OccurrenceService;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
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
    private final CurrentUser currentUser;
    private final Clock clock;
    private final DashboardCalculator calculator = new DashboardCalculator();

    public DashboardService(
            CategoryService categoryService, TransactionRepository transactionRepository,
            RecurringTransactionRepository recurringRepository, OccurrenceService occurrenceService,
            CurrentUser currentUser, Clock clock) {
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
        return calculate(month, averageIncome(month, currentMonth), currentMonth);
    }

    /**
     * Income, expenses and actuals of a month without Ē(M) (R23), for the analysis (R14): it needs no targets, and
     * the average would cost twelve more months per month.
     */
    @Transactional(readOnly = true)
    public DashboardResponse actuals(YearMonth month) {
        return calculate(month, IncomeAverage.Result.EMPTY, YearMonth.now(clock));
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

    private DashboardResponse calculate(YearMonth month, IncomeAverage.Result average, YearMonth currentMonth) {
        Map<Long, TargetEntry> targets = categoryService.targetsInEffect(month);
        List<CategoryData> categories = categoryService.findAllOrdered().stream()
                .map(category -> {
                    TargetEntry target = targets.get(category.getId());
                    return new CategoryData(
                            category.getId(),
                            category.getName(),
                            target == null ? null : target.targetAmount(),
                            target == null ? null : target.targetPercent());
                })
                .toList();
        return calculator.calculate(categories, entries(month, month), month, average, currentMonth);
    }

    // R23: E of every month that counts, from one query for the single transactions
    private IncomeAverage.Result averageIncome(YearMonth month, YearMonth currentMonth) {
        YearMonth firstEntryMonth = firstEntryMonth();
        IncomeAverage.Result window = IncomeAverage.window(firstEntryMonth, month, currentMonth);
        if (window.months() == 0) {
            return IncomeAverage.Result.EMPTY;
        }
        List<TransactionData> entries = entries(window.from(), window.to());
        Map<YearMonth, BigDecimal> income = new HashMap<>();
        for (YearMonth m = window.from(); !m.isAfter(window.to()); m = m.plusMonths(1)) {
            income.put(m, DashboardCalculator.incomeOf(entries, m));
        }
        return IncomeAverage.of(income, firstEntryMonth, month, currentMonth);
    }

    // R1: an occurrence of a recurring transaction counts with its full amount, like a single transaction
    private List<TransactionData> entries(YearMonth from, YearMonth to) {
        Stream<TransactionData> singles = transactionRepository
                .findByOwnerAndDateBetweenOrderByDateDescIdDesc(currentUser.get(), from.atDay(1), to.atEndOfMonth())
                .stream()
                .map(transaction -> new TransactionData(
                        transaction.getCategory().getId(), transaction.getAmount(), transaction.getDate(), false));
        Stream<TransactionData> occurrences = Stream.iterate(from, m -> !m.isAfter(to), m -> m.plusMonths(1))
                .flatMap(m -> occurrenceService.occurrencesIn(m).stream())
                .map(occurrence -> new TransactionData(
                        occurrence.categoryId(), occurrence.amount(), occurrence.date(), true));
        return Stream.concat(singles, occurrences).toList();
    }
}
