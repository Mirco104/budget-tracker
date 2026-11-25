package io.github.mirco104.budget.passkey;

import java.time.Instant;

/** A passkey in Settings › Konto (US44); never the key itself. */
public record PasskeyResponse(Long id, String label, Instant createdAt, Instant lastUsedAt) {

    public static PasskeyResponse from(Passkey passkey) {
        return new PasskeyResponse(passkey.getId(), passkey.getLabel(), passkey.getCreatedAt(), passkey.getLastUsedAt());
    }
}
