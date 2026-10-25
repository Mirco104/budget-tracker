package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;

    public TransactionService(TransactionRepository transactionRepository, CategoryService categoryService) {
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
    }

    // A transaction belongs to the month of its date (spec 11.3)
    @Transactional(readOnly = true)
    public List<TransactionResponse> findByMonth(YearMonth month) {
        return transactionRepository.findByDateBetweenOrderByDateDescIdDesc(month.atDay(1), month.atEndOfMonth())
                .stream()
                .map(TransactionResponse::from)
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
