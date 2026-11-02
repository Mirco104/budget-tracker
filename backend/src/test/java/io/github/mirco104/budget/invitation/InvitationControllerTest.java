package io.github.mirco104.budget.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.CategoryRepository;
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

/** US15: an admin creates a link; it can be used once within 7 days and creates a signed-in user. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvitationControllerTest {

    private static final String NEW_EMAIL = "person-d@example.test";
    private static final String NEW_PASSWORD = "test-only-password-d";

    @Autowired
    private MockMvc anonymous;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    private User admin;

    @BeforeEach
    void createAdmin() {
        admin = TestUsers.create(userService, "person-c@example.test", Role.ADMIN);
    }

    @Test
    void adminCreatesALinkValidForSevenDays() throws Exception {
        Instant before = Instant.now();

        String body = TestUsers.mockMvcFor(context, admin).perform(post("/api/invitations"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.link", matchesPattern("^http://localhost/\\?einladung=[A-Za-z0-9_-]{43}$")))
                .andReturn().getResponse().getContentAsString();

        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(Duration.between(before, expiresAt)).isBetween(Duration.ofDays(7).minusMinutes(1),
                Duration.ofDays(7).plusMinutes(1));
    }

    @Test
    void onlyAnAdminCreatesLinks() throws Exception {
        User user = TestUsers.create(userService, "person-b@example.test");

        TestUsers.mockMvcFor(context, user).perform(post("/api/invitations"))
                .andExpect(status().isForbidden());
        anonymous.perform(post("/api/invitations").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptingCreatesASignedInUserWithDefaultCategories() throws Exception {
        String token = createInvitation();
        MockHttpSession session = new MockHttpSession();

        accept(session, token, NEW_EMAIL, NEW_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(NEW_EMAIL))
                .andExpect(jsonPath("$.displayName").value("Person D"))
                .andExpect(jsonPath("$.role").value("USER"));

        anonymous.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NEW_EMAIL));
        User created = userRepository.findByEmail(NEW_EMAIL).orElseThrow();
        assertThat(categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(created)).hasSize(20);
        assertThat(created.getLastLoginAt()).isNotNull();
        assertThat(invitationRepository.findByToken(token).orElseThrow().getUsedAt()).isNotNull();
    }

    @Test
    void aLinkWorksOnlyOnce() throws Exception {
        String token = createInvitation();
        accept(new MockHttpSession(), token, NEW_EMAIL, NEW_PASSWORD).andExpect(status().isCreated());

        accept(new MockHttpSession(), token, "person-e@example.test", NEW_PASSWORD)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("Die Einladung ist abgelaufen oder wurde schon benutzt."));
    }

    @Test
    void anExpiredLinkIsGone() throws Exception {
        invitationRepository.save(new Invitation("expired-token", admin, Instant.now().minusSeconds(1)));

        accept(new MockHttpSession(), "expired-token", NEW_EMAIL, NEW_PASSWORD)
                .andExpect(status().isGone());
        assertThat(userRepository.findByEmail(NEW_EMAIL)).isEmpty();
    }

    @Test
    void anUnknownLinkIsNotFound() throws Exception {
        accept(new MockHttpSession(), "unknown-token", NEW_EMAIL, NEW_PASSWORD)
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsATakenEmailAndKeepsTheLinkUsable() throws Exception {
        String token = createInvitation();

        accept(new MockHttpSession(), token, "Person-C@example.test", NEW_PASSWORD)
                .andExpect(status().isConflict());
        accept(new MockHttpSession(), token, NEW_EMAIL, NEW_PASSWORD).andExpect(status().isCreated());
    }

    @Test
    void rejectsAShortPasswordAndAnInvalidEmail() throws Exception {
        String token = createInvitation();

        accept(new MockHttpSession(), token, NEW_EMAIL, "too-short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("Das Passwort muss mindestens 12 Zeichen haben."));
        accept(new MockHttpSession(), token, "no-email", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    private String createInvitation() throws Exception {
        String link = JsonPath.read(TestUsers.mockMvcFor(context, admin).perform(post("/api/invitations"))
                .andReturn().getResponse().getContentAsString(), "$.link");
        return link.substring(link.indexOf('=') + 1);
    }

    private ResultActions accept(MockHttpSession session, String token, String email, String password)
            throws Exception {
        return anonymous.perform(post("/api/invitations/{token}/accept", token).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "displayName": "Person D", "password": "%s"}
                        """.formatted(email, password)));
    }
}
