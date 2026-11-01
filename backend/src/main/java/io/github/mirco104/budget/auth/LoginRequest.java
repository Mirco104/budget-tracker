package io.github.mirco104.budget.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Die E-Mail-Adresse ist Pflicht.") String email,
        @NotBlank(message = "Das Passwort ist Pflicht.") String password) {

    // Keeps the password out of logs
    @Override
    public String toString() {
        return "LoginRequest[]";
    }
}
