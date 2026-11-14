package io.github.mirco104.budget.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * US10: the name is trimmed before it is stored. Without an icon the category gets "tag" (US28), a subcategory the icon
 * of its parent. parentId: the parent category (R32), null for a top-level category.
 */
public record CreateCategoryRequest(
        @NotBlank(message = "Der Name ist Pflicht.")
        @Size(max = 50, message = "Der Name darf höchstens 50 Zeichen haben.")
        String name,

        String icon,
        Long parentId) {
}
