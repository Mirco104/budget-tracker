package io.github.mirco104.budget.auth;

/** Wrong e-mail or password: 401 with one text for both, so it does not tell whether the e-mail exists (US14). */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-Mail oder Passwort ist falsch.");
    }
}
