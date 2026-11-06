package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.recurring.OccurrenceService;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final OccurrenceService occurrenceService;
    private final CurrentUser currentUser;

    public TransactionService(
            TransactionRepository transactionRepository, CategoryService categoryService,
            OccurrenceService occurrenceService, CurrentUser currentUser) {
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.occurrenceService = occurrenceService;
        this.currentUser = currentUser;
    }

    /** The longest period of the list (US26): to must lie before from + 10 years. */
    static final int MAX_PERIOD_YEARS = 10;

    // Transactions with their date in the period, both days included, and the occurrences of series in it (R1, R12).
    // Newest first; on the same date occurrences come after single transactions.
    @Transactional(readOnly = true)
    public List<TransactionResponse> findBetween(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidFieldException("to", "„Bis“ darf nicht vor „Von“ liegen.");
        }
        if (!to.isBefore(from.plusYears(MAX_PERIOD_YEARS))) {
            throw new InvalidFieldException("to", "Der Zeitraum darf höchstens 10 Jahre lang sein.");
        }
        Stream<TransactionResponse> singles = transactionRepository
                .findByOwnerAndDateBetweenOrderByDateDescIdDesc(currentUser.get(), from, to)
                .stream()
                .map(TransactionResponse::from);
        Stream<TransactionResponse> occurrences = occurrenceService.occurrencesBetween(from, to).stream()
                .map(TransactionResponse::from);
        return Stream.concat(singles, occurrences)
                .sorted(Comparator.comparing(TransactionResponse::date).reversed())
                .toList();
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        checkAmount(request.amount());
        Transaction transaction = new Transaction(
                currentUser.get(),
                request.amount().setScale(2, RoundingMode.HALF_UP),
                request.description().trim(),
                request.date(),
                categoryService.findBookable(request.categoryId()));
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse update(long id, TransactionRequest request) {
        Transaction transaction = findTransaction(id);
        checkAmount(request.amount());
        transaction.update(
                request.amount().setScale(2, RoundingMode.HALF_UP),
                request.description().trim(),
                request.date(),
                categoryService.findBookable(request.categoryId()));
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(long id) {
        transactionRepository.delete(findTransaction(id));
    }

    // R11: the sign decides, so 0 is neither income nor expense
    private static void checkAmount(BigDecimal amount) {
        if (amount.signum() == 0) {
            throw new InvalidFieldException("amount", "Der Betrag darf nicht 0 sein.");
        }
    }

    // Another user's transaction is 404 like an unknown one (R15)
    private Transaction findTransaction(long id) {
        return transactionRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Transaktion " + id + " wurde nicht gefunden."));
    }
}
