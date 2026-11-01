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

    // --- GET /api/dashboard (US5) ---

    @Test
    void reflectsTargetsAndTransactionsOfTheMonth() throws Exception {
        long wohnen = categoryId("Wohnen");
        putTarget(wohnen, "2026-10", "{\"targetAmount\": 800}");
        postTransaction("2000", "Gehalt", "2026-10-01", categoryId("Gehalt/Lohn (netto)"));
        postTransaction("-750", "Miete", "2026-10-03", wohnen);
        postTransaction("-99", "Miete September", "2026-09-30", wohnen);

        mockMvc.perform(get("/api/dashboard").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.incomeTotal").value(2000.00))
                .andExpect(jsonPath("$.categories[?(@.name == 'Wohnen')].target", contains(800.00)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Wohnen')].actual", contains(750.00)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Wohnen')].difference", contains(50.00)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Sonstiges (berechneter Rest)')]", hasSize(0)))
                .andExpect(jsonPath("$.expenses.target").value(800.00))
                .andExpect(jsonPath("$.expenses.actual").value(750.00))
                .andExpect(jsonPath("$.expenses.difference").value(50.00))
                .andExpect(jsonPath("$.unplanned").value(1200.00))
                .andExpect(jsonPath("$.unplannedLabel").value("Nicht verplant"))
                .andExpect(jsonPath("$.surplus").value(1250.00))
                .andExpect(jsonPath("$.surplusLabel").value("Monatlicher Überschuss"))
                .andExpect(jsonPath("$.surplusShare").value(0.625))
                .andExpect(jsonPath("$.groups").doesNotExist())
                .andExpect(jsonPath("$.expenses.recurring").value(0.00))
                .andExpect(jsonPath("$.expenses.oneOff").value(750.00))
                .andExpect(jsonPath("$.expenses.oneOffShare").value(0.375))
                .andExpect(jsonPath("$.income[?(@.name == 'Gehalt/Lohn (netto)')].share", contains(1.0)));
    }

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

    @Test
    void showsEmptyMonthWithoutErrors() throws Exception {
        mockMvc.perform(get("/api/dashboard").param("month", "2030-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomeTotal").value(0.00))
                .andExpect(jsonPath("$.expenses.actual").value(0.00))
                .andExpect(jsonPath("$.surplus").value(0.00))
                .andExpect(jsonPath("$.surplusShare").doesNotExist())
                .andExpect(jsonPath("$.categories", hasSize(0)))
                .andExpect(jsonPath("$.expenses.recurring").value(0.00))
                .andExpect(jsonPath("$.expenses.oneOff").value(0.00))
                .andExpect(jsonPath("$.income", hasSize(0)));
    }

    // --- Reference fixture F1 (spec section 12), end to end with recurring transactions (R1, R12, R13) ---

    @Test
    void referenceFixtureF1MatchesSpec() throws Exception {
        putTarget(categoryId("Wohnen"), "2026-10", "{\"targetAmount\": 900}");
        putTarget(categoryId("Nebenkosten"), "2026-10", "{\"targetPercent\": 0.10}");
        putTarget(categoryId("Versicherungen"), "2026-10", "{\"targetAmount\": 100}");
        putTarget(categoryId("Mobilität"), "2026-10", "{\"targetAmount\": 60, \"targetPercent\": 0.50}");
        putTarget(categoryId("Lebensmittel"), "2026-10", "{\"targetAmount\": 400}");
        putTarget(categoryId("Freizeit"), "2026-10", "{\"targetPercent\": 0.05}");
        putTarget(categoryId("Investitionen"), "2026-10", "{\"targetPercent\": 0.20}");

        postTransaction("3000.00", "Gehalt", "2026-10-01", categoryId("Gehalt/Lohn (netto)"));
        postTransaction("200.00", "Nebenjob", "2026-10-10", categoryId("Nebenjob"));
        postTransaction("-200.00", "Strom", "2026-10-02", categoryId("Nebenkosten"));
        postTransaction("-140.00", "Wasser", "2026-10-03", categoryId("Nebenkosten"));
        postTransaction("-120.30", "Einkauf 1", "2026-10-04", categoryId("Lebensmittel"));
        postTransaction("-200.20", "Einkauf 2", "2026-10-11", categoryId("Lebensmittel"));
        postTransaction("-130.00", "Einkauf 3", "2026-10-18", categoryId("Lebensmittel"));
        postTransaction("30.00", "Erstattung", "2026-10-19", categoryId("Lebensmittel"));
        postTransaction("-45.99", "Tanken", "2026-10-06", categoryId("Mobilität"));
        postTransaction("-640.00", "Sparplan", "2026-10-15", categoryId("Investitionen"));
        postTransaction("-20.00", "Geschenk", "2026-10-20", categoryId("Geschenke"));
        postTransaction("-999.00", "Einkauf September", "2026-09-30", categoryId("Lebensmittel"));

        postSeries("-900.00", "Miete", categoryId("Wohnen"), "MONTHLY", "2026-01-01");
        postSeries("-240.00", "Haftpflicht", categoryId("Versicherungen"), "YEARLY", "2026-10-15");
        long streaming = postSeries("-15.00", "Streaming", categoryId("Freizeit"), "MONTHLY", "2026-01-05");
        mockMvc.perform(delete("/api/recurring-transactions/{id}/occurrences/{date}", streaming, "2026-10-05")
                        .param("scope", "THIS"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/dashboard").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomeTotal").value(3200.00))
                .andExpect(line("Wohnen", "target", 900.00)).andExpect(line("Wohnen", "actual", 900.00))
                .andExpect(line("Wohnen", "difference", 0.00))
                .andExpect(line("Nebenkosten", "target", 320.00)).andExpect(line("Nebenkosten", "actual", 340.00))
                .andExpect(line("Nebenkosten", "difference", -20.00))
                .andExpect(line("Versicherungen", "target", 100.00))
                .andExpect(line("Versicherungen", "actual", 240.00))
                .andExpect(line("Versicherungen", "difference", -140.00))
                .andExpect(line("Mobilität", "target", 60.00)).andExpect(line("Mobilität", "actual", 45.99))
                .andExpect(line("Mobilität", "difference", 14.01))
                .andExpect(line("Lebensmittel", "target", 400.00))
                .andExpect(line("Lebensmittel", "actual", 420.50))
                .andExpect(line("Lebensmittel", "difference", -20.50))
                .andExpect(line("Freizeit", "target", 160.00)).andExpect(line("Freizeit", "actual", 0.00))
                .andExpect(line("Freizeit", "difference", 160.00))
                .andExpect(line("Geschenke", "target", null)).andExpect(line("Geschenke", "actual", 20.00))
                .andExpect(line("Geschenke", "difference", null))
                .andExpect(jsonPath("$.categories[?(@.name == 'Kredite')]", hasSize(0)))
                .andExpect(line("Investitionen", "target", 640.00))
                .andExpect(line("Investitionen", "actual", 640.00))
                .andExpect(line("Investitionen", "difference", 0.00))
                .andExpect(jsonPath("$.expenses.target").value(2580.00))
                .andExpect(jsonPath("$.expenses.actual").value(2606.49))
                .andExpect(jsonPath("$.expenses.difference").value(-26.49))
                .andExpect(jsonPath("$.unplanned").value(620.00))
                .andExpect(jsonPath("$.unplannedLabel").value("Nicht verplant"))
                .andExpect(jsonPath("$.surplus").value(593.51))
                .andExpect(jsonPath("$.surplusLabel").value("Monatlicher Überschuss"))
                .andExpect(jsonPath("$.surplusShare").value(0.1855))
                .andExpect(jsonPath("$.income[?(@.name == 'Gehalt/Lohn (netto)')].share", contains(0.9375)))
                .andExpect(jsonPath("$.income[?(@.name == 'Nebenjob')].share", contains(0.0625)))
                .andExpect(jsonPath("$.expenses.recurring").value(1140.00))
                .andExpect(jsonPath("$.expenses.oneOff").value(1466.49))
                .andExpect(jsonPath("$.expenses.recurringShare").value(0.3563))
                .andExpect(jsonPath("$.expenses.oneOffShare").value(0.4583));

        // An occurrence counts only in its own month (R1): November has rent and streaming, no insurance
        mockMvc.perform(get("/api/dashboard").param("month", "2026-11"))
                .andExpect(line("Wohnen", "actual", 900.00))
                .andExpect(line("Versicherungen", "actual", 0.00))
                .andExpect(line("Freizeit", "actual", 15.00));
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

    private void putTarget(long categoryId, String month, String body) throws Exception {
        mockMvc.perform(put("/api/categories/{id}/target", categoryId)
                        .param("month", month)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
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

    private long postSeries(String amount, String description, long categoryId, String interval, String startDate)
            throws Exception {
        String json = mockMvc.perform(post("/api/recurring-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": %s, "description": "%s", "categoryId": %d, "interval": "%s",
                                 "startDate": "%s"}
                                """.formatted(amount, description, categoryId, interval, startDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static ResultMatcher line(String name, String field, Double expected) {
        String path = "$.categories[?(@.name == '" + name + "')]." + field;
        return expected == null ? jsonPath(path, contains(nullValue())) : jsonPath(path, contains(expected));
    }

    private long categoryId(String name) {
        return categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
