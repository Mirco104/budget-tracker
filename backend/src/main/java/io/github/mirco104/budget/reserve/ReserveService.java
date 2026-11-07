package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.dashboard.DashboardResponse;
import io.github.mirco104.budget.dashboard.DashboardService;
import io.github.mirco104.budget.recurring.OccurrenceService;
import io.github.mirco104.budget.recurring.RecurringTransaction;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
import io.github.mirco104.budget.reserve.ReserveCalculator.CategoryData;
import io.github.mirco104.budget.reserve.ReserveCalculator.PastMonth;
import io.github.mirco104.budget.reserve.ReserveCalculator.SeriesData;
import io.github.mirco104.budget.reserve.ReserveCalculator.SingleData;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Look back and outlook of the Auswertung (R14, decision 29). */
@Service
public class ReserveService {

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final OccurrenceService occurrenceService;
    private final DashboardService dashboardService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ReserveCalculator calculator = new ReserveCalculator();

    public ReserveService(
            RecurringTransactionRepository recurringRepository, TransactionRepository transactionRepository,
            CategoryRepository categoryRepository, OccurrenceService occurrenceService,
            DashboardService dashboardService, CurrentUser currentUser, Clock clock) {
        this.recurringRepository = recurringRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.occurrenceService = occurrenceService;
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Look back from {@code from} to {@code to}; both null means the 12 months before the current month H.
     * V must not be after B, B must be before H, and the period has at most 120 months (R14), otherwise 400.
     */
    @Transactional(readOnly = true)
    public HistoryResponse history(YearMonth from, YearMonth to) {
        YearMonth current = YearMonth.now(clock);
        YearMonth last = to == null ? current.minusMonths(1) : to;
        YearMonth first = from == null ? current.minusMonths(12) : from;
        if (first.isAfter(last)) {
            throw new InvalidFieldException("to", "„Bis“ darf nicht vor „Von“ liegen.");
        }
        if (!last.isBefore(current)) {
            throw new InvalidFieldException("to", "„Bis“ muss vor dem aktuellen Monat liegen.");
        }
        if (ChronoUnit.MONTHS.between(first, last) + 1 > ReserveCalculator.MAX_PERIOD) {
            throw new InvalidFieldException("from",
                    "Der Zeitraum darf höchstens " + ReserveCalculator.MAX_PERIOD + " Monate lang sein.");
        }
        // Income E and expenses A per month (R2) come from the dashboard, so the rules stay in one place
        YearMonth firstEntryMonth = dashboardService.firstEntryMonth();
        Map<Long, String> icons = categoryRepository.findAllByOwner(currentUser.get())
                .stream()
                .collect(Collectors.toMap(Category::getId, Category::getIcon));
        List<PastMonth> pastMonths = firstEntryMonth == null
                ? List.of()
                : Stream.iterate(first, month -> !month.isAfter(last), month -> month.plusMonths(1))
                        .filter(month -> !month.isBefore(firstEntryMonth))
                        .map(month -> pastMonth(month, icons::get))
                        .toList();
        return calculator.history(pastMonths, firstEntryMonth, first, last);
    }

    /** Reserve pot and forecast from the month on; without a month from the current month H (R14). */
    @Transactional(readOnly = true)
    public ReserveResponse outlook(YearMonth month) {
        YearMonth start = month == null ? YearMonth.now(clock) : month;
        // Forecast period M..M+11: every series active somewhere in it and every single transaction in it
        LocalDate forecastStart = start.atDay(1);
        LocalDate forecastEnd = start.plusMonths(ReserveCalculator.FORECAST_MONTHS - 1).atEndOfMonth();
        List<RecurringTransaction> series =
                recurringRepository.findActiveBetween(currentUser.get(), forecastStart, forecastEnd);
        Map<Long, Set<LocalDate>> exceptions = occurrenceService.exceptionsBySeries(series);
        List<SeriesData> seriesData = series.stream()
                .map(recurring -> new SeriesData(
                        recurring.getId(),
                        recurring.getDescription(),
                        recurring.getCategory().getName(),
                        recurring.getAmount(),
                        recurring.getInterval(),
                        recurring.getStartDate(),
                        recurring.getEndDate(),
                        exceptions.getOrDefault(recurring.getId(), Set.of())))
                .toList();
        List<SingleData> singles = transactionRepository
                .findCountedBetween(currentUser.get(), forecastStart, forecastEnd).stream()
                .map(t -> new SingleData(t.getId(), t.getDescription(), t.getDate(), t.getAmount()))
                .toList();
        return calculator.outlook(seriesData, singles, start);
    }

    private PastMonth pastMonth(YearMonth month, Function<Long, String> icon) {
        DashboardResponse dashboard = dashboardService.actuals(month);
        return new PastMonth(
                month,
                dashboard.incomeTotal(),
                dashboard.expenses().actual(),
                dashboard.income().stream()
                        .map(line -> new CategoryData(line.id(), line.name(), icon.apply(line.id()), line.actual()))
                        .toList(),
                dashboard.categories().stream()
                        .filter(line -> line.actual().signum() > 0)
                        .map(line -> new CategoryData(line.id(), line.name(), icon.apply(line.id()), line.actual()))
                        .toList());
    }
}
