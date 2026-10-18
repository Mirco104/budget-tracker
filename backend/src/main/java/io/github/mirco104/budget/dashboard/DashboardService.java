package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.TransactionData;
import io.github.mirco104.budget.transaction.TransactionRepository;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final DashboardCalculator calculator = new DashboardCalculator();

    public DashboardService(CategoryRepository categoryRepository, TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse calculate(YearMonth month) {
        List<CategoryData> categories = categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(category -> new CategoryData(
                        category.getId(),
                        category.getName(),
                        category.getType(),
                        category.getGroup(),
                        category.getTargetAmount(),
                        category.getTargetPercent(),
                        category.isSystem()))
                .toList();
        List<TransactionData> transactions = transactionRepository
                .findByDateBetweenOrderByDateDescIdDesc(month.atDay(1), month.atEndOfMonth()).stream()
                .map(transaction -> new TransactionData(
                        transaction.getCategory().getId(), transaction.getAmount(), transaction.getDate()))
                .toList();
        // Yearly transactions follow with US6 (AP 7)
        return calculator.calculate(categories, transactions, List.of(), month);
    }
}
