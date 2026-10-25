package io.github.mirco104.budget.recurring;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculated occurrences of all series in a month (R12), for the transaction list and the dashboard (R1). */
@Service
public class OccurrenceService {

    private final RecurringTransactionRepository recurringRepository;
    private final RecurringExceptionRepository exceptionRepository;

    public OccurrenceService(
            RecurringTransactionRepository recurringRepository, RecurringExceptionRepository exceptionRepository) {
        this.recurringRepository = recurringRepository;
        this.exceptionRepository = exceptionRepository;
    }

    /** Occurrences in the month, oldest first. */
    @Transactional(readOnly = true)
    public List<Occurrence> occurrencesIn(YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<RecurringTransaction> series = recurringRepository.findActiveBetween(from, to);
        if (series.isEmpty()) {
            return List.of();
        }
        Map<Long, Set<LocalDate>> exceptions = exceptionRepository.findByRecurringIn(series).stream()
                .collect(Collectors.groupingBy(
                        exception -> exception.getRecurring().getId(),
                        Collectors.mapping(RecurringException::getOccurrenceDate, Collectors.toSet())));
        return series.stream()
                .flatMap(recurring -> recurring.schedule(exceptions.getOrDefault(recurring.getId(), Set.of()))
                        .occurrencesBetween(from, to).stream()
                        .map(date -> new Occurrence(
                                recurring.getId(),
                                recurring.getAmount(),
                                recurring.getDescription(),
                                date,
                                recurring.getCategory().getId(),
                                recurring.getCategory().getName())))
                .sorted(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::recurringId))
                .toList();
    }
}
