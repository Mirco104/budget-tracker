package io.github.mirco104.budget.category;

/**
 * A category of the signed-in user. incomeOnly: the category has entries and all of them are positive (R24). The plan
 * of a category is the sum of its planned items (P3) and comes with /api/plan.
 */
public record CategoryResponse(Long id, String name, String icon, boolean archived, boolean incomeOnly) {

    static CategoryResponse from(Category category, boolean incomeOnly) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getIcon(), category.isArchived(), incomeOnly);
    }
}
