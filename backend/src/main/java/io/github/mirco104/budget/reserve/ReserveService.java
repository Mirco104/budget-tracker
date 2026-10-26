package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.recurring.OccurrenceService;
import io.github.mirco104.budget.recurring.RecurringTransaction;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
import io.github.mirco104.budget.reserve.ReserveCalculator.SeriesData;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReserveService {

    private final RecurringTransactionRepository recurringRepository;
    private final OccurrenceService occurrenceService;
    private final ReserveCalculator calculator = new ReserveCalculator();

    public ReserveService(RecurringTransactionRepository recurringRepository, OccurrenceService occurrenceService) {
        this.recurringRepository = recurringRepository;
        this.occurrenceService = occurrenceService;
    }

    @Transactional(readOnly = true)
    public ReserveResponse calculate(YearMonth month) {
        // Every series active somewhere in the forecast period; the calculator picks those active in the month
        YearMonth lastMonth = month.plusMonths(ReserveCalculator.FORECAST_MONTHS - 1);
        List<RecurringTransaction> series =
                recurringRepository.findActiveBetween(month.atDay(1), lastMonth.atEndOfMonth());
        Map<Long, Set<LocalDate>> exceptions = occurrenceService.exceptionsBySeries(series);
        List<SeriesData> data = series.stream()
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
        return calculator.calculate(data, month);
    }
}
