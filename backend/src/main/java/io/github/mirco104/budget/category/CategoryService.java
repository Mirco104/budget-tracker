package io.github.mirco104.budget.category;

import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.plan.PlannedItemRepository;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categories of the signed-in user (R15); their plan comes from the planned items (P3, section 19). */
@Service
public class CategoryService {

    private static final int SORT_ORDER_STEP = 10;

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final PlannedItemRepository plannedItemRepository;
    private final CurrentUser currentUser;

    public CategoryService(
            CategoryRepository categoryRepository, TransactionRepository transactionRepository,
            PlannedItemRepository plannedItemRepository, CurrentUser currentUser) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.plannedItemRepository = plannedItemRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        Set<Long> incomeOnly = incomeOnlyIds();
        return findAllOrdered().stream()
                .map(category -> CategoryResponse.from(category, incomeOnly.contains(category.getId())))
                .toList();
    }

    /** The user's categories, alphabetically (decision 38). */
    @Transactional(readOnly = true)
    public List<Category> findAllOrdered() {
        return categoryRepository.findAllByOwner(currentUser.get()).stream()
                .sorted(CategoryOrder.ALPHABETICAL)
                .toList();
    }

    /**
     * US10: names are unique ignoring case (409). A new category appears at its alphabetical place (decision 38);
     * the column sort_order is still filled, because the schema needs it, but nothing reads it any more.
     */
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
        return CategoryResponse.from(category, false);
    }

    /** US10: renames, archives and changes the icon (US28); the order follows the name (decision 38). */
    @Transactional
    public CategoryResponse update(long id, UpdateCategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        if (categoryRepository.existsByOwnerAndNameIgnoreCaseAndIdNot(currentUser.get(), name, id)) {
            throw nameTaken(name);
        }
        String icon = request.icon() == null ? category.getIcon() : knownIcon(request.icon());

        category.rename(name);
        category.setArchived(request.archived());
        category.changeIcon(icon);
        return CategoryResponse.from(category, incomeOnlyIds().contains(id));
    }

    /** US10: only an unused category can be deleted; otherwise 409 with the hint to archive it. */
    @Transactional
    public void delete(long id) {
        Category category = find(id);
        if (transactionRepository.existsByCategoryId(id)
                || transactionRepository.existsByCategoryBeforeTransferId(id)
                || plannedItemRepository.existsByCategoryId(id)) {
            throw new ConflictException(
                    "Es gibt Einträge unter dieser Kategorie. Die Kategorie kann nur archiviert werden.");
        }
        categoryRepository.delete(category);
    }

    /**
     * R24: categories with at least one transaction or planned item where every amount is positive. The smallest amount
     * over both decides: above zero means only income.
     */
    private Set<Long> incomeOnlyIds() {
        Map<Long, BigDecimal> minimum = new HashMap<>();
        Stream.concat(
                        transactionRepository.findMinAmountPerCategory(currentUser.get()).stream(),
                        plannedItemRepository.findMinAmountPerCategory(currentUser.get()).stream())
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
}
