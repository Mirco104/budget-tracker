package io.github.mirco104.budget.account;

import jakarta.validation.constraints.NotNull;

/** US40: the setup wizard is done ("Einrichtung abschließen", "Später einrichten") or starts again. */
public record ChangeSetupRequest(@NotNull(message = "Pflichtfeld.") Boolean done) {
}
