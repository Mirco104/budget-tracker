package io.github.mirco104.budget.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first admin from environment variables on the first start (US15). Logs no values. */
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
        if (userRepository.count() > 0) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No user exists and ADMIN_EMAIL / ADMIN_PASSWORD are not set, so nobody can sign in.");
            return;
        }
        userService.create(email, displayName.isBlank() ? "Admin" : displayName, password, Role.ADMIN);
        log.info("Created the first admin from ADMIN_EMAIL.");
    }
}
