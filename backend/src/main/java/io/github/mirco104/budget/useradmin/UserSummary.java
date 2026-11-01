package io.github.mirco104.budget.useradmin;

import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import java.time.Instant;

/** One account in the user overview (US25): account data only, never financial data (R15). */
public record UserSummary(
        Long id, String email, String displayName, Role role, Instant createdAt, Instant lastLoginAt) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getCreatedAt(), user.getLastLoginAt());
    }
}
