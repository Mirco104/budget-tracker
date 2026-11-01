package io.github.mirco104.budget.auth;

/** Too many failed sign-ins for this e-mail: 429 (US14). */
public class LoginLockedException extends RuntimeException {

    public LoginLockedException() {
        super("Zu viele Fehlversuche. Die Anmeldung ist für 15 Minuten gesperrt.");
    }
}
