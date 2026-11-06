package io.github.mirco104.budget.category;

import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.recurring.FixedCosts;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categories and targets of the signed-in user (R15). */
@Service
public class CategoryService {

    private static final int SORT_ORDER_STEP = 10;

    private final CategoryRepository categoryRepository;
    private final CategoryTargetRepository categoryTargetRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final CurrentUser currentUser;

    public CategoryService(
            CategoryRepository categoryRepository, CategoryTargetRepository categoryTargetRepository,
            TransactionRepository transactionRepository,
            RecurringTransactionRepository recurringTransactionRepository, CurrentUser currentUser) {
        this.categoryRepository = categoryRepository;
        this.categoryTargetRepository = categoryTargetRepository;
        this.transactionRepository = transactionRepository;
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll(YearMonth month) {
        Map<Long, TargetEntry> targets = targetsInEffect(month);
        Set<Long> incomeOnly = incomeOnlyIds();
        Map<Long, FixedCosts.Result> fixedCosts = fixedCosts(month);
        return findAllOrdered().stream()
                .map(category -> CategoryResponse.from(
                        category, targets.get(category.getId()), incomeOnly.contains(category.getId()),
                        fixedCosts.get(category.getId())))
                .toList();
    }

    /** R25: the monthly share of the user's recurring expenses per category id, for the month. */
    private Map<Long, FixedCosts.Result> fixedCosts(YearMonth month) {
        List<FixedCosts.SeriesData> series = recurringTransactionRepository
                .findActiveBetween(currentUser.get(), month.atDay(1), month.atEndOfMonth()).stream()
                .map(r -> new FixedCosts.SeriesData(r.getId(), r.getCategory().getId(), r.getDescription(),
                        r.getAmount(), r.getInterval().months(), r.getStartDate(), r.getEndDate()))
                .toList();
        return FixedCosts.of(series, month);
    }

    /** The user's categories in their order. */
    @Transactional(readOnly = true)
    public List<Category> findAllOrdered() {
        return categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(currentUser.get());
    }

    /** Targets per category id in effect for the month (R3). */
    @Transactional(readOnly = true)
    public Map<Long, TargetEntry> targetsInEffect(YearMonth month) {
        List<TargetEntry> entries = categoryTargetRepository
                .findByCategoryOwnerAndValidFromLessThanEqual(currentUser.get(), month.atDay(1)).stream()
                .map(CategoryTarget::toEntry)
                .toList();
        return TargetResolver.inEffect(entries, month);
    }

    /** US10: a new category goes to the end of the order. Names are unique ignoring case (409). */
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByOwnerAndNameIgnoreCase(currentUser.get(), name)) {
            throw nameTaken(name);
        }
        String icon = request.icon() == null ? CategoryIcons.DEFAULT : knownIcon(request.icon());
        int sortOrder = categoryRepository.findTopByOwnerOrderBySortOrderDesc(currentUser.get())
                .map(last -> last.getSortOrder() + SORT_ORDER_STEP)
                .orElse(SORT_ORDER_STEP);
        Category category = categoryRepository.save(new Category(currentUser.get(), name, sortOrder, icon));
        // A new category has no entries yet (R24, R25)
        return CategoryResponse.from(category, null, false, null);
    }

    /**
     * US10: renames, moves and archives. If another category has the requested sortOrder, the category takes its
     * place, so moving to a neighbour's sortOrder swaps the two in a single call. Afterwards all categories are
     * renumbered in steps of 10, which also repairs equal sortOrders.
     */
    @Transactional
    public CategoryResponse update(long id, UpdateCategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        if (categoryRepository.existsByOwnerAndNameIgnoreCaseAndIdNot(currentUser.get(), name, id)) {
            throw nameTaken(name);
        }
        String icon = request.icon() == null ? category.getIcon() : knownIcon(request.icon());

        List<Category> ordered = new ArrayList<>(findAllOrdered());
        int sortOrder = request.sortOrder();
        if (sortOrder != category.getSortOrder()) {
            int target = indexOfOther(ordered, sortOrder, category);
            ordered.remove(category);
            if (target < 0) {
                target = (int) ordered.stream().filter(other -> other.getSortOrder() <= sortOrder).count();
            }
            ordered.add(target, category);
        }
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo((i + 1) * SORT_ORDER_STEP);
        }
        category.rename(name);
        category.setArchived(request.archived());
        category.changeIcon(icon);
        YearMonth month = YearMonth.now();
        return CategoryResponse.from(category, targetsInEffect(month).get(id), incomeOnlyIds().contains(id),
                fixedCosts(month).get(id));
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
        target.update(scale(request.targetAmount(), 2));
        categoryTargetRepository.save(target);
        return CategoryResponse.from(category, target.toEntry(), incomeOnlyIds().contains(id), fixedCosts(month).get(id));
    }

    /**
     * R24: categories with at least one transaction or series where every amount is positive. The smallest amount
     * over both decides: above zero means only income.
     */
    private Set<Long> incomeOnlyIds() {
        Map<Long, BigDecimal> minimum = new HashMap<>();
        Stream.concat(
                        transactionRepository.findMinAmountPerCategory(currentUser.get()).stream(),
                        recurringTransactionRepository.findMinAmountPerCategory(currentUser.get()).stream())
                .forEach(row -> minimum.merge((Long) row[0], (BigDecimal) row[1], BigDecimal::min));
        Set<Long> ids = new HashSet<>();
        minimum.forEach((id, amount) -> {
            if (amount.signum() > 0) {
                ids.add(id);
            }
        });
        return ids;
    }

    /** The category for a booking; 400 on field categoryId if it is unknown or another user's (R15). */
    @Transactional(readOnly = true)
    public Category findBookable(long categoryId) {
        return categoryRepository.findByIdAndOwner(categoryId, currentUser.get())
                .orElseThrow(() -> new InvalidFieldException("categoryId", "Die Kategorie existiert nicht."));
    }

    // Another user's category is 404 like an unknown one (R15)
    private Category find(long id) {
        return categoryRepository.findByIdAndOwner(id, currentUser.get())
                .orElseThrow(() -> new NotFoundException("Kategorie " + id + " wurde nicht gefunden."));
    }

    /** Position of another category with this sortOrder, -1 if there is none. */
    private static int indexOfOther(List<Category> ordered, int sortOrder, Category category) {
        for (int i = 0; i < ordered.size(); i++) {
            Category other = ordered.get(i);
            if (other != category && other.getSortOrder() == sortOrder) {
                return i;
            }
        }
        return -1;
    }

    /** US28: an icon key outside the fixed list is 400 on field icon. */
    private static String knownIcon(String icon) {
        if (!CategoryIcons.isKnown(icon)) {
            throw new InvalidFieldException("icon", "Das Icon gibt es nicht.");
        }
        return icon;
    }

    private static ConflictException nameTaken(String name) {
        return new ConflictException("Eine Kategorie mit dem Namen \"" + name + "\" gibt es schon.");
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
}
