package io.github.mirco104.budget;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.mirco104.budget.user.AppUserDetails;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Fictional users with the default categories, and MockMvc signed in as one of them (R15). */
public final class TestUsers {

    public static final String PASSWORD = "test-only-password";

    private TestUsers() {
    }

    public static User create(UserService userService, String email) {
        return create(userService, email, Role.USER);
    }

    public static User create(UserService userService, String email, Role role) {
        return userService.create(email, email.substring(0, email.indexOf('@')), PASSWORD, role);
    }

    /** Every request is signed in as the user and carries a CSRF token. */
    public static MockMvc mockMvcFor(WebApplicationContext context, User user) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user(AppUserDetails.from(user))).with(csrf()))
                .build();
    }
}
