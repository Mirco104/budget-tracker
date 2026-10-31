package io.github.mirco104.budget.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** US10: a sortOrder that another category has swaps the two categories. */
public record UpdateCategoryRequest(
        @NotBlank(message = "Der Name ist Pflicht.")
        @Size(max = 50, message = "Der Name darf höchstens 50 Zeichen haben.")
        String name,

        @NotNull(message = "Die Reihenfolge ist Pflicht.")
        Integer sortOrder,

        @NotNull(message = "Archiviert ist Pflicht.")
        Boolean archived) {
}
