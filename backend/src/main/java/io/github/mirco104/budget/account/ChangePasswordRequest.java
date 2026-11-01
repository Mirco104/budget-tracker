package io.github.mirco104.budget.account;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Das bisherige Passwort ist Pflicht.") String currentPassword,
        @NotBlank(message = "Das neue Passwort ist Pflicht.") String newPassword) {

    // Keeps the passwords out of logs
    @Override
    public String toString() {
        return "ChangePasswordRequest[]";
    }
}
