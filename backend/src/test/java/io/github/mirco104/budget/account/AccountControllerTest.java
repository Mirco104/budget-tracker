package io.github.mirco104.budget.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.auth.LoginAttempts;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.invitation.InvitationRepository;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
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
import org.springframework.web.context.WebApplicationContext;

/** US16: change the password, export all own data, delete the account with all data. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest {

    private static final String EMAIL = "person-b@example.test";
    private static final String MONTH = "2026-10";

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
    private TransactionRepository transactionRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private LoginAttempts loginAttempts;

    private User b;
    private User c;
    private MockMvc asB;
    private MockMvc asC;

    @BeforeEach
    void createUsersWithData() throws Exception {
        // The login lock lives in memory and is shared with other test classes in the same context;
        // the sign-ins with the old password below count as failures (by e-mail and by the 127.0.0.1 test IP)
        loginAttempts.reset(EMAIL);
        loginAttempts.resetIp("127.0.0.1");
        // B is an admin, so the delete also covers the invitations B created
        b = TestUsers.create(userService, EMAIL, Role.ADMIN);
        c = TestUsers.create(userService, "person-c@example.test");
        asB = TestUsers.mockMvcFor(context, b);
        asC = TestUsers.mockMvcFor(context, c);
        createData(asB, b, "Einkauf B");
        createData(asC, c, "Einkauf C");
        asB.perform(post("/api/invitations")).andExpect(status().isCreated());
    }

    // --- PUT /api/account/password ---

    @Test
    void changesThePasswordSoOnlyTheNewOneSignsIn() throws Exception {
        changePassword(TestUsers.PASSWORD, "new-test-password").andExpect(status().isNoContent());

        login("new-test-password").andExpect(status().isNoContent());
        login(TestUsers.PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAWrongCurrentPasswordAndAShortNewOne() throws Exception {
        changePassword("wrong-password-123", "new-test-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("Das Passwort ist falsch."));
        changePassword(TestUsers.PASSWORD, "too-short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    // --- PUT /api/account/display-name ---

    @Test
    void changesTheDisplayNameWithoutPassword() throws Exception {
        asB.perform(put("/api/account/display-name").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"  Person B neu  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Person B neu"));

        assertThat(userRepository.findById(b.getId()).orElseThrow().getDisplayName()).isEqualTo("Person B neu");
    }

    @Test
    void rejectsAnEmptyOrTooLongDisplayName() throws Exception {
        asB.perform(put("/api/account/display-name").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("displayName"));
        asB.perform(put("/api/account/display-name").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"%s\"}".formatted("x".repeat(51))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Der Anzeigename darf höchstens 50 Zeichen haben."));
    }

    // --- PUT /api/account/email ---

    @Test
    void changesTheEmailSoTheNewOneSignsIn() throws Exception {
        changeEmail("Person-B-Neu@Example.test", TestUsers.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("person-b-neu@example.test"));

        loginAs("person-b-neu@example.test", TestUsers.PASSWORD).andExpect(status().isNoContent());
        login(TestUsers.PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAWrongPasswordATakenAndAnInvalidEmail() throws Exception {
        changeEmail("person-b-neu@example.test", "wrong-password-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        changeEmail("Person-C@example.test", TestUsers.PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese E-Mail-Adresse ist schon registriert."));
        changeEmail("no-email", TestUsers.PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        // The own address again is fine
        changeEmail(EMAIL, TestUsers.PASSWORD).andExpect(status().isOk());
    }

    // --- GET /api/account/export ---

    // --- DELETE /api/account ---

    @Test
    void rejectsDeletingWithAWrongPassword() throws Exception {
        asB.perform(delete("/api/account").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"wrong-password-123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        assertThat(userRepository.findById(b.getId())).isPresent();
    }

    // US25: only ADMIN_EMAIL sets the system admin's e-mail
    @Test
    void theSystemAdminCannotChangeTheEmail() throws Exception {
        User systemAdmin = userRepository.findByEmail("person-a@example.test").orElseThrow();

        TestUsers.mockMvcFor(context, systemAdmin).perform(put("/api/account/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin-neu@example.test\", \"password\": \"%s\"}"
                                .formatted(TestUsers.PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die E-Mail des Systemadmins wird über ADMIN_EMAIL festgelegt."));
    }

    // US25: there is always one system admin
    @Test
    void theSystemAdminCannotDeleteTheAccount() throws Exception {
        User systemAdmin = userRepository.findByEmail("person-a@example.test").orElseThrow();

        TestUsers.mockMvcFor(context, systemAdmin).perform(delete("/api/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\"}".formatted(TestUsers.PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Der Systemadmin kann sein Konto nicht löschen."));
        assertThat(userRepository.findById(systemAdmin.getId())).isPresent();
    }

    private boolean isOwnedByB(Long categoryId) {
        return categoryRepository.findByIdAndOwner(categoryId, b).isPresent();
    }

    // A transaction and a monthly planned item (section 19)
    private void createData(MockMvc mockMvc, User owner, String description) throws Exception {
        long wohnen = categoryRepository.findAllByOwner(owner).stream()
                .filter(category -> category.getName().equals("Wohnen")).findFirst().orElseThrow().getId();
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -20, "description": "%s", "date": "2026-10-05", "categoryId": %d}
                        """.formatted(description, wohnen)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Miete", "amount": -900, "categoryId": %d, "rhythmUnit": "MONTH",
                                 "rhythmCount": 1, "anchorDate": "2026-10-01", "validFrom": "2026-10"}
                                """.formatted(wohnen)))
                .andExpect(status().isCreated());
    }

    private ResultActions changePassword(String current, String next) throws Exception {
        return asB.perform(put("/api/account/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, next)));
    }

    private ResultActions changeEmail(String email, String password) throws Exception {
        return asB.perform(put("/api/account/email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions loginAs(String email, String password) throws Exception {
        return anonymous.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions login(String password) throws Exception {
        return login(new MockHttpSession(), password);
    }

    private ResultActions login(MockHttpSession session, String password) throws Exception {
        return anonymous.perform(post("/api/auth/login").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, password)));
    }
}
