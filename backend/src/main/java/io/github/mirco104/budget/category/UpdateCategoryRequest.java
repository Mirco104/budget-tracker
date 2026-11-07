package io.github.mirco104.budget.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * US10: rename and archive. Without an icon it stays (US28). There is no own order (decision 38); a sortOrder sent by
 * an older client is ignored.
 */
public record UpdateCategoryRequest(
        @NotBlank(message = "Der Name ist Pflicht.")
        @Size(max = 50, message = "Der Name darf höchstens 50 Zeichen haben.")
        String name,

        @NotNull(message = "Archiviert ist Pflicht.")
        Boolean archived,

        String icon) {
}
