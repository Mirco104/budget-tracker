package io.github.mirco104.budget.passwordreset;

import io.github.mirco104.budget.auth.LoginAttempts;
import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.GoneException;
import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.user.CurrentUser;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import io.github.mirco104.budget.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US36 (ticket #81): an admin creates a link for an account whose password is forgotten; whoever opens it once within
 * 24 hours sets a new password. The app sends no e-mail (decision 19), the admin passes the link on.
 */
@Service
public class PasswordResetService {

    static final Duration VALIDITY = Duration.ofHours(24);
    private static final int TOKEN_BYTES = 32;

    /** A new link with its token; the token itself is not stored. */
    public record CreatedLink(String token, Instant expiresAt) {
    }

    private final PasswordResetRepository resetRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttempts loginAttempts;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            PasswordResetRepository resetRepository, UserRepository userRepository, PasswordEncoder passwordEncoder,
            LoginAttempts loginAttempts, CurrentUser currentUser, Clock clock) {
        this.resetRepository = resetRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * 403 for a user (CurrentUser), 404 for an unknown account, 409 for oneself (Einstellungen › Konto), for the system
     * admin and, unless the system admin asks, for another admin: a link must never give more rights than its creator.
     */
    @Transactional
    public CreatedLink create(long userId) {
        User admin = currentUser.requireAdmin();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Konto " + userId + " wurde nicht gefunden."));
        if (user.getId().equals(admin.getId())) {
            throw new ConflictException("Dein eigenes Passwort änderst du unter Einstellungen › Konto.");
        }
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw new ConflictException("Für den Systemadmin gibt es keinen Link zum Zurücksetzen.");
        }
        if (user.getRole() == Role.ADMIN && admin.getRole() != Role.SYSTEM_ADMIN) {
            throw new ConflictException("Das Passwort eines Admins setzt nur der Systemadmin zurück.");
        }
        Instant now = Instant.now(clock);
        resetRepository.retireOpenLinksOf(user, now);
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(VALIDITY);
        resetRepository.save(new PasswordReset(hash(token), user, admin, expiresAt));
        return new CreatedLink(token, expiresAt);
    }

    /** 404 for an unknown link, 410 for an expired or used one, 400 for a short password; lifts a login lock. */
    @Transactional
    public User reset(String token, String password) {
        PasswordReset reset = resetRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new NotFoundException("Diesen Link gibt es nicht."));
        Instant now = Instant.now(clock);
        if (!reset.isUsableAt(now)) {
            throw new GoneException("Der Link ist abgelaufen oder wurde schon benutzt.");
        }
        if (password.length() < UserService.MIN_PASSWORD_LENGTH) {
            throw new InvalidFieldException("password",
                    "Das Passwort muss mindestens " + UserService.MIN_PASSWORD_LENGTH + " Zeichen haben.");
        }
        User user = userRepository.findById(reset.getUser().getId()).orElseThrow();
        user.changePasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        reset.markUsed(now);
        loginAttempts.reset(user.getEmail());
        return user;
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }
}
