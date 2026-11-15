package io.github.mirco104.budget.transaction;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Ticket #136: the bookings of a multiple selection get one category. */
public record CategorizeRequest(
        @NotEmpty(message = "Bitte mindestens eine Buchung wählen.")
        @Size(max = 1000, message = "Höchstens 1000 Buchungen auf einmal.")
        List<@NotNull Long> ids,

        @NotNull(message = "Die Kategorie ist Pflicht.")
        Long categoryId) {
}
