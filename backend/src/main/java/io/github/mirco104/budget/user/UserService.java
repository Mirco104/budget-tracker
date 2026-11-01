package io.github.mirco104.budget.user;

import io.github.mirco104.budget.category.DefaultCategories;
import io.github.mirco104.budget.common.ConflictException;
import io.github.mirco104.budget.common.InvalidFieldException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    public static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DefaultCategories defaultCategories;
    private final Clock clock;

    public UserService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, DefaultCategories defaultCategories,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultCategories = defaultCategories;
        this.clock = clock;
    }

    /** A new user gets the default categories without targets (US15). E-mail unique (409), password ≥ 12. */
    @Transactional
    public User create(String email, String displayName, String password, Role role) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidFieldException("password",
                    "Das Passwort muss mindestens " + MIN_PASSWORD_LENGTH + " Zeichen haben.");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Diese E-Mail-Adresse ist schon registriert.");
        }
        User user = userRepository.save(new User(
                normalizedEmail, passwordEncoder.encode(password), displayName.trim(), role, Instant.now(clock)));
        defaultCategories.seedFor(user);
        return user;
    }
}
