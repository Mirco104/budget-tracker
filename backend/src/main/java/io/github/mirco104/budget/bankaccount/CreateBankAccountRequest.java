package io.github.mirco104.budget.bankaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US30: the name is trimmed; the IBAN is optional and stored without spaces in upper case (R26). */
public record CreateBankAccountRequest(
        @NotBlank(message = "Der Name ist Pflicht.")
        @Size(max = 50, message = "Der Name darf höchstens 50 Zeichen haben.")
        String name,

        @Size(max = 50, message = "Die IBAN ist zu lang.")
        String iban) {
}
