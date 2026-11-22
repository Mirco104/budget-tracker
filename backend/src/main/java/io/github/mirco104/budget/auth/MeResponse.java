package io.github.mirco104.budget.auth;

import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;

/** The signed-in user (GET /api/auth/me), with the Ist stage (R35) and whether the setup is done (US40). */
public record MeResponse(Long id, String email, String displayName, Role role, int istStage, boolean setupDone) {

    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getIstStage(),
                user.isSetupDone());
    }
}
