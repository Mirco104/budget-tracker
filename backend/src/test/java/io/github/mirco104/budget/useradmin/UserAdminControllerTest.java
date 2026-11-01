package io.github.mirco104.budget.useradmin;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.user.AppUserDetails;
import io.github.mirco104.budget.user.Role;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserRepository;
import io.github.mirco104.budget.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** US25: admins see all accounts and change roles; the system admin stays, nobody changes their own role. */
@SpringBootTest
@Transactional
class UserAdminControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private User systemAdmin;
    private User admin;
    private User user;
    private MockMvc asAdmin;

    @BeforeEach
    void createUsers() {
        // Created on startup from application-test.properties
        systemAdmin = userRepository.findByEmail("person-a@example.test").orElseThrow();
        admin = TestUsers.create(userService, "person-b@example.test", Role.ADMIN);
        user = TestUsers.create(userService, "person-c@example.test");
        asAdmin = TestUsers.mockMvcFor(context, admin);
    }

    @Test
    void listsAllAccountsWithoutFinancialData() throws Exception {
        asAdmin.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("person-a@example.test"))
                .andExpect(jsonPath("$[0].role").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$[*].email", hasItem("person-c@example.test")))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].lastLoginAt").hasJsonPath())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].categories").doesNotExist());
    }

    @Test
    void onlyAdminsSeeTheOverview() throws Exception {
        TestUsers.mockMvcFor(context, user).perform(get("/api/users"))
                .andExpect(status().isForbidden());
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
        TestUsers.mockMvcFor(context, systemAdmin).perform(get("/api/users"))
                .andExpect(status().isOk());
    }

    @Test
    void makesAUserAnAdminAndAnotherAdminAUser() throws Exception {
        User otherAdmin = TestUsers.create(userService, "person-d@example.test", Role.ADMIN);

        changeRole(asAdmin, user, "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        changeRole(asAdmin, otherAdmin, "USER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void theSystemAdminStaysSystemAdmin() throws Exception {
        changeRole(asAdmin, systemAdmin, "USER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Der Systemadmin bleibt immer Systemadmin."));
        changeRole(asAdmin, systemAdmin, "ADMIN").andExpect(status().isConflict());
    }

    @Test
    void theSystemAdminRoleCannotBeGiven() throws Exception {
        changeRole(asAdmin, user, "SYSTEM_ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("role"));
        asAdmin.perform(put("/api/users/{id}/role", user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"BOSS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nobodyChangesTheirOwnRole() throws Exception {
        changeRole(asAdmin, admin, "USER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die eigene Rolle kannst du nicht ändern."));
    }

    @Test
    void anUnknownAccountIsNotFound() throws Exception {
        asAdmin.perform(put("/api/users/{id}/role", 999_999).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"ADMIN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aUserCannotChangeRoles() throws Exception {
        changeRole(TestUsers.mockMvcFor(context, user), admin, "USER").andExpect(status().isForbidden());
    }

    // The session still holds the old role; the rights come from the database
    @Test
    void aRoleChangeTakesEffectOnTheNextRequest() throws Exception {
        MockMvc demotedWithOldSession = withSessionRole(admin, Role.ADMIN);
        MockMvc promotedWithOldSession = withSessionRole(user, Role.USER);
        MockMvc asSystemAdmin = TestUsers.mockMvcFor(context, systemAdmin);

        changeRole(asSystemAdmin, admin, "USER").andExpect(status().isOk());
        changeRole(asSystemAdmin, user, "ADMIN").andExpect(status().isOk());

        demotedWithOldSession.perform(get("/api/users")).andExpect(status().isForbidden());
        demotedWithOldSession.perform(post("/api/invitations")).andExpect(status().isForbidden());
        promotedWithOldSession.perform(get("/api/users")).andExpect(status().isOk());
        promotedWithOldSession.perform(post("/api/invitations")).andExpect(status().isCreated());
    }

    private MockMvc withSessionRole(User account, Role sessionRole) {
        AppUserDetails details = new AppUserDetails(account.getId(), account.getEmail(), account.getPasswordHash(),
                sessionRole);
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(user(details)).with(csrf()))
                .build();
    }

    private ResultActions changeRole(MockMvc mockMvc, User target, String role) throws Exception {
        return mockMvc.perform(put("/api/users/{id}/role", target.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"%s\"}".formatted(role)));
    }
}
