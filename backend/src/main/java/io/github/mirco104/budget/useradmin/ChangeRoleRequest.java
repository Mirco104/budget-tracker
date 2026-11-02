package io.github.mirco104.budget.useradmin;

import io.github.mirco104.budget.user.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Die Rolle ist Pflicht.") Role role) {
}
