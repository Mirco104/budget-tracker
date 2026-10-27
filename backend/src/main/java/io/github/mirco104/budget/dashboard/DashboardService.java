package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.TransactionData;
import io.github.mirco104.budget.recurring.OccurrenceService;
import io.github.mirco104.budget.transaction.TransactionRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final OccurrenceService occurrenceService;
    private final DashboardCalculator calculator = new DashboardCalculator();

    public DashboardService(
            CategoryRepository categoryRepository, CategoryService categoryService,
            TransactionRepository transactionRepository, OccurrenceService occurrenceService) {
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
        this.occurrenceService = occurrenceService;
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
                            target == null ? null : target.targetAmount(),
                            target == null ? null : target.targetPercent());
                })
                .toList();
        // R1: an occurrence of a recurring transaction counts with its full amount, like a single transaction
        Stream<TransactionData> singles = transactionRepository
                .findByDateBetweenOrderByDateDescIdDesc(month.atDay(1), month.atEndOfMonth()).stream()
                .map(transaction -> new TransactionData(
                        transaction.getCategory().getId(), transaction.getAmount(), transaction.getDate(), false));
        Stream<TransactionData> occurrences = occurrenceService.occurrencesIn(month).stream()
                .map(occurrence -> new TransactionData(
                        occurrence.categoryId(), occurrence.amount(), occurrence.date(), true));
        List<TransactionData> transactions = Stream.concat(singles, occurrences).toList();
        return calculator.calculate(categories, transactions, month);
    }
}
