package io.github.mirco104.budget.passkey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenamePasskeyRequest(
        @NotBlank(message = "Der Name ist Pflicht.")
        @Size(max = PasskeyService.MAX_LABEL_LENGTH, message = "Der Name darf höchstens 50 Zeichen haben.")
        String label) {
}
