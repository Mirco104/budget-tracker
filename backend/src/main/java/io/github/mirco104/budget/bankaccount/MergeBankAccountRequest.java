package io.github.mirco104.budget.bankaccount;

import jakarta.validation.constraints.NotNull;

/** Ticket #139: the account that takes over everything of the merged one. */
public record MergeBankAccountRequest(
        @NotNull(message = "Bitte das Zielkonto wählen.")
        Long targetId) {
}
