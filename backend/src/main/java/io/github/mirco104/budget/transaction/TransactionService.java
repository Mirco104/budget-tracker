package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.recurring.OccurrenceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
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

    public TransactionService(
            TransactionRepository transactionRepository, CategoryService categoryService,
            OccurrenceService occurrenceService) {
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.occurrenceService = occurrenceService;
    }

    // A transaction belongs to the month of its date (spec 11.3); occurrences of recurring ones are added (R12).
    // Newest first; on the same date occurrences come after single transactions.
    @Transactional(readOnly = true)
    public List<TransactionResponse> findByMonth(YearMonth month) {
        Stream<TransactionResponse> singles = transactionRepository
                .findByDateBetweenOrderByDateDescIdDesc(month.atDay(1), month.atEndOfMonth()).stream()
                .map(TransactionResponse::from);
        Stream<TransactionResponse> occurrences = occurrenceService.occurrencesIn(month).stream()
                .map(TransactionResponse::from);
        return Stream.concat(singles, occurrences)
                .sorted(Comparator.comparing(TransactionResponse::date).reversed())
                .toList();
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        checkAmount(request.amount());
        Transaction transaction = new Transaction(
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

    private Transaction findTransaction(long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaktion " + id + " wurde nicht gefunden."));
    }
}
