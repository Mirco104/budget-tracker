package io.github.mirco104.budget.transaction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Used for create and update. Amounts are always positive (R11); the category decides
 * whether it is income or expense.
 */
public record TransactionRequest(
        @NotNull(message = "Der Betrag ist Pflicht.")
        @Positive(message = "Der Betrag muss größer als 0 sein.")
        @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
        BigDecimal amount,

        @NotBlank(message = "Die Beschreibung ist Pflicht.")
        @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
        String description,

        @NotNull(message = "Das Datum ist Pflicht.")
        LocalDate date,

        @NotNull(message = "Die Kategorie ist Pflicht.")
        Long categoryId) {
}
