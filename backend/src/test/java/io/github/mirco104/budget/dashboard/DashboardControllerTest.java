package io.github.mirco104.budget.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class DashboardControllerTest {

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

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private Clock clock;

    // --- GET /api/dashboard (US5) ---

    // P3: the plan of a category is the sum of its planned items; a change applies from its month (section 19.3)
    @Test
    void appliesTargetFromItsMonthOnwardUntilANewerOneReplacesIt() throws Exception {
        long wohnen = categoryId("Wohnen");
        putTarget(wohnen, "2026-11", "{\"targetAmount\": 800}");
        putTarget(wohnen, "2027-01", "{\"targetAmount\": 850}");

        assertWohnenTarget("2026-10", null);
        assertWohnenTarget("2026-11", 800.00);
        assertWohnenTarget("2026-12", 800.00);
        assertWohnenTarget("2027-01", 850.00);
        assertWohnenTarget("2027-06", 850.00);
    }

    @Test
    void refundLowersActualOfItsCategoryAndExpenseInIncomeCategoryIsListed() throws Exception {
        postTransaction("2000", "Gehalt", "2026-10-01", categoryId("Gehalt/Lohn (netto)"));
        postTransaction("-100", "Einkauf", "2026-10-02", categoryId("Lebensmittel"));
        postTransaction("30", "Erstattung", "2026-10-03", categoryId("Lebensmittel"));
        postTransaction("-20", "Gebühr", "2026-10-04", categoryId("Nebenjob"));

        mockMvc.perform(get("/api/dashboard").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomeTotal").value(2000.00))
                .andExpect(jsonPath("$.categories[?(@.name == 'Lebensmittel')].actual", contains(70.00)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Nebenjob')].actual", contains(20.00)))
                .andExpect(jsonPath("$.expenses.actual").value(90.00))
                .andExpect(jsonPath("$.income[*].name", contains("Gehalt/Lohn (netto)")));
    }

    // --- Ø income and labels relative to today (R7, R23, decision 26 and 27) ---

    @Test
    void averageAndMinimumOfTheCurrentMonthUseTheIncomeOfTheMonthsBefore() throws Exception {
        YearMonth current = YearMonth.now(clock);
        long gehalt = categoryId("Gehalt/Lohn (netto)");
        // Only bookings count (P1)
        for (int k = 1; k <= 3; k++) {
            postTransaction("2000.00", "Gehalt", current.minusMonths(k).atDay(1).toString(), gehalt);
        }
        postTransaction("600.00", "Nachhilfe", current.minusMonths(2).atDay(5).toString(), categoryId("Nebenjob"));
        // A bonus in the current month does not count for Ē and Emin (R23)
        postTransaction("5000.00", "Bonus", current.atDay(2).toString(), gehalt);
        putTarget(categoryId("Lebensmittel"), current.toString(), "{\"targetAmount\": 200}");

        mockMvc.perform(get("/api/dashboard").param("month", current.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthState").value("CURRENT"))
                // Only the bonus is booked in the current month (P1)
                .andExpect(jsonPath("$.incomeTotal").value(5000.00))
                .andExpect(jsonPath("$.averageIncome").value(2200.00))
                .andExpect(jsonPath("$.minimumIncome").value(2000.00))
                .andExpect(jsonPath("$.averageIncomeMonths").value(3))
                .andExpect(jsonPath("$.averageIncomeFrom").value(current.minusMonths(3).toString()))
                .andExpect(jsonPath("$.averageIncomeTo").value(current.minusMonths(1).toString()))
                .andExpect(line("Lebensmittel", "target", 200.00))
                .andExpect(jsonPath("$.unplanned").value(2000.00))
                .andExpect(jsonPath("$.unplannedMinimum").value(1800.00))
                .andExpect(jsonPath("$.surplusLabel").value("Noch frei"));

        // A future month uses only the months before today's month as well
        mockMvc.perform(get("/api/dashboard").param("month", current.plusMonths(2).toString()))
                .andExpect(jsonPath("$.monthState").value("FUTURE"))
                .andExpect(jsonPath("$.averageIncome").value(2200.00))
                .andExpect(jsonPath("$.averageIncomeTo").value(current.minusMonths(1).toString()));
    }

    @Test
    void withoutAnEarlierMonthUnplannedIsEmptyAndTargetsStay() throws Exception {
        YearMonth current = YearMonth.now(clock);
        postTransaction("2000.00", "Gehalt", current.atDay(1).toString(), categoryId("Gehalt/Lohn (netto)"));
        putTarget(categoryId("Lebensmittel"), current.toString(), "{\"targetAmount\": 200}");
        putTarget(categoryId("Wohnen"), current.toString(), "{\"targetAmount\": 800}");

        mockMvc.perform(get("/api/dashboard").param("month", current.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageIncome").value(nullValue()))
                .andExpect(jsonPath("$.averageIncomeMonths").value(0))
                .andExpect(jsonPath("$.unplanned").value(nullValue()))
                .andExpect(jsonPath("$.minimumIncome").value(nullValue()))
                .andExpect(jsonPath("$.unplannedMinimum").value(nullValue()))
                .andExpect(line("Lebensmittel", "target", 200.00))
                .andExpect(line("Wohnen", "target", 800.00));
    }

    // --- GET /api/dashboard (US5): validation ---

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "Oktober", ""})
    void rejectsInvalidMonth(String month) throws Exception {
        mockMvc.perform(get("/api/dashboard").param("month", month))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
    }

    @Test
    void rejectsMissingMonth() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"))
                .andExpect(jsonPath("$.detail").value("Der Parameter 'month' fehlt."));
    }

    private final java.util.Map<Long, Long> budgetItems = new java.util.HashMap<>();

    // A budget for variable costs (P4); a second call for the same category changes it from the month on
    private void putTarget(long categoryId, String month, String body) throws Exception {
        Number amount = JsonPath.read(body, "$.targetAmount");
        String item = """
                {"name": "Budget", "amount": -%s, "categoryId": %d, "validFrom": "%s"}
                """.formatted(amount, categoryId, month);
        Long id = budgetItems.get(categoryId);
        if (id == null) {
            String json = mockMvc.perform(post("/api/planned-items")
                            .contentType(MediaType.APPLICATION_JSON).content(item))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            budgetItems.put(categoryId, ((Number) JsonPath.read(json, "$.id")).longValue());
        } else {
            mockMvc.perform(put("/api/planned-items/{id}", id).contentType(MediaType.APPLICATION_JSON).content(item))
                    .andExpect(status().isOk());
        }
    }

    private void assertWohnenTarget(String month, Double expected) throws Exception {
        List<Object> targets = JsonPath.read(
                mockMvc.perform(get("/api/dashboard").param("month", month))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.categories[?(@.name == 'Wohnen')].target");
        // Without a target and without expenses the category is not listed (R10)
        if (expected == null) {
            assertThat(targets).as("target in %s", month).isEmpty();
        } else {
            assertThat(targets).as("target in %s", month).containsExactly(expected);
        }
    }

    private void postTransaction(String amount, String description, String date, long categoryId)
            throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": %s, "description": "%s", "date": "%s", "categoryId": %d}
                                """.formatted(amount, description, date, categoryId)))
                .andExpect(status().isCreated());
    }

    private static ResultMatcher line(String name, String field, Double expected) {
        String path = "$.categories[?(@.name == '" + name + "')]." + field;
        return expected == null ? jsonPath(path, contains(nullValue())) : jsonPath(path, contains(expected));
    }

    private long categoryId(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
