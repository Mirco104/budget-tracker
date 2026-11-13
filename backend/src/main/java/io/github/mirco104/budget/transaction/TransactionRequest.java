package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.plan.TermRef;
import jakarta.validation.Valid;
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
        Boolean transfer,



        // P5: optional, the term of a planned item this booking fulfils (only when it is created)
        @Valid
        TermRef replaces,

        // P8: optional, "aus Rücklage bezahlen": the item key of a rare planned item whose reserve pot pays
        java.util.UUID reserveItemKey,

        // P8: optional, "aus Ersparnissen bezahlen": the saving goal that pays
        Long savingGoalId,

        // P1: optional, the kind Sparen: a transfer to a savings or depot account, without category
        Boolean saving) {
}
