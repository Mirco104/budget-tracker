package io.github.mirco104.budget.recurring;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Used for create and for "Dieser und alle folgenden" (R13). The amount is signed per occurrence (R11);
 * 0 and an end date before the start date are rejected by RecurringTransactionService.
 */
public record RecurringTransactionRequest(
        @NotNull(message = "Der Betrag ist Pflicht.")
        @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
        BigDecimal amount,

        @NotBlank(message = "Die Beschreibung ist Pflicht.")
        @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
        String description,

        @NotNull(message = "Die Kategorie ist Pflicht.")
        Long categoryId,

        @NotNull(message = "Das Intervall ist Pflicht.")
        RecurringInterval interval,

        @NotNull(message = "Das Startdatum ist Pflicht.")
        LocalDate startDate,

        LocalDate endDate) {
}
