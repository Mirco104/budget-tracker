package io.github.mirco104.budget.transaction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Used for create and update. The sign of the amount decides: positive is income, negative is expense (R11).
 * An amount of 0 is rejected by TransactionService.
 */
public record TransactionRequest(
        @NotNull(message = "Der Betrag ist Pflicht.")
        @Digits(integer = 10, fraction = 2, message = "Der Betrag darf höchstens 2 Nachkommastellen haben.")
        BigDecimal amount,

        @NotBlank(message = "Die Beschreibung ist Pflicht.")
        @Size(max = 100, message = "Die Beschreibung darf höchstens 100 Zeichen lang sein.")
        String description,

        @NotNull(message = "Das Datum ist Pflicht.")
        LocalDate date,

        // Required unless transfer is true, then it must be missing (R27); checked by TransactionService
        Long categoryId,

        // R26: optional, the default account without it
        Long accountId,

        // R27: optional, false without it
        Boolean transfer) {
}
