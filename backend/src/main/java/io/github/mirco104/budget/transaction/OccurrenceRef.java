package io.github.mirco104.budget.transaction;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** An occurrence of a series by its series and its date, e.g. the one a transaction replaces (R28). */
public record OccurrenceRef(
        @NotNull(message = "Die Serie ist Pflicht.")
        Long recurringId,

        @NotNull(message = "Das Datum des Vorkommens ist Pflicht.")
        LocalDate date) {
}
