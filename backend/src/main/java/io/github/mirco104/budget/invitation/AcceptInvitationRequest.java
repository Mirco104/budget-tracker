package io.github.mirco104.budget.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US15: the invited person chooses e-mail, display name and password. */
public record AcceptInvitationRequest(
        @NotBlank(message = "Die E-Mail-Adresse ist Pflicht.")
        @Email(message = "Die E-Mail-Adresse ist ungültig.")
        @Size(max = 254, message = "Die E-Mail-Adresse darf höchstens 254 Zeichen haben.")
        String email,
        @NotBlank(message = "Der Anzeigename ist Pflicht.")
        @Size(max = 50, message = "Der Anzeigename darf höchstens 50 Zeichen haben.")
        String displayName,
        @NotBlank(message = "Das Passwort ist Pflicht.")
        String password) {

    // Keeps the password out of logs
    @Override
    public String toString() {
        return "AcceptInvitationRequest[]";
    }
}
