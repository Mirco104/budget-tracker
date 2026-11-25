package io.github.mirco104.budget.passkey;

import jakarta.validation.constraints.NotBlank;

/** US44: adding a passkey needs the password (decision 96). */
public record PasskeyOptionsRequest(@NotBlank(message = "Das Passwort ist Pflicht.") String password) {

    // Keeps the password out of logs
    @Override
    public String toString() {
        return "PasskeyOptionsRequest[]";
    }
}
