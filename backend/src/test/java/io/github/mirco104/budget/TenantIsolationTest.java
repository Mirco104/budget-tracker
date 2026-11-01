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

    private MockMvc asB;
    private MockMvc asC;
    private User c;
    private long categoryOfB;
    private long transactionOfB;
    private long seriesOfB;

    @BeforeEach
    void createDataOfB() throws Exception {
        User b = TestUsers.create(userService, "person-b@example.test");
        c = TestUsers.create(userService, "person-c@example.test");
        asB = TestUsers.mockMvcFor(context, b);
        asC = TestUsers.mockMvcFor(context, c);

        categoryOfB = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(b).getFirst().getId();
        asB.perform(put("/api/categories/{id}/target", categoryOfB).param("month", MONTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetAmount\": 500}"))
                .andExpect(status().isOk());
        transactionOfB = idOf(asB.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .content(transaction(categoryOfB))));
        seriesOfB = idOf(asB.perform(post("/api/recurring-transactions").contentType(MediaType.APPLICATION_JSON)
                .content(series(categoryOfB))));
    }

    @Test
    void listsAndCalculationsShowOnlyOwnData() throws Exception {
        asC.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) categoryOfB))))
                .andExpect(jsonPath("$[*].targetAmount", not(hasItem(500.0))));
        asC.perform(get("/api/transactions").param("month", MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        asC.perform(get("/api/recurring-transactions").param("month", MONTH))
                .andExpect(jsonPath("$", empty()));
        asC.perform(get("/api/dashboard").param("month", MONTH))
                .andExpect(jsonPath("$.incomeTotal").value(0))
                .andExpect(jsonPath("$.categories", empty()));
        asC.perform(get("/api/reserves").param("month", "2026-11"))
                .andExpect(jsonPath("$.history", empty()));

        // Person B still sees all of it
        asB.perform(get("/api/transactions").param("month", MONTH))
                .andExpect(jsonPath("$.length()").value(2));
    }

    static Stream<Arguments> urlsWithResourceOfB() {
        return Stream.of(
                Arguments.of("PUT category", "category"),
                Arguments.of("PUT target", "target"),
                Arguments.of("DELETE category", "deleteCategory"),
                Arguments.of("PUT transaction", "transaction"),
                Arguments.of("DELETE transaction", "deleteTransaction"),
                Arguments.of("PUT occurrence THIS", "occurrenceThis"),
                Arguments.of("PUT occurrence FOLLOWING", "occurrenceFollowing"),
                Arguments.of("DELETE occurrence", "deleteOccurrence"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("urlsWithResourceOfB")
    void resourceOfAnotherUserInTheUrlIsNotFound(String name, String request) throws Exception {
        long ownCategory = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(c).getFirst().getId();
        MockHttpServletRequestBuilder builder = switch (request) {
            case "category" -> put("/api/categories/{id}", categoryOfB)
                    .content("{\"name\": \"Neu\", \"sortOrder\": 10, \"archived\": false}");
            case "target" -> put("/api/categories/{id}/target", categoryOfB).param("month", MONTH)
                    .content("{\"targetAmount\": 1}");
            case "deleteCategory" -> delete("/api/categories/{id}", categoryOfB);
            case "transaction" -> put("/api/transactions/{id}", transactionOfB).content(transaction(ownCategory));
            case "deleteTransaction" -> delete("/api/transactions/{id}", transactionOfB);
            case "occurrenceThis" -> put("/api/recurring-transactions/{id}/occurrences/{date}", seriesOfB, OCCURRENCE)
                    .param("scope", "THIS").content(transaction(ownCategory));
            case "occurrenceFollowing" -> put("/api/recurring-transactions/{id}/occurrences/{date}", seriesOfB,
                    OCCURRENCE).param("scope", "FOLLOWING").content(series(ownCategory));
            case "deleteOccurrence" -> delete("/api/recurring-transactions/{id}/occurrences/{date}", seriesOfB,
                    OCCURRENCE).param("scope", "THIS");
            default -> throw new IllegalArgumentException(request);
        };

        asC.perform(builder.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Nothing of B changed
        asB.perform(get("/api/transactions").param("month", MONTH))
                .andExpect(jsonPath("$.length()").value(2));
        asB.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[0].id").value(categoryOfB))
                .andExpect(jsonPath("$[0].targetAmount").value(500.0));
    }

    @Test
    void categoryOfAnotherUserInTheBodyIsInvalid() throws Exception {
        asC.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(transaction(categoryOfB)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        asC.perform(post("/api/recurring-transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(series(categoryOfB)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
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

        for (String url : new String[] {"/api/categories", "/api/transactions", "/api/recurring-transactions",
                "/api/dashboard", "/api/reserves", "/api/auth/me"}) {
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
        long ownCategory = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(c).getFirst().getId();

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
                {"amount": -50, "description": "Abo", "categoryId": %d, "interval": "MONTHLY",
                 "startDate": "2026-10-01"}
                """.formatted(categoryId);
    }

    private static long idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
        return id.longValue();
    }
}
