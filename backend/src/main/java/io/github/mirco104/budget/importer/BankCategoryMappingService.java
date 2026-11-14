package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryService;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.importer.BankCategoryMappingRequest.Mapping;
import io.github.mirco104.budget.importer.BankCategoryMappingRequest.Stored;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** R33, US38, decision 63: stores the mappings of bank categories, creating the new categories first. */
@Service
public class BankCategoryMappingService {

    private final BankCategoryMappingRepository repository;
    private final CategoryService categoryService;
    private final CurrentUser currentUser;

    public BankCategoryMappingService(
            BankCategoryMappingRepository repository, CategoryService categoryService, CurrentUser currentUser) {
        this.repository = repository;
        this.categoryService = categoryService;
        this.currentUser = currentUser;
    }

    /** The mappings of one bank by their bank category; a mapping to an archived category does not count (R33). */
    @Transactional(readOnly = true)
    public Map<String, Long> valid(BankProfile profile) {
        Map<String, Long> result = new HashMap<>();
        for (BankCategoryMapping mapping : repository.findByOwnerAndProfile(currentUser.get(), profile)) {
            if (!mapping.getCategory().isArchived()) {
                result.put(mapping.getBankCategory(), mapping.getCategory().getId());
            }
        }
        return result;
    }

    /**
     * Creates or replaces the mappings. A category that is unknown, another user's (R15) or archived is 400; a new
     * category with the name of an existing one at its level is that one, so two bank categories with the same new
     * parent create it once.
     */
    @Transactional
    public List<Stored> save(BankCategoryMappingRequest request) {
        User owner = currentUser.get();
        List<Stored> stored = new ArrayList<>();
        for (Mapping mapping : request.mappings()) {
            Category category = categoryOf(mapping);
            String bankCategory = mapping.bankCategory().trim();
            repository.findByOwnerAndProfileAndBankCategory(owner, request.profile(), bankCategory)
                    .ifPresentOrElse(
                            existing -> existing.mapTo(category),
                            () -> repository.save(new BankCategoryMapping(owner, request.profile(), bankCategory, category)));
            stored.add(new Stored(bankCategory, category.getId()));
        }
        return stored;
    }

    private Category categoryOf(Mapping mapping) {
        if (mapping.categoryId() != null) {
            return active(categoryService.findBookable(mapping.categoryId()), "categoryId");
        }
        Category parent;
        if (mapping.parentId() != null) {
            parent = active(categoryService.findBookable(mapping.parentId()), "parentId");
        } else if (mapping.parentName() != null && !mapping.parentName().isBlank()) {
            parent = categoryService.findOrCreate(mapping.parentName(), null);
        } else {
            throw new InvalidFieldException("categoryId", "Wähle eine Kategorie oder lege eine an.");
        }
        if (mapping.name() == null || mapping.name().isBlank()) {
            return parent;
        }
        if (parent.getParent() != null) {
            throw new InvalidFieldException("parentId", "Eine Unterkategorie kann keine Unterkategorien haben.");
        }
        return categoryService.findOrCreate(mapping.name(), parent);
    }

    private static Category active(Category category, String field) {
        if (category.isArchived()) {
            throw new InvalidFieldException(field, "Die Kategorie ist archiviert.");
        }
        return category;
    }
}
