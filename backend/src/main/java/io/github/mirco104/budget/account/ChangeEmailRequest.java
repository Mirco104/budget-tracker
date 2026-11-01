package io.github.mirco104.budget.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US16: changing the e-mail needs the password. */
public record ChangeEmailRequest(
        @NotBlank(message = "Die E-Mail-Adresse ist Pflicht.")
        @Email(message = "Die E-Mail-Adresse ist ungültig.")
        @Size(max = 254, message = "Die E-Mail-Adresse darf höchstens 254 Zeichen haben.")
        String email,
        @NotBlank(message = "Das Passwort ist Pflicht.") String password) {

    // Keeps the password out of logs
    @Override
    public String toString() {
        return "ChangeEmailRequest[]";
    }
}
