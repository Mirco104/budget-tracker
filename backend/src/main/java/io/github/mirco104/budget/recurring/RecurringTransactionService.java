package io.github.mirco104.budget.recurring;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.transaction.TransactionRequest;
import io.github.mirco104.budget.transaction.TransactionResponse;
import io.github.mirco104.budget.transaction.TransactionService;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Series of recurring transactions (R12) and editing their occurrences by splitting and exceptions (R13). */
@Service
public class RecurringTransactionService {

    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RecurringTransactionRepository recurringRepository;
    private final RecurringExceptionRepository exceptionRepository;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final CurrentUser currentUser;

    public RecurringTransactionService(
            RecurringTransactionRepository recurringRepository, RecurringExceptionRepository exceptionRepository,
            CategoryService categoryService, TransactionService transactionService, CurrentUser currentUser) {
        this.recurringRepository = recurringRepository;
        this.exceptionRepository = exceptionRepository;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
        this.currentUser = currentUser;
    }

    /** Series active in the month (R14). */
    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> findActive(YearMonth month) {
        return recurringRepository.findActiveBetween(currentUser.get(), month.atDay(1), month.atEndOfMonth()).stream()
                .map(RecurringTransactionResponse::from)
                .toList();
    }

    @Transactional
    public RecurringTransactionResponse create(RecurringTransactionRequest request) {
        Category category = validate(request);
        RecurringTransaction recurring = new RecurringTransaction(
                currentUser.get(),
                UUID.randomUUID(),
                request.amount().setScale(2, RoundingMode.HALF_UP),
                request.description().trim(),
                category,
                request.interval(),
                request.startDate(),
                request.endDate());
        return RecurringTransactionResponse.from(recurringRepository.save(recurring));
    }

    /** "Nur dieser Eintrag": suspends the occurrence and books the changed values as a single transaction. */
    @Transactional
    public TransactionResponse updateThis(long id, LocalDate occurrence, TransactionRequest request) {
        RecurringTransaction recurring = findOccurrence(id, occurrence);
        TransactionResponse replacement = transactionService.create(request);
        exceptionRepository.save(new RecurringException(recurring, occurrence));
        return replacement;
    }

    /**
     * "Dieser und alle folgenden": on the first occurrence the series itself changes; otherwise it ends the day
     * before and a new series with the same seriesId takes over. Exceptions from the occurrence on are dropped.
     */
    @Transactional
    public RecurringTransactionResponse updateFollowing(
            long id, LocalDate occurrence, RecurringTransactionRequest request) {
        RecurringTransaction recurring = findOccurrence(id, occurrence);
        Category category = validate(request);

        if (occurrence.equals(recurring.getStartDate())) {
            exceptionRepository.deleteByRecurringAndOccurrenceDateGreaterThanEqual(recurring, occurrence);
            recurring.update(
                    request.amount().setScale(2, RoundingMode.HALF_UP),
                    request.description().trim(),
                    category,
                    request.interval(),
                    request.startDate(),
                    request.endDate());
            return RecurringTransactionResponse.from(recurring);
        }

        LocalDate previous = scheduleOf(recurring).previousOccurrence(occurrence).orElseThrow();
        if (!request.startDate().isAfter(previous)) {
            throw new InvalidFieldException("startDate",
                    "Das Startdatum muss nach dem vorherigen Vorkommen (" + previous.format(GERMAN_DATE)
                            + ") liegen.");
        }
        endBefore(recurring, occurrence);
        RecurringTransaction following = new RecurringTransaction(
                currentUser.get(),
                recurring.getSeriesId(),
                request.amount().setScale(2, RoundingMode.HALF_UP),
                request.description().trim(),
                category,
                request.interval(),
                request.startDate(),
                request.endDate());
        return RecurringTransactionResponse.from(recurringRepository.save(following));
    }

    /** THIS suspends the occurrence; FOLLOWING ends the series before it, or deletes it from its first one. */
    @Transactional
    public void delete(long id, LocalDate occurrence, OccurrenceScope scope) {
        RecurringTransaction recurring = findOccurrence(id, occurrence);
        if (scope == OccurrenceScope.THIS) {
            exceptionRepository.save(new RecurringException(recurring, occurrence));
        } else if (occurrence.equals(recurring.getStartDate())) {
            exceptionRepository.deleteByRecurringAndOccurrenceDateGreaterThanEqual(recurring, occurrence);
            recurringRepository.delete(recurring);
        } else {
            endBefore(recurring, occurrence);
        }
    }

    private void endBefore(RecurringTransaction recurring, LocalDate occurrence) {
        recurring.endOn(occurrence.minusDays(1));
        exceptionRepository.deleteByRecurringAndOccurrenceDateGreaterThanEqual(recurring, occurrence);
    }

    // 404 for an unknown or another user's series (R15) and for a date that is not a counting occurrence of it
    private RecurringTransaction findOccurrence(long id, LocalDate occurrence) {
        RecurringTransaction recurring = recurringRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException(
                        "Wiederkehrende Transaktion " + id + " wurde nicht gefunden."));
        if (!scheduleOf(recurring).isOccurrence(occurrence)) {
            throw new NotFoundException("Die wiederkehrende Transaktion " + id + " hat am "
                    + occurrence.format(GERMAN_DATE) + " kein Vorkommen.");
        }
        return recurring;
    }

    private RecurrenceSchedule scheduleOf(RecurringTransaction recurring) {
        Set<LocalDate> exceptions = exceptionRepository.findByRecurringIn(List.of(recurring)).stream()
                .map(RecurringException::getOccurrenceDate)
                .collect(Collectors.toSet());
        return recurring.schedule(exceptions);
    }

    // R11 (amount ≠ 0), R4 (bookable category), end date not before start date (US6)
    private Category validate(RecurringTransactionRequest request) {
        if (request.amount().signum() == 0) {
            throw new InvalidFieldException("amount", "Der Betrag darf nicht 0 sein.");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new InvalidFieldException("endDate", "Das Enddatum darf nicht vor dem Startdatum liegen.");
        }
        return categoryService.findBookable(request.categoryId());
    }
}
