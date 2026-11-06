package io.github.mirco104.budget.bankaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** US30: defaultAccount true makes this the default account; the previous one becomes a normal account (R26). */
public record UpdateBankAccountRequest(
        @NotBlank(message = "Der Name ist Pflicht.")
        @Size(max = 50, message = "Der Name darf höchstens 50 Zeichen haben.")
        String name,

        @Size(max = 50, message = "Die IBAN ist zu lang.")
        String iban,

        @NotNull(message = "Archiviert ist Pflicht.")
        Boolean archived,

        @NotNull(message = "Standardkonto ist Pflicht.")
        Boolean defaultAccount) {
}
