package io.github.mirco104.budget.recurring;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * "Alle Einträge" (R13, ticket #80): the values that change for every part of a series. Interval, start and end date
 * stay; they change with "Dieser und alle folgenden". The amount is signed (R11); 0 is rejected by the service.
 */
public record RecurringValuesRequest(
        @NotNull(message = "Der Betrag ist Pflicht.")
        @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
        BigDecimal amount,

        @NotBlank(message = "Die Beschreibung ist Pflicht.")
        @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
        String description,

        @NotNull(message = "Die Kategorie ist Pflicht.")
        Long categoryId,

        // R26: optional, the default account without it
        Long accountId) {
}
