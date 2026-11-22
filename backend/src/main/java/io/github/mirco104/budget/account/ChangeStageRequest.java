package io.github.mirco104.budget.account;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** R35: the Ist stage, 1 (plan only) to 4 (bookings with categories). */
public record ChangeStageRequest(
        @NotNull(message = "Die Stufe ist Pflicht.")
        @Min(value = 1, message = "Die Stufe ist 1 bis 4.")
        @Max(value = 4, message = "Die Stufe ist 1 bis 4.")
        Integer stage) {
}
