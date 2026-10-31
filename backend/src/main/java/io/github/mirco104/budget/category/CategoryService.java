package io.github.mirco104.budget.category;

import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
import io.github.mirco104.budget.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private static final int SORT_ORDER_STEP = 10;

    private final CategoryRepository categoryRepository;
    private final CategoryTargetRepository categoryTargetRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;

    public CategoryService(
            CategoryRepository categoryRepository, CategoryTargetRepository categoryTargetRepository,
            TransactionRepository transactionRepository,
            RecurringTransactionRepository recurringTransactionRepository) {
        this.categoryRepository = categoryRepository;
        this.categoryTargetRepository = categoryTargetRepository;
        this.transactionRepository = transactionRepository;
        this.recurringTransactionRepository = recurringTransactionRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll(YearMonth month) {
        Map<Long, TargetEntry> targets = targetsInEffect(month);
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(category -> CategoryResponse.from(category, targets.get(category.getId())))
                .toList();
    }

    /** Targets per category id in effect for the month (R3). */
    @Transactional(readOnly = true)
    public Map<Long, TargetEntry> targetsInEffect(YearMonth month) {
        List<TargetEntry> entries = categoryTargetRepository.findByValidFromLessThanEqual(month.atDay(1)).stream()
                .map(CategoryTarget::toEntry)
                .toList();
        return TargetResolver.inEffect(entries, month);
    }

    /** US10: a new category goes to the end of the order. Names are unique ignoring case (409). */
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw nameTaken(name);
        }
        int sortOrder = categoryRepository.findTopByOrderBySortOrderDesc()
                .map(last -> last.getSortOrder() + SORT_ORDER_STEP)
                .orElse(SORT_ORDER_STEP);
        Category category = categoryRepository.save(new Category(name, sortOrder));
        return CategoryResponse.from(category, null);
    }

    /**
     * US10: renames, moves and archives. If another category has the requested sortOrder, the two swap places, so
     * moving by one position is a single call.
     */
    @Transactional
    public CategoryResponse update(long id, UpdateCategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw nameTaken(name);
        }

        int sortOrder = request.sortOrder();
        if (sortOrder != category.getSortOrder()) {
            categoryRepository.findFirstBySortOrderAndIdNot(sortOrder, id)
                    .ifPresent(other -> other.moveTo(category.getSortOrder()));
            category.moveTo(sortOrder);
        }
        category.rename(name);
        category.setArchived(request.archived());
        return CategoryResponse.from(category, targetsInEffect(YearMonth.now()).get(id));
    }

    /** US10: only an unused category can be deleted; otherwise 409 with the hint to archive it. */
    @Transactional
    public void delete(long id) {
        Category category = find(id);
        if (transactionRepository.existsByCategoryId(id)
                || recurringTransactionRepository.existsByCategoryId(id)
                || categoryTargetRepository.existsByCategoryId(id)) {
            throw new ConflictException(
                    "Es gibt Einträge unter dieser Kategorie. Die Kategorie kann nur archiviert werden.");
        }
        categoryRepository.delete(category);
    }

    /** Sets the target from the month onward; an entry for the same month is overwritten. */
    @Transactional
    public CategoryResponse updateTarget(long id, YearMonth month, UpdateTargetRequest request) {
        Category category = find(id);

        CategoryTarget target = categoryTargetRepository.findByCategoryIdAndValidFrom(id, month.atDay(1))
                .orElseGet(() -> new CategoryTarget(category, month));
        target.update(scale(request.targetAmount(), 2), scale(request.targetPercent(), 4));
        categoryTargetRepository.save(target);
        return CategoryResponse.from(category, target.toEntry());
    }

    /** The category for a booking; 400 on field categoryId if it is unknown. */
    @Transactional(readOnly = true)
    public Category findBookable(long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new InvalidFieldException("categoryId", "Die Kategorie existiert nicht."));
    }

    private Category find(long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Kategorie " + id + " wurde nicht gefunden."));
    }

    private static ConflictException nameTaken(String name) {
        return new ConflictException("Eine Kategorie mit dem Namen \"" + name + "\" gibt es schon.");
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
}
