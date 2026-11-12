package io.github.mirco104.budget.passwordreset;

import io.github.mirco104.budget.auth.AuthService;
import io.github.mirco104.budget.auth.MeResponse;
import io.github.mirco104.budget.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** US36 (ticket #81): reset links for a forgotten password. */
@RestController
public class PasswordResetController {

    public record PasswordResetLinkResponse(String link, Instant expiresAt) {
    }

    public record ResetPasswordRequest(@NotBlank(message = "Das Passwort ist Pflicht.") String password) {

        // Keeps the password out of logs
        @Override
        public String toString() {
            return "ResetPasswordRequest[]";
        }
    }

    private final PasswordResetService resetService;
    private final AuthService authService;

    public PasswordResetController(PasswordResetService resetService, AuthService authService) {
        this.resetService = resetService;
        this.authService = authService;
    }

    /** Only for admins (the service checks). The link opens the app with the token as a parameter. */
    @PostMapping("/api/users/{id}/password-reset")
    @ResponseStatus(HttpStatus.CREATED)
    public PasswordResetLinkResponse create(@PathVariable long id) {
        PasswordResetService.CreatedLink created = resetService.create(id);
        String link = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/")
                .queryParam("passwort", created.token())
                .toUriString();
        return new PasswordResetLinkResponse(link, created.expiresAt());
    }

    /** Without sign-in: sets the new password and signs the user in. */
    @PostMapping("/api/password-resets/{token}")
    public MeResponse reset(
            @PathVariable String token, @Valid @RequestBody ResetPasswordRequest reset,
            HttpServletRequest request, HttpServletResponse response) {
        User user = resetService.reset(token, reset.password());
        authService.signIn(user, request, response);
        return MeResponse.from(user);
    }
}
