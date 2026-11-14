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

    /** The user's categories, alphabetically, each top-level category followed by its subcategories (R32). */
    @Transactional(readOnly = true)
    public List<Category> findAllOrdered() {
        return categoryRepository.findAllByOwner(currentUser.get()).stream()
                .sorted(CategoryOrder.TREE)
                .toList();
    }

    /** R32: the categories every calculation shows as lines; a subcategory counts as its parent. */
    @Transactional(readOnly = true)
    public List<Category> findCalculationCategories() {
        return findAllOrdered().stream().filter(category -> category.getParent() == null).toList();
    }

    /**
     * US10: names are unique ignoring case (409), per level (R32). A new category appears at its alphabetical place
     * (decision 38); the column sort_order is still filled, because the schema needs it, but nothing reads it any more.
     * A new subcategory without an icon takes the icon of its parent (US37).
     */
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();
        Category parent = parentFor(null, request.parentId());
        checkName(parent, name, -1);
        String icon = request.icon() != null ? knownIcon(request.icon())
                : parent != null ? parent.getIcon() : CategoryIcons.DEFAULT;
        int sortOrder = categoryRepository.findTopByOwnerOrderBySortOrderDesc(currentUser.get())
                .map(last -> last.getSortOrder() + SORT_ORDER_STEP)
                .orElse(SORT_ORDER_STEP);
        Category category = new Category(currentUser.get(), name, sortOrder, icon);
        category.moveTo(parent);
        categoryRepository.save(category);
        // A new category has no entries yet (R24, R25)
        return CategoryResponse.from(category, false);
    }

    /**
     * R33, US38: the category with this name at its level (top level for a null parent), created if there is none. An
     * archived one is 400 on field name, so the user reactivates it or picks another.
     */
    @Transactional
    public Category findOrCreate(String name, Category parent) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 50) {
            throw new InvalidFieldException("name", "Der Name muss 1 bis 50 Zeichen haben.");
        }
        java.util.Optional<Category> existing = parent == null
                ? categoryRepository.findFirstByOwnerAndParentIsNullAndNameIgnoreCase(currentUser.get(), trimmed)
                : categoryRepository.findFirstByParentAndNameIgnoreCase(parent, trimmed);
        if (existing.isPresent()) {
            if (existing.get().isArchived()) {
                throw new InvalidFieldException("name", "Die Kategorie \"" + trimmed + "\" ist archiviert.");
            }
            return existing.get();
        }
        long id = create(new CreateCategoryRequest(trimmed, null, parent == null ? null : parent.getId())).id();
        return find(id);
    }

    /**
     * US10: renames, archives and changes the icon (US28); the order follows the name (decision 38). R32: sets the
     * parent (null: top level); archiving a parent archives its subcategories, a subcategory of an archived parent
     * cannot be reactivated (409).
     */
    @Transactional
    public CategoryResponse update(long id, UpdateCategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        Category parent = parentFor(category, request.parentId());
        checkName(parent, name, id);
        String icon = request.icon() == null ? category.getIcon() : knownIcon(request.icon());
        if (!request.archived() && parent != null && parent.isArchived()) {
            throw new ConflictException("Die Oberkategorie ist archiviert. Reaktiviere zuerst die Oberkategorie.");
        }

        category.rename(name);
        category.moveTo(parent);
        category.setArchived(request.archived());
        category.changeIcon(icon);
        if (request.archived()) {
            categoryRepository.findAllByParent(category).forEach(child -> child.setArchived(true));
        }
        return CategoryResponse.from(category, incomeOnlyIds().contains(id));
    }

    /**
     * US10: only an unused category can be deleted; otherwise 409 with the hint to archive it. R32: a parent only
     * without subcategories (409).
     */
    @Transactional
    public void delete(long id) {
        Category category = find(id);
        if (categoryRepository.existsByParent(category)) {
            throw new ConflictException(
                    "Die Kategorie hat Unterkategorien. Lösche oder verschiebe zuerst die Unterkategorien.");
        }
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

    /**
     * R32: the parent of a category, 400 on field parentId if it is unknown or another user's (R15), archived, a
     * subcategory itself or the category itself, or if the category has subcategories. The archived check does not
     * apply when the parent stays the same, so an archived subcategory of an archived parent can still be renamed.
     */
    private Category parentFor(Category category, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Category parent = categoryRepository.findByIdAndOwner(parentId, currentUser.get())
                .orElseThrow(() -> new InvalidFieldException("parentId", "Die Oberkategorie existiert nicht."));
        if (category != null && parent.getId().equals(category.getId())) {
            throw new InvalidFieldException("parentId", "Eine Kategorie kann nicht ihre eigene Oberkategorie sein.");
        }
        if (parent.getParent() != null) {
            throw new InvalidFieldException("parentId", "Eine Unterkategorie kann keine Unterkategorien haben.");
        }
        if (category != null && categoryRepository.existsByParent(category)) {
            throw new InvalidFieldException(
                    "parentId", "Eine Kategorie mit Unterkategorien kann keine Oberkategorie haben.");
        }
        boolean unchanged = category != null && category.getParent() == parent;
        if (parent.isArchived() && !unchanged) {
            throw new InvalidFieldException("parentId", "Die Oberkategorie ist archiviert.");
        }
        return parent;
    }

    // R32: top-level names per user, subcategory names per parent, ignoring case (409)
    private void checkName(Category parent, String name, long id) {
        boolean taken = parent == null
                ? categoryRepository.existsByOwnerAndParentIsNullAndNameIgnoreCaseAndIdNot(currentUser.get(), name, id)
                : categoryRepository.existsByParentAndNameIgnoreCaseAndIdNot(parent, name, id);
        if (taken) {
            throw nameTaken(name);
        }
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
