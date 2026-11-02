package io.github.mirco104.budget.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US16: the display name needs no password; it plays no role in signing in. */
public record ChangeDisplayNameRequest(
        @NotBlank(message = "Der Anzeigename ist Pflicht.")
        @Size(max = 50, message = "Der Anzeigename darf höchstens 50 Zeichen haben.")
        String displayName) {
}
