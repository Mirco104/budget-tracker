package io.github.mirco104.budget.user;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The system admin is always the account with the e-mail from ADMIN_EMAIL (US15, US25), checked on every start.
 * A missing account is created with ADMIN_PASSWORD; an existing one becomes system admin and keeps its password;
 * a previous system admin with another e-mail becomes admin. Logs no values.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final String email;
    private final String password;
    private final String displayName;

    public AdminBootstrap(
            UserRepository userRepository, UserService userService,
            @Value("${app.admin.email:}") String email,
            @Value("${app.admin.password:}") String password,
            @Value("${app.admin.display-name:}") String displayName) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> systemAdmins = userRepository.findByRole(Role.SYSTEM_ADMIN);
        if (email.isBlank()) {
            if (systemAdmins.isEmpty()) {
                log.warn("ADMIN_EMAIL is not set and there is no system admin.");
            }
            return;
        }

        Optional<User> account = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (account.isEmpty() && password.isBlank()) {
            log.warn("No account has ADMIN_EMAIL and ADMIN_PASSWORD is not set, so the system admin stays as it is.");
            return;
        }

        // Exactly one system admin: a previous one with another e-mail keeps admin rights
        for (User previous : systemAdmins) {
            if (account.isEmpty() || !previous.getId().equals(account.get().getId())) {
                previous.changeRole(Role.ADMIN);
                log.info("The previous system admin is now an admin.");
            }
        }

        if (account.isEmpty()) {
            userService.create(email, displayName.isBlank() ? "Admin" : displayName, password, Role.SYSTEM_ADMIN);
            log.info("Created the system admin from ADMIN_EMAIL.");
        } else if (account.get().getRole() != Role.SYSTEM_ADMIN) {
            account.get().changeRole(Role.SYSTEM_ADMIN);
            log.info("The account with ADMIN_EMAIL is now the system admin.");
        }
    }
}
