package io.github.mirco104.budget.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.user.UserService;
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

/** US14: sign in and out with e-mail and password, generic error, lock after failures. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    private static final String EMAIL = "person-b@example.test";
    private static final String WRONG = "E-Mail oder Passwort ist falsch.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginAttempts loginAttempts;

    @BeforeEach
    void createUser() {
        TestUsers.create(userService, EMAIL);
        loginAttempts.reset(EMAIL);
        loginAttempts.reset("unknown@example.test");
    }

    @Test
    void signsInWithANewSessionAndShowsTheUser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();

        login(session, " Person-B@Example.test ", TestUsers.PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("person-b"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        org.assertj.core.api.Assertions.assertThat(session.getId()).isNotEqualTo(before);
    }

    @Test
    void rejectsWrongPasswordAndUnknownEmailWithTheSameText() throws Exception {
        login(new MockHttpSession(), EMAIL, "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(WRONG));
        login(new MockHttpSession(), "unknown@example.test", TestUsers.PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(WRONG));
    }

    @Test
    void locksTheEmailAfterFiveFailuresEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(new MockHttpSession(), EMAIL, "wrong-password-123").andExpect(status().isUnauthorized());
        }

        login(new MockHttpSession(), EMAIL, TestUsers.PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Anmeldung gesperrt"));
    }

    @Test
    void rejectsLoginWithoutEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\", \"password\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void logsOut() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session, EMAIL, TestUsers.PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void answers401WithoutSession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Nicht angemeldet"));
    }

    @Test
    void rejectsLoginWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, TestUsers.PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthNeedsNoSession() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    private ResultActions login(MockHttpSession session, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }
}
