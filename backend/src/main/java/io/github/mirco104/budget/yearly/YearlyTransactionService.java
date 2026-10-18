package io.github.mirco104.budget.yearly;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class YearlyTransactionService {

    private final YearlyTransactionRepository yearlyTransactionRepository;
    private final CategoryRepository categoryRepository;

    public YearlyTransactionService(
            YearlyTransactionRepository yearlyTransactionRepository, CategoryRepository categoryRepository) {
        this.yearlyTransactionRepository = yearlyTransactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<YearlyTransactionResponse> findValidIn(YearMonth month) {
        return yearlyTransactionRepository.findValidIn(month.atDay(1)).stream()
                .map(YearlyTransactionResponse::from)
                .toList();
    }

    @Transactional
    public YearlyTransactionResponse create(YearlyTransactionRequest request) {
        checkValidityRange(request);
        YearlyTransaction transaction = new YearlyTransaction(
                request.amountPerYear().setScale(2, RoundingMode.HALF_UP),
                request.description().trim(),
                request.dueDate(),
                request.validFrom(),
                request.validTo(),
                findCategory(request.categoryId()));
        return YearlyTransactionResponse.from(yearlyTransactionRepository.save(transaction));
    }

    @Transactional
    public YearlyTransactionResponse update(long id, YearlyTransactionRequest request) {
        YearlyTransaction transaction = findTransaction(id);
        checkValidityRange(request);
        transaction.update(
                request.amountPerYear().setScale(2, RoundingMode.HALF_UP),
                request.description().trim(),
                request.dueDate(),
                request.validFrom(),
                request.validTo(),
                findCategory(request.categoryId()));
        return YearlyTransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(long id) {
        yearlyTransactionRepository.delete(findTransaction(id));
    }

    private static void checkValidityRange(YearlyTransactionRequest request) {
        if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new InvalidFieldException("validTo", "Der Monat „gültig bis“ darf nicht vor „gültig ab“ liegen.");
        }
    }

    private YearlyTransaction findTransaction(long id) {
        return yearlyTransactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Jährliche Transaktion " + id + " wurde nicht gefunden."));
    }

    private Category findCategory(long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new InvalidFieldException("categoryId", "Die Kategorie existiert nicht."));
    }
}
