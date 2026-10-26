package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.dashboard.DashboardService;
import io.github.mirco104.budget.recurring.OccurrenceService;
import io.github.mirco104.budget.recurring.RecurringTransaction;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
import io.github.mirco104.budget.reserve.ReserveCalculator.MonthExpenses;
import io.github.mirco104.budget.reserve.ReserveCalculator.SeriesData;
import io.github.mirco104.budget.reserve.ReserveCalculator.SingleData;
import io.github.mirco104.budget.transaction.TransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReserveService {

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final OccurrenceService occurrenceService;
    private final DashboardService dashboardService;
    private final ReserveCalculator calculator = new ReserveCalculator();

    public ReserveService(
            RecurringTransactionRepository recurringRepository, TransactionRepository transactionRepository,
            OccurrenceService occurrenceService, DashboardService dashboardService) {
        this.recurringRepository = recurringRepository;
        this.transactionRepository = transactionRepository;
        this.occurrenceService = occurrenceService;
        this.dashboardService = dashboardService;
    }

    @Transactional(readOnly = true)
    public ReserveResponse calculate(YearMonth month) {
        // Forecast period M..M+11: every series active somewhere in it and every single transaction in it
        LocalDate forecastStart = month.atDay(1);
        LocalDate forecastEnd = month.plusMonths(ReserveCalculator.FORECAST_MONTHS - 1).atEndOfMonth();
        List<RecurringTransaction> series = recurringRepository.findActiveBetween(forecastStart, forecastEnd);
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
                .findByDateBetweenOrderByDateDescIdDesc(forecastStart, forecastEnd).stream()
                .map(t -> new SingleData(t.getId(), t.getDescription(), t.getDate(), t.getAmount()))
                .toList();

        // Expense actual A per month (R2) comes from the dashboard, so the rule stays in one place
        YearMonth firstEntryMonth = firstEntryMonth();
        List<MonthExpenses> pastMonths = firstEntryMonth == null
                ? List.of()
                : IntStream.rangeClosed(1, ReserveCalculator.AVERAGE_MONTHS)
                        .mapToObj(month::minusMonths)
                        .filter(past -> !past.isBefore(firstEntryMonth))
                        .map(past -> new MonthExpenses(past, dashboardService.calculate(past).expenses().actual()))
                        .toList();
        return calculator.calculate(seriesData, singles, pastMonths, firstEntryMonth, month);
    }

    private YearMonth firstEntryMonth() {
        return Stream.of(transactionRepository.findMinDate(), recurringRepository.findMinStartDate())
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .map(YearMonth::from)
                .orElse(null);
    }
}
