package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.dashboard.DashboardResponse;
import io.github.mirco104.budget.dashboard.DashboardService;
import io.github.mirco104.budget.plan.ItemTermRepository;
import io.github.mirco104.budget.plan.PlannedItemRepository;
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

    private final PlannedItemRepository itemRepository;
    private final ItemTermRepository termRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final DashboardService dashboardService;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ReserveCalculator calculator = new ReserveCalculator();

    public ReserveService(
            PlannedItemRepository itemRepository, ItemTermRepository termRepository,
            TransactionRepository transactionRepository, CategoryRepository categoryRepository,
            DashboardService dashboardService, CurrentUser currentUser, Clock clock) {
        this.itemRepository = itemRepository;
        this.termRepository = termRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
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
        // Forecast period M..M+11: the terms of every planned item without a booking and every booking in it
        LocalDate forecastStart = start.atDay(1);
        LocalDate forecastEnd = start.plusMonths(ReserveCalculator.FORECAST_MONTHS - 1).atEndOfMonth();
        Map<java.util.UUID, Set<LocalDate>> handled = new java.util.HashMap<>();
        termRepository.findBetween(currentUser.get(), forecastStart, forecastEnd).forEach(term ->
                handled.computeIfAbsent(term.getItemKey(), key -> new java.util.HashSet<>()).add(term.getTermDate()));
        List<SeriesData> seriesData = itemRepository.findAllOf(currentUser.get()).stream()
                .filter(item -> item.schedule() != null && !item.isSaving())
                .map(item -> new SeriesData(
                        item.getId(),
                        item.getName(),
                        item.getCategory() == null ? null : item.getCategory().getPath(),
                        item.getAmount(),
                        item.getRhythmUnit(),
                        item.getRhythmCount(),
                        item.schedule(),
                        handled.getOrDefault(item.getItemKey(), Set.of())))
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
