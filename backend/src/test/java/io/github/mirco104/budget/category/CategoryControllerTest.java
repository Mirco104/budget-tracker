package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class CategoryControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void signIn() {
        user = TestUsers.create(userService, "person-b@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
    }

    private static final String MONTH = "2026-10";

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryTargetRepository categoryTargetRepository;

    // --- GET /api/categories (US1) ---

    @Test
    void listsAllSeededCategoriesSortedBySortOrder() throws Exception {
        String json = getCategories(MONTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[0].name").value("Wohnen"))
                .andExpect(jsonPath("$[0].icon").value("home"))
                .andExpect(jsonPath("$[0].type").doesNotExist())
                .andExpect(jsonPath("$[0].system").doesNotExist())
                .andExpect(jsonPath("$[0].group").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        List<Integer> sortOrders = JsonPath.read(json, "$[*].sortOrder");
        assertThat(sortOrders).isSorted();
    }

    // --- PUT /api/categories/{id}/target (US2): success ---

    @Test
    void setsTargetAmountWithTwoDecimals() throws Exception {
        long id = categoryId("Wohnen");

        putTarget(id, "{\"targetAmount\": 750.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(content().string(containsString("\"targetAmount\":750.50")));
    }

    // Decision 36: a target is only an amount in euro; percent is an input helper in the frontend
    @Test
    void targetHasNoPercent() throws Exception {
        long id = categoryId("Freizeit");

        putTarget(id, "{\"targetAmount\": 100}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(100.00))
                .andExpect(jsonPath("$.targetPercent").doesNotExist());
        getCategories(MONTH).andExpect(jsonPath("$[0].targetPercent").doesNotExist());
    }

    @Test
    void clearsTargetWhenTheAmountIsNull() throws Exception {
        long id = categoryId("Urlaub");
        putTarget(id, "{\"targetAmount\": 200}").andExpect(status().isOk());

        putTarget(id, "{\"targetAmount\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(nullValue()));
    }

    @Test
    void acceptsBoundaryValues() throws Exception {
        long id = categoryId("Geschenke");

        putTarget(id, "{\"targetAmount\": 0}").andExpect(status().isOk());
        putTarget(id, "{\"targetAmount\": 9999999999.99}").andExpect(status().isOk());
    }

    @Test
    void persistsTarget() throws Exception {
        long id = categoryId("Mobilität");
        putTarget(id, "{\"targetAmount\": 120.25}").andExpect(status().isOk());

        getCategories(MONTH)
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetAmount").value(120.25));
    }

    // --- Month-dependent targets (US2, R3) ---

    @Test
    void targetAppliesFromItsMonthOnward() throws Exception {
        long id = categoryId("Lebensmittel");
        putTarget(id, "2026-11", "{\"targetAmount\": 300}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetValidFrom").value("2026-11"));

        getCategories("2026-10")
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetAmount", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetValidFrom", contains(nullValue())));
        getCategories("2026-12")
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetAmount", contains(300.00)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetValidFrom", contains("2026-11")));
    }

    @Test
    void newerTargetReplacesOlderOneWithoutChangingEarlierMonths() throws Exception {
        long id = categoryId("Lebensmittel");
        putTarget(id, "2026-11", "{\"targetAmount\": 300}").andExpect(status().isOk());
        putTarget(id, "2027-01", "{\"targetAmount\": 350}").andExpect(status().isOk());

        getCategories("2026-12")
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetAmount", contains(300.00)));
        getCategories("2027-02")
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetAmount", contains(350.00)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetValidFrom", contains("2027-01")));
    }

    @Test
    void savingAgainForTheSameMonthOverwritesTheEntry() throws Exception {
        long id = categoryId("Lebensmittel");
        putTarget(id, "2026-11", "{\"targetAmount\": 300}").andExpect(status().isOk());
        putTarget(id, "2026-11", "{\"targetAmount\": 320}").andExpect(status().isOk());

        assertThat(categoryTargetRepository.findAll()).hasSize(1);
        getCategories("2026-11")
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetAmount", contains(320.00)));
    }

    @Test
    void rejectsMissingMonth() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Parameter 'month' fehlt."));
        mockMvc.perform(put("/api/categories/{id}/target", categoryId("Wohnen"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Parameter 'month' fehlt."));
    }

    @Test
    void rejectsInvalidMonth() throws Exception {
        getCategories("2026-13")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ein Parameter hat ein ungültiges Format."));
        putTarget(categoryId("Wohnen"), "Oktober", "{\"targetAmount\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ein Parameter hat ein ungültiges Format."));
    }

    // --- 404 ---

    @Test
    void returns404ForUnknownCategory() throws Exception {
        putTarget(999_999L, "{\"targetAmount\": 10}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Nicht gefunden"))
                .andExpect(jsonPath("$.detail").value("Kategorie 999999 wurde nicht gefunden."));
    }

    // --- 400 ---

    @Test
    void rejectsNegativeTargetAmount() throws Exception {
        putTarget(categoryId("Wohnen"), "{\"targetAmount\": -1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"))
                .andExpect(jsonPath("$.errors[0].field").value("targetAmount"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Soll-Betrag darf nicht negativ sein."));
    }

    @Test
    void rejectsTargetAmountWithMoreThanTwoDecimals() throws Exception {
        putTarget(categoryId("Wohnen"), "{\"targetAmount\": 12.345}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetAmount"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Soll-Betrag darf höchstens 2 Nachkommastellen haben."));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        putTarget(categoryId("Wohnen"), "{\"targetAmount\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"))
                .andExpect(jsonPath("$.detail").value("Die Anfrage konnte nicht gelesen werden."));
    }

    @Test
    void rejectsNonNumericId() throws Exception {
        mockMvc.perform(put("/api/categories/abc/target")
                        .param("month", MONTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
    }

    // R3: every category can have a target, also one that is typically used for income
    @Test
    void setsTargetForAnIncomeCategory() throws Exception {
        long id = categoryId("Nebenjob");

        putTarget(id, "{\"targetAmount\": 10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(10.00));

        assertThat(categoryTargetRepository.findAll()).hasSize(1);
    }

    // --- incomeOnly (R24, decision 33) ---

    @Test
    void marksCategoriesWhoseEntriesAreAllPositive() throws Exception {
        postCreated("/api/transactions", """
                {"amount": 2000, "description": "Gehalt", "date": "2026-10-01", "categoryId": %d}
                """.formatted(categoryId("Gehalt/Lohn (netto)")));
        postCreated("/api/recurring-transactions", """
                {"amount": 300, "description": "Nebenjob", "categoryId": %d, "interval": "MONTHLY",
                 "startDate": "2026-01-15"}
                """.formatted(categoryId("Nebenjob")));
        // An expense and a refund: not only income
        postCreated("/api/transactions", """
                {"amount": -50, "description": "Einkauf", "date": "2026-10-02", "categoryId": %d}
                """.formatted(categoryId("Lebensmittel")));
        postCreated("/api/transactions", """
                {"amount": 20, "description": "Erstattung", "date": "2026-10-03", "categoryId": %d}
                """.formatted(categoryId("Lebensmittel")));
        // Positive transactions but a negative series
        postCreated("/api/transactions", """
                {"amount": 100, "description": "Rückzahlung", "date": "2026-10-04", "categoryId": %d}
                """.formatted(categoryId("Kredite")));
        postCreated("/api/recurring-transactions", """
                {"amount": -200, "description": "Rate", "categoryId": %d, "interval": "MONTHLY",
                 "startDate": "2026-01-01"}
                """.formatted(categoryId("Kredite")));

        getCategories(MONTH)
                .andExpect(status().isOk())
                .andExpect(incomeOnly("Gehalt/Lohn (netto)", true))
                .andExpect(incomeOnly("Nebenjob", true))
                .andExpect(incomeOnly("Lebensmittel", false))
                .andExpect(incomeOnly("Kredite", false))
                // Without entries a category is not "only income"
                .andExpect(incomeOnly("Sonstige Einnahmen", false));
    }

    @Test
    void anotherUsersEntriesDoNotCount() throws Exception {
        User other = TestUsers.create(userService, "person-c@example.test");
        long otherGehalt = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(other).stream()
                .filter(c -> c.getName().equals("Gehalt/Lohn (netto)"))
                .findFirst().orElseThrow().getId();
        TestUsers.mockMvcFor(context, other).perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 2000, "description": "Gehalt", "date": "2026-10-01", "categoryId": %d}
                                """.formatted(otherGehalt)))
                .andExpect(status().isCreated());

        getCategories(MONTH).andExpect(incomeOnly("Gehalt/Lohn (netto)", false));
    }

    // --- Fixed costs (R25, decision 36) ---

    @Test
    void listsTheFixedCostsOfTheMonth() throws Exception {
        long nebenkosten = categoryId("Nebenkosten");
        postCreated("/api/recurring-transactions", """
                {"amount": -165, "description": "Strom & Internet", "categoryId": %d, "interval": "MONTHLY",
                 "startDate": "2026-01-01"}
                """.formatted(nebenkosten));
        postCreated("/api/recurring-transactions", """
                {"amount": -55.08, "description": "Rundfunkbeitrag", "categoryId": %d, "interval": "QUARTERLY",
                 "startDate": "2026-01-15"}
                """.formatted(nebenkosten));
        // Income, a series that starts later and one that ended do not count
        postCreated("/api/recurring-transactions", """
                {"amount": 3000, "description": "Gehalt", "categoryId": %d, "interval": "MONTHLY",
                 "startDate": "2026-01-01"}
                """.formatted(categoryId("Gehalt/Lohn (netto)")));
        postCreated("/api/recurring-transactions", """
                {"amount": -50, "description": "Garage", "categoryId": %d, "interval": "MONTHLY",
                 "startDate": "2026-11-01"}
                """.formatted(categoryId("Wohnen")));
        postCreated("/api/recurring-transactions", """
                {"amount": -30, "description": "Stellplatz", "categoryId": %d, "interval": "MONTHLY",
                 "startDate": "2025-01-01", "endDate": "2026-09-30"}
                """.formatted(categoryId("Wohnen")));

        getCategories(MONTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Nebenkosten')].fixedCosts", contains(183.36)))
                .andExpect(jsonPath("$[?(@.name == 'Nebenkosten')].fixedCostItems[*].description",
                        contains("Strom & Internet", "Rundfunkbeitrag")))
                .andExpect(jsonPath("$[?(@.name == 'Nebenkosten')].fixedCostItems[1].amount", contains(55.08)))
                .andExpect(jsonPath("$[?(@.name == 'Nebenkosten')].fixedCostItems[1].interval", contains(3)))
                .andExpect(jsonPath("$[?(@.name == 'Nebenkosten')].fixedCostItems[1].monthly", contains(18.36)))
                .andExpect(jsonPath("$[?(@.name == 'Wohnen')].fixedCosts", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.name == 'Wohnen')].fixedCostItems[*]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.name == 'Gehalt/Lohn (netto)')].fixedCosts", contains(nullValue())));
        getCategories("2026-11")
                .andExpect(jsonPath("$[?(@.name == 'Wohnen')].fixedCosts", contains(50.00)));
    }

    @Test
    void anotherUsersSeriesAreNoFixedCosts() throws Exception {
        User other = TestUsers.create(userService, "person-c@example.test");
        long otherWohnen = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(other).stream()
                .filter(c -> c.getName().equals("Wohnen"))
                .findFirst().orElseThrow().getId();
        TestUsers.mockMvcFor(context, other).perform(post("/api/recurring-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -900, "description": "Miete", "categoryId": %d, "interval": "MONTHLY",
                                 "startDate": "2026-01-01"}
                                """.formatted(otherWohnen)))
                .andExpect(status().isCreated());

        getCategories(MONTH).andExpect(jsonPath("$[?(@.name == 'Wohnen')].fixedCosts", contains(nullValue())));
    }

    private void postCreated(String path, String body) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private static ResultMatcher incomeOnly(String name, boolean expected) {
        return jsonPath("$[?(@.name == '" + name + "')].incomeOnly", contains(expected));
    }

    private ResultActions getCategories(String month) throws Exception {
        return mockMvc.perform(get("/api/categories").param("month", month));
    }

    private ResultActions putTarget(long id, String body) throws Exception {
        return putTarget(id, MONTH, body);
    }

    private ResultActions putTarget(long id, String month, String body) throws Exception {
        return mockMvc.perform(put("/api/categories/{id}/target", id)
                .param("month", month)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long categoryId(String name) {
        return categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
