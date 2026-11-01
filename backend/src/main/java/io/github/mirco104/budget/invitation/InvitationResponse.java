package io.github.mirco104.budget.invitation;

import java.time.Instant;

/** The link the admin passes on themselves; the app sends no e-mail (US15). */
public record InvitationResponse(String link, Instant expiresAt) {
}
