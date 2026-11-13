package io.github.mirco104.budget;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.bankaccount.BankAccountRepository;
import io.github.mirco104.budget.category.CategoryOrder;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.user.AppUserDetails;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * R15: Person C sees none of Person B's data. Another user's resource in the URL is 404, a reference in the body is
 * 400; without a session every call is 401, a writing call without CSRF token is 403.
 */
@SpringBootTest
@Transactional
class TenantIsolationTest {

    private static final String MONTH = "2026-10";
    private static final String OCCURRENCE = "2026-10-01";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    private MockMvc asB;
    private MockMvc asC;
    private User c;
    private long categoryOfB;
    private long transactionOfB;
    private long seriesOfB;
    private long accountOfB;

    @BeforeEach
    void createDataOfB() throws Exception {
        User b = TestUsers.create(userService, "person-b@example.test");
        c = TestUsers.create(userService, "person-c@example.test");
        asB = TestUsers.mockMvcFor(context, b);
        asC = TestUsers.mockMvcFor(context, c);

        categoryOfB = categoryRepository.findAllByOwner(b).stream().min(CategoryOrder.ALPHABETICAL).orElseThrow().getId();
        accountOfB = bankAccountRepository.findAllOrdered(b).getFirst().getId();
        transactionOfB = idOf(asB.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .content(transaction(categoryOfB))));
        seriesOfB = idOf(asB.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON)
                .content(series(categoryOfB))));
    }

    @Test
    void listsAndCalculationsShowOnlyOwnData() throws Exception {
        asC.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) categoryOfB))));
        asC.perform(get("/api/transactions").param("month", MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        asC.perform(get("/api/plan").param("month", MONTH))
                .andExpect(jsonPath("$.items", empty()));
        asC.perform(get("/api/dashboard").param("month", MONTH))
                .andExpect(jsonPath("$.incomeTotal").value(0))
                .andExpect(jsonPath("$.categories", empty()));
        asC.perform(get("/api/reserves").param("month", "2026-11"))
                .andExpect(jsonPath("$.reserves", empty()));
        asC.perform(get("/api/history").param("from", "2026-01").param("to", "2026-09"))
                .andExpect(jsonPath("$.months").value(0))
                .andExpect(jsonPath("$.history", empty()));

        // Person B still sees all of it; planned items are no bookings (P1)
        asB.perform(get("/api/transactions").param("month", MONTH))
                .andExpect(jsonPath("$.length()").value(1));
        asB.perform(get("/api/plan").param("month", MONTH))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    static Stream<Arguments> urlsWithResourceOfB() {
        return Stream.of(
                Arguments.of("PUT category", "category"),
                Arguments.of("DELETE category", "deleteCategory"),
                Arguments.of("PUT transaction", "transaction"),
                Arguments.of("DELETE transaction", "deleteTransaction"),
                Arguments.of("PUT planned item", "item"),
                Arguments.of("DELETE planned item", "deleteItem"),
                Arguments.of("PUT bank account", "bankAccount"),
                Arguments.of("DELETE bank account", "deleteBankAccount"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("urlsWithResourceOfB")
    void resourceOfAnotherUserInTheUrlIsNotFound(String name, String request) throws Exception {
        long ownCategory = categoryRepository.findAllByOwner(c).stream().min(CategoryOrder.ALPHABETICAL).orElseThrow().getId();
        MockHttpServletRequestBuilder builder = switch (request) {
            case "category" -> put("/api/categories/{id}", categoryOfB)
                    .content("{\"name\": \"Neu\", \"sortOrder\": 10, \"archived\": false}");
            case "deleteCategory" -> delete("/api/categories/{id}", categoryOfB);
            case "transaction" -> put("/api/transactions/{id}", transactionOfB).content(transaction(ownCategory));
            case "deleteTransaction" -> delete("/api/transactions/{id}", transactionOfB);
            case "item" -> put("/api/planned-items/{id}", seriesOfB).content(series(ownCategory));
            case "deleteItem" -> delete("/api/planned-items/{id}", seriesOfB);
            case "bankAccount" -> put("/api/bank-accounts/{id}", accountOfB)
                    .content("{\"name\": \"Neu\", \"archived\": false, \"defaultAccount\": true}");
            case "deleteBankAccount" -> delete("/api/bank-accounts/{id}", accountOfB);
            default -> throw new IllegalArgumentException(request);
        };

        asC.perform(builder.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Nothing of B changed
        asB.perform(get("/api/transactions").param("month", MONTH))
                .andExpect(jsonPath("$.length()").value(1));
        asB.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[0].id").value(categoryOfB));
        asB.perform(get("/api/plan").param("month", MONTH))
                .andExpect(jsonPath("$.items[0].name").value("Abo"));
    }

    @Test
    void categoryOfAnotherUserInTheBodyIsInvalid() throws Exception {
        asC.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(transaction(categoryOfB)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        asC.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON)
                        .content(series(categoryOfB)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    // R26: another user's bank account in the body is 400 on accountId
    @Test
    void bankAccountOfAnotherUserInTheBodyIsInvalid() throws Exception {
        long ownCategory = categoryRepository.findAllByOwner(c).getFirst().getId();
        asC.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(transaction(ownCategory).replace("}", ", \"accountId\": " + accountOfB + "}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("accountId"));
        asC.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON)
                        .content(series(ownCategory).replace("}", ", \"accountId\": " + accountOfB + "}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("accountId"));
        asC.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id", not(org.hamcrest.Matchers.equalTo((int) accountOfB))));
    }

    @Test
    void sameCategoryNameIsAllowedForAnotherUser() throws Exception {
        asB.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Haustier\"}"))
                .andExpect(status().isCreated());
        asC.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Haustier\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void everyApiCallWithoutSessionIsUnauthorized() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        for (String url : new String[] {"/api/categories", "/api/transactions", "/api/plan",
                "/api/dashboard", "/api/reserves", "/api/history", "/api/auth/me", "/api/bank-accounts", "/api/booking-suggestion", "/api/imports"}) {
            anonymous.perform(get(url).param("month", MONTH))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
        anonymous.perform(post("/api/transactions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(transaction(categoryOfB)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writingCallWithoutCsrfTokenIsForbidden() throws Exception {
        MockMvc withoutCsrf = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(user(AppUserDetails.from(c))))
                .build();
        long ownCategory = categoryRepository.findAllByOwner(c).stream().min(CategoryOrder.ALPHABETICAL).orElseThrow().getId();

        withoutCsrf.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(transaction(ownCategory)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        withoutCsrf.perform(get("/api/transactions").param("month", MONTH)).andExpect(status().isOk());
    }

    private static String transaction(long categoryId) {
        return """
                {"amount": -20, "description": "Einkauf", "date": "2026-10-05", "categoryId": %d}
                """.formatted(categoryId);
    }

    private static String series(long categoryId) {
        return """
                {"name": "Abo", "amount": -50, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                 "anchorDate": "2026-10-01", "entryMode": "MANUAL", "validFrom": "2026-10"}
                """.formatted(categoryId);
    }

    private static long idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
        return id.longValue();
    }
}
