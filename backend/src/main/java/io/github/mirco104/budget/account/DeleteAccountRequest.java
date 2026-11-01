package io.github.mirco104.budget.account;

import jakarta.validation.constraints.NotBlank;

/** US16: deleting needs the password. */
public record DeleteAccountRequest(@NotBlank(message = "Das Passwort ist Pflicht.") String password) {

    // Keeps the password out of logs
    @Override
    public String toString() {
        return "DeleteAccountRequest[]";
    }
}
