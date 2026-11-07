package io.github.mirco104.budget.recurring;

import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.recurring.OccurrenceMatcher.Planned;
import io.github.mirco104.budget.transaction.OccurrenceRef;
import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.user.CurrentUser;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Ist ersetzt Plan" (R28): a single transaction replaces an occurrence of a series through an exception that points to
 * it. Used by the transactions, the dashboard, the series and the import.
 */
@Service
public class ReplacementService {

    private final RecurringTransactionRepository recurringRepository;
    private final RecurringExceptionRepository exceptionRepository;
    private final OccurrenceService occurrenceService;
    private final CurrentUser currentUser;

    public ReplacementService(
            RecurringTransactionRepository recurringRepository, RecurringExceptionRepository exceptionRepository,
            OccurrenceService occurrenceService, CurrentUser currentUser) {
        this.recurringRepository = recurringRepository;
        this.exceptionRepository = exceptionRepository;
        this.occurrenceService = occurrenceService;
        this.currentUser = currentUser;
    }

    /**
     * The user's series with a counting occurrence on the date (R12, no exception); 400 on the field otherwise, also for
     * another user's series (R15).
     */
    @Transactional(readOnly = true)
    public RecurringTransaction countingOccurrence(OccurrenceRef occurrence, String field) {
        RecurringTransaction recurring = recurringRepository.findByIdAndOwner(occurrence.recurringId(), currentUser.get())
                .orElseThrow(() -> notCounting(field));
        Set<LocalDate> exceptions = exceptionRepository.findByRecurringIn(List.of(recurring)).stream()
                .map(RecurringException::getOccurrenceDate)
                .collect(Collectors.toSet());
        if (!recurring.schedule(exceptions).isOccurrence(occurrence.date())) {
            throw notCounting(field);
        }
        return recurring;
    }

    private static InvalidFieldException notCounting(String field) {
        return new InvalidFieldException(field, "Dieses geplante Vorkommen gibt es nicht oder es ist schon ersetzt.");
    }

    /** The occurrence stops counting; the transaction counts instead (R28). */
    @Transactional
    public void replace(RecurringTransaction recurring, LocalDate date, Transaction transaction) {
        exceptionRepository.save(new RecurringException(recurring, date, transaction));
    }

    /** The occurrence the transaction replaced counts again. */
    @Transactional
    public void unlink(Transaction transaction) {
        exceptionRepository.deleteByTransaction(transaction);
    }

    /** The occurrence each of the transactions replaces, by transaction id; missing when it replaces none. */
    @Transactional(readOnly = true)
    public Map<Long, OccurrenceRef> replacedBy(Collection<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        return exceptionRepository.findByTransactionIdIn(transactionIds).stream()
                .collect(Collectors.toMap(e -> e.getTransaction().getId(),
                        e -> new OccurrenceRef(e.getRecurring().getId(), e.getOccurrenceDate())));
    }

    /** Ids of the user's transactions that replace an occurrence; they count as recurring for R10. */
    @Transactional(readOnly = true)
    public Set<Long> replacingIds() {
        return new HashSet<>(exceptionRepository.findReplacingTransactionIds(currentUser.get()));
    }

    /** Occurrences that still count from one day to another, for the matching of R28. */
    @Transactional(readOnly = true)
    public List<Planned> plannedBetween(LocalDate from, LocalDate to) {
        return occurrenceService.occurrencesBetween(from, to).stream()
                .map(o -> new Planned(o.recurringId(), o.date(), o.amount(), o.description(), o.categoryId()))
                .toList();
    }

    /**
     * R28 for "Dieser und alle folgenden": the linked exceptions of the series from the date on, collected before they
     * are dropped.
     */
    @Transactional(readOnly = true)
    public List<RecurringException> linkedFrom(RecurringTransaction recurring, LocalDate from) {
        return exceptionRepository.findByRecurringAndOccurrenceDateGreaterThanEqualAndTransactionIsNotNull(recurring, from);
    }

    /**
     * R28: a dropped link goes to the series that now has the occurrence on its date; without one the transaction stays
     * a single transaction.
     */
    @Transactional
    public void relink(List<RecurringException> dropped, RecurringTransaction target) {
        exceptionRepository.flush();
        RecurrenceSchedule schedule = target.schedule(Set.of());
        for (RecurringException exception : dropped) {
            if (schedule.isOccurrence(exception.getOccurrenceDate())) {
                exceptionRepository.save(
                        new RecurringException(target, exception.getOccurrenceDate(), exception.getTransaction()));
            }
        }
    }
}
