package io.github.mirco104.budget.importer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * R33, US38: the mappings the user confirmed in the card "Kategorien der Bank". Each entry maps the bank category to
 * categoryId, or creates the category first: parentName creates a new top-level category (or parentId names an
 * existing one) and name a subcategory below it; without name the parent itself is the category.
 */
public record BankCategoryMappingRequest(
        @NotNull(message = "Die Bank ist Pflicht.")
        BankProfile profile,
        @NotEmpty(message = "Mindestens eine Zuordnung ist Pflicht.")
        List<@Valid Mapping> mappings) {

    public record Mapping(
            @NotBlank(message = "Die Bankkategorie ist Pflicht.")
            @Size(max = 100, message = "Die Bankkategorie darf höchstens 100 Zeichen haben.")
            String bankCategory,
            Long categoryId,
            Long parentId,
            @Size(max = 50, message = "Der Name darf höchstens 50 Zeichen haben.")
            String parentName,
            @Size(max = 50, message = "Der Name darf höchstens 50 Zeichen haben.")
            String name) {
    }

    /** One stored mapping with the id of its category. */
    public record Stored(String bankCategory, long categoryId) {
    }
}
