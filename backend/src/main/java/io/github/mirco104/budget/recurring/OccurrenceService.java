package io.github.mirco104.budget.recurring;

import io.github.mirco104.budget.user.CurrentUser;
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
    private final CurrentUser currentUser;

    public OccurrenceService(
            RecurringTransactionRepository recurringRepository, RecurringExceptionRepository exceptionRepository,
            CurrentUser currentUser) {
        this.recurringRepository = recurringRepository;
        this.exceptionRepository = exceptionRepository;
        this.currentUser = currentUser;
    }

    /** Occurrences in the month, oldest first. */
    @Transactional(readOnly = true)
    public List<Occurrence> occurrencesIn(YearMonth month) {
        return occurrencesBetween(month.atDay(1), month.atEndOfMonth());
    }

    /** Occurrences from one day to another, both included, oldest first (US26). */
    @Transactional(readOnly = true)
    public List<Occurrence> occurrencesBetween(LocalDate from, LocalDate to) {
        List<RecurringTransaction> series = recurringRepository.findActiveBetween(currentUser.get(), from, to);
        if (series.isEmpty()) {
            return List.of();
        }
        Map<Long, Set<LocalDate>> exceptions = exceptionsBySeries(series);
        return series.stream()
                .flatMap(recurring -> recurring.schedule(exceptions.getOrDefault(recurring.getId(), Set.of()))
                        .occurrencesBetween(from, to).stream()
                        .map(date -> new Occurrence(
                                recurring.getId(),
                                recurring.getAmount(),
                                recurring.getDescription(),
                                date,
                                recurring.getCategory().getId(),
                                recurring.getCategory().getName(),
                                recurring.getAccount().getId(),
                                recurring.getAccount().getName())))
                .sorted(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::recurringId))
                .toList();
    }

    /** Suspended occurrence dates (R13) by series id; series without exceptions are missing from the map. */
    @Transactional(readOnly = true)
    public Map<Long, Set<LocalDate>> exceptionsBySeries(List<RecurringTransaction> series) {
        if (series.isEmpty()) {
            return Map.of();
        }
        return exceptionRepository.findByRecurringIn(series).stream()
                .collect(Collectors.groupingBy(
                        exception -> exception.getRecurring().getId(),
                        Collectors.mapping(RecurringException::getOccurrenceDate, Collectors.toSet())));
    }
}
