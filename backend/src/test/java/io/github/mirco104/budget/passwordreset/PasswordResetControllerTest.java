package io.github.mirco104.budget.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.auth.LoginAttempts;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import io.github.mirco104.budget.user.UserService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** US36 (ticket #81): an admin creates a reset link; it sets a new password once within 24 hours. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetControllerTest {

    private static final String NEW_PASSWORD = "new-test-only-password";

    @Autowired
    private MockMvc anonymous;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetRepository resetRepository;

    @Autowired
    private LoginAttempts loginAttempts;

    private User systemAdmin;
    private User admin;
    private User user;

    @BeforeEach
    void createUsers() {
        // Created on startup from application-test.properties
        systemAdmin = userRepository.findByEmail("person-a@example.test").orElseThrow();
        admin = TestUsers.create(userService, "person-b@example.test", Role.ADMIN);
        user = TestUsers.create(userService, "person-c@example.test");
    }

    @Test
    void anAdminCreatesALinkValidFor24HoursAndOnlyItsHashIsStored() throws Exception {
        Instant before = Instant.now();

        String body = TestUsers.mockMvcFor(context, admin).perform(post("/api/users/{id}/password-reset", user.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.link", matchesPattern("^http://localhost/\\?passwort=[A-Za-z0-9_-]{43}$")))
                .andReturn().getResponse().getContentAsString();

        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(Duration.between(before, expiresAt))
                .isBetween(Duration.ofHours(24).minusMinutes(1), Duration.ofHours(24).plusMinutes(1));
        String token = tokenOf(body);
        assertThat(resetRepository.findByTokenHash(token)).isEmpty();
        assertThat(resetRepository.findByTokenHash(PasswordResetService.hash(token))).isPresent();
    }

    @Test
    void theLinkSetsTheNewPasswordSignsInAndWorksOnlyOnce() throws Exception {
        String token = createLink(admin, user);
        MockHttpSession session = new MockHttpSession();

        reset(session, token, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("person-c@example.test"));

        anonymous.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("person-c@example.test"));
        login("person-c@example.test", NEW_PASSWORD).andExpect(status().isNoContent());
        login("person-c@example.test", TestUsers.PASSWORD).andExpect(status().isUnauthorized());
        reset(new MockHttpSession(), token, "another-new-password")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("Der Link ist abgelaufen oder wurde schon benutzt."));
    }

    @Test
    void theLinkLiftsALoginLock() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginAttempts.recordFailure("person-c@example.test");
        }
        assertThat(loginAttempts.isLocked("person-c@example.test")).isTrue();

        reset(new MockHttpSession(), createLink(admin, user), NEW_PASSWORD).andExpect(status().isOk());

        assertThat(loginAttempts.isLocked("person-c@example.test")).isFalse();
    }

    @Test
    void aNewLinkMakesTheOlderOneUnusable() throws Exception {
        String older = createLink(admin, user);
        String newer = createLink(admin, user);

        reset(new MockHttpSession(), older, NEW_PASSWORD).andExpect(status().isGone());
        reset(new MockHttpSession(), newer, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anExpiredOrUnknownLinkDoesNothing() throws Exception {
        resetRepository.save(new PasswordReset(
                PasswordResetService.hash("expired-token"), user, admin, Instant.now().minusSeconds(1)));

        reset(new MockHttpSession(), "expired-token", NEW_PASSWORD).andExpect(status().isGone());
        reset(new MockHttpSession(), "unknown-token", NEW_PASSWORD).andExpect(status().isNotFound());
        login("person-c@example.test", TestUsers.PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aShortPasswordIsRejectedAndKeepsTheLinkUsable() throws Exception {
        String token = createLink(admin, user);

        reset(new MockHttpSession(), token, "too-short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("Das Passwort muss mindestens 12 Zeichen haben."));
        reset(new MockHttpSession(), token, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void onlyAdminsCreateLinksAndNeverMoreRightsThanTheyHave() throws Exception {
        TestUsers.mockMvcFor(context, user).perform(post("/api/users/{id}/password-reset", admin.getId()))
                .andExpect(status().isForbidden());
        anonymous.perform(post("/api/users/{id}/password-reset", user.getId()).with(csrf()))
                .andExpect(status().isUnauthorized());
        // An admin resets no other admin and nobody the system admin or themselves
        User otherAdmin = TestUsers.create(userService, "person-d@example.test", Role.ADMIN);
        MockMvc asAdmin = TestUsers.mockMvcFor(context, admin);
        asAdmin.perform(post("/api/users/{id}/password-reset", otherAdmin.getId()))
                .andExpect(status().isConflict());
        asAdmin.perform(post("/api/users/{id}/password-reset", systemAdmin.getId()))
                .andExpect(status().isConflict());
        asAdmin.perform(post("/api/users/{id}/password-reset", admin.getId()))
                .andExpect(status().isConflict());
        asAdmin.perform(post("/api/users/{id}/password-reset", 999_999))
                .andExpect(status().isNotFound());
        // The system admin may reset an admin
        TestUsers.mockMvcFor(context, systemAdmin).perform(post("/api/users/{id}/password-reset", otherAdmin.getId()))
                .andExpect(status().isCreated());
    }

    @Test
    void deletingAnAccountRemovesItsLinks() throws Exception {
        createLink(admin, user);

        TestUsers.mockMvcFor(context, user).perform(delete("/api/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\"}".formatted(TestUsers.PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(resetRepository.findAll()).isEmpty();
    }

    private String createLink(User creator, User target) throws Exception {
        return tokenOf(TestUsers.mockMvcFor(context, creator)
                .perform(post("/api/users/{id}/password-reset", target.getId()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private static String tokenOf(String body) {
        String link = JsonPath.read(body, "$.link");
        return link.substring(link.indexOf('=') + 1);
    }

    private ResultActions reset(MockHttpSession session, String token, String password) throws Exception {
        return anonymous.perform(post("/api/password-resets/{token}", token).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"%s\"}".formatted(password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return anonymous.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }
}
