package io.github.mirco104.budget.yearly;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Used for create and update. Amounts are always positive (R11). validFrom and validTo are months
 * ("2026-10"); validTo is optional and inclusive.
 */
public record YearlyTransactionRequest(
        @NotNull(message = "Der Betrag ist Pflicht.")
        @Positive(message = "Der Betrag muss größer als 0 sein.")
        @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
        BigDecimal amountPerYear,

        @NotBlank(message = "Die Beschreibung ist Pflicht.")
        @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
        String description,

        LocalDate dueDate,

        @NotNull(message = "Der Monat „gültig ab“ ist Pflicht.")
        YearMonth validFrom,

        YearMonth validTo,

        @NotNull(message = "Die Kategorie ist Pflicht.")
        Long categoryId) {
}
