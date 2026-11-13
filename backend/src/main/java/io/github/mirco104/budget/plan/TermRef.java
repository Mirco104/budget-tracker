package io.github.mirco104.budget.plan;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * A term by the id of a planned item version and its planned date (P5). The import still sends the id as recurringId
 * (R28), so that name is accepted too.
 */
public record TermRef(
        @NotNull(message = "Der Posten ist Pflicht.")
        @JsonAlias("recurringId")
        Long itemId,
        @NotNull(message = "Das Datum des Termins ist Pflicht.")
        LocalDate date) {
}
