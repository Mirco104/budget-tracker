package io.github.mirco104.budget.account;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/**
 * R35: the Ist stage, 1 (plan only) to 4 (bookings with categories). Decision 90: when leaving stage 1, autoEntries
 * asks to enter income and fixed costs automatically (saving on ticking) from entriesFrom on (today when empty); both
 * are ignored for other switches.
 */
public record ChangeStageRequest(
        @NotNull(message = "Die Stufe ist Pflicht.")
        @Min(value = 1, message = "Die Stufe ist 1 bis 4.")
        @Max(value = 4, message = "Die Stufe ist 1 bis 4.")
        Integer stage,
        Boolean autoEntries,
        @PastOrPresent(message = "Das Datum darf nicht in der Zukunft liegen.")
        LocalDate entriesFrom) {

    public ChangeStageRequest(Integer stage) {
        this(stage, null, null);
    }
}
