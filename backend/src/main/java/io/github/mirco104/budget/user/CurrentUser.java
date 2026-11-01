package io.github.mirco104.budget.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The signed-in user from the security context (R15). Services scope every query with it; controllers never take a
 * user id from the request.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** A reference to the signed-in user, enough to filter by owner without loading the row. */
    public User get() {
        return userRepository.getReferenceById(details().id());
    }

    public AppUserDetails details() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails details)) {
            // Security lets only signed-in requests through to the services
            throw new IllegalStateException("No signed-in user");
        }
        return details;
    }
}
