package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.TransactionData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.YearlyTransactionData;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.yearly.YearlyTransactionRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final YearlyTransactionRepository yearlyTransactionRepository;
    private final DashboardCalculator calculator = new DashboardCalculator();

    public DashboardService(
            CategoryRepository categoryRepository, CategoryService categoryService,
            TransactionRepository transactionRepository, YearlyTransactionRepository yearlyTransactionRepository) {
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
        this.yearlyTransactionRepository = yearlyTransactionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse calculate(YearMonth month) {
        Map<Long, TargetEntry> targets = categoryService.targetsInEffect(month);
        List<CategoryData> categories = categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(category -> {
                    TargetEntry target = targets.get(category.getId());
                    return new CategoryData(
                            category.getId(),
                            category.getName(),
                            category.getType(),
                            category.getGroup(),
                            target == null ? null : target.targetAmount(),
                            target == null ? null : target.targetPercent(),
                            category.isSystem());
                })
                .toList();
        List<TransactionData> transactions = transactionRepository
                .findByDateBetweenOrderByDateDescIdDesc(month.atDay(1), month.atEndOfMonth()).stream()
                .map(transaction -> new TransactionData(
                        transaction.getCategory().getId(), transaction.getAmount(), transaction.getDate()))
                .toList();
        // All yearly transactions; DashboardCalculator picks the ones valid in the month (R1)
        List<YearlyTransactionData> yearlyTransactions = yearlyTransactionRepository.findAllByOrderByIdAsc().stream()
                .map(transaction -> new YearlyTransactionData(
                        transaction.getId(), transaction.getCategory().getId(), transaction.getDescription(),
                        transaction.getAmountPerYear(), transaction.getDueDate(),
                        transaction.getValidFrom(), transaction.getValidTo()))
                .toList();
        return calculator.calculate(categories, transactions, yearlyTransactions, month);
    }
}
