package io.github.mirco104.budget.category;

import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse updateTarget(long id, UpdateTargetRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Kategorie " + id + " wurde nicht gefunden."));

        if (category.isSystem()) {
            throw new ConflictException(
                    "Die Rest-Kategorie wird automatisch berechnet und kann nicht geändert werden.");
        }
        if (category.getType() == CategoryType.INCOME) {
            throw new ConflictException("Für Einnahmen-Kategorien kann kein Soll festgelegt werden.");
        }

        category.updateTarget(scale(request.targetAmount(), 2), scale(request.targetPercent(), 4));
        return CategoryResponse.from(category);
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
}
