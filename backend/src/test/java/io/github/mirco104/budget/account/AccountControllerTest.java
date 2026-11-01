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
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.category.CategoryTargetRepository;
import io.github.mirco104.budget.invitation.InvitationRepository;
import io.github.mirco104.budget.recurring.RecurringExceptionRepository;
import io.github.mirco104.budget.recurring.RecurringTransactionRepository;
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
    private CategoryTargetRepository categoryTargetRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RecurringTransactionRepository recurringRepository;

    @Autowired
    private RecurringExceptionRepository exceptionRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    private User b;
    private User c;
    private MockMvc asB;
    private MockMvc asC;

    @BeforeEach
    void createUsersWithData() throws Exception {
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

    // --- GET /api/account/export ---

    @Test
    void exportsAllOwnDataAndNothingElse() throws Exception {
        asB.perform(get("/api/account/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("budget-export.json")))
                .andExpect(jsonPath("$.account.email").value(EMAIL))
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.categories.length()").value(20))
                .andExpect(jsonPath("$.categories[0].name").value("Wohnen"))
                .andExpect(jsonPath("$.categories[0].targets[0].validFrom").value(MONTH))
                .andExpect(jsonPath("$.categories[0].targets[0].targetAmount").value(500.0))
                .andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.transactions[0].description").value("Einkauf B"))
                .andExpect(jsonPath("$.transactions[0].category").value("Wohnen"))
                .andExpect(jsonPath("$.recurringTransactions.length()").value(1))
                .andExpect(jsonPath("$.recurringTransactions[0].interval").value("MONTHLY"))
                .andExpect(jsonPath("$.recurringTransactions[0].exceptions[0]").value("2026-11-01"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Einkauf C"))));
    }

    // --- DELETE /api/account ---

    @Test
    void rejectsDeletingWithAWrongPassword() throws Exception {
        asB.perform(delete("/api/account").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"wrong-password-123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        assertThat(userRepository.findById(b.getId())).isPresent();
    }

    @Test
    void deletesTheAccountWithAllDataAndEndsTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session, TestUsers.PASSWORD).andExpect(status().isNoContent());

        anonymous.perform(delete("/api/account").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\"}".formatted(TestUsers.PASSWORD)))
                .andExpect(status().isNoContent());

        anonymous.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(userRepository.findById(b.getId())).isEmpty();
        assertThat(categoryRepository.findAll()).noneMatch(category -> isOwnedByB(category.getId()));
        assertThat(transactionRepository.findAll()).extracting(t -> t.getDescription()).doesNotContain("Einkauf B");
        assertThat(invitationRepository.findAll()).isEmpty();
        assertThat(exceptionRepository.findAll()).hasSize(1);
        // C keeps everything
        assertThat(categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(c)).hasSize(20);
        assertThat(transactionRepository.findByOwnerOrderByDateAscIdAsc(c)).hasSize(1);
        assertThat(recurringRepository.findByOwnerOrderByStartDateAscIdAsc(c)).hasSize(1);
        assertThat(categoryTargetRepository.findByCategoryOwnerOrderByValidFromAsc(c)).hasSize(1);
        login(TestUsers.PASSWORD).andExpect(status().isUnauthorized());
    }

    private boolean isOwnedByB(Long categoryId) {
        return categoryRepository.findByIdAndOwner(categoryId, b).isPresent();
    }

    // A target, a transaction and a monthly series with one suspended occurrence (R13)
    private void createData(MockMvc mockMvc, User owner, String description) throws Exception {
        long wohnen = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(owner).getFirst().getId();
        mockMvc.perform(put("/api/categories/{id}/target", wohnen).param("month", MONTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetAmount\": 500}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -20, "description": "%s", "date": "2026-10-05", "categoryId": %d}
                        """.formatted(description, wohnen)))
                .andExpect(status().isCreated());
        String series = mockMvc.perform(post("/api/recurring-transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -900, "description": "Miete", "categoryId": %d, "interval": "MONTHLY",
                                 "startDate": "2026-10-01"}
                                """.formatted(wohnen)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long seriesId = ((Number) com.jayway.jsonpath.JsonPath.read(series, "$.id")).longValue();
        mockMvc.perform(delete("/api/recurring-transactions/{id}/occurrences/{date}", seriesId, "2026-11-01")
                        .param("scope", "THIS"))
                .andExpect(status().isNoContent());
    }

    private ResultActions changePassword(String current, String next) throws Exception {
        return asB.perform(put("/api/account/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, next)));
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
