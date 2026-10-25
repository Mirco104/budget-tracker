package io.github.mirco104.budget.category;

import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryTargetRepository categoryTargetRepository;

    public CategoryService(CategoryRepository categoryRepository, CategoryTargetRepository categoryTargetRepository) {
        this.categoryRepository = categoryRepository;
        this.categoryTargetRepository = categoryTargetRepository;
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

    /** Sets the target from the month onward; an entry for the same month is overwritten. */
    @Transactional
    public CategoryResponse updateTarget(long id, YearMonth month, UpdateTargetRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Kategorie " + id + " wurde nicht gefunden."));

        if (category.isSystem()) {
            throw new ConflictException(
                    "Die Rest-Kategorie wird automatisch berechnet und kann nicht geändert werden.");
        }
        if (category.getGroup() == null) {
            throw new ConflictException("Für Kategorien ohne Gruppe kann kein Soll festgelegt werden.");
        }

        CategoryTarget target = categoryTargetRepository.findByCategoryIdAndValidFrom(id, month.atDay(1))
                .orElseGet(() -> new CategoryTarget(category, month));
        target.update(scale(request.targetAmount(), 2), scale(request.targetPercent(), 4));
        categoryTargetRepository.save(target);
        return CategoryResponse.from(category, target.toEntry());
    }

    /** The category for a booking; 400 on field categoryId if it is unknown or the remainder category (R4). */
    @Transactional(readOnly = true)
    public Category findBookable(long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new InvalidFieldException("categoryId", "Die Kategorie existiert nicht."));
        if (category.isSystem()) {
            throw new InvalidFieldException("categoryId", "Auf die Rest-Kategorie kann nicht gebucht werden.");
        }
        return category;
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
}
