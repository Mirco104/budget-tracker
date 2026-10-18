package io.github.mirco104.budget.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.category.CategoryType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

    private static final String REMAINDER = "Sonstiges (berechneter Rest)";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    // --- GET /api/dashboard (US5) ---

    @Test
    void reflectsTargetsAndTransactionsOfTheMonth() throws Exception {
        long wohnen = categoryId("Wohnen", CategoryType.EXPENSE);
        putTarget(wohnen, "2026-10", "{\"targetAmount\": 800}");
        postTransaction("2000", "Gehalt", "2026-10-01", categoryId("Gehalt/Lohn (netto)", CategoryType.INCOME));
        postTransaction("750", "Miete", "2026-10-03", wohnen);
        postTransaction("99", "Miete September", "2026-09-30", wohnen);

        mockMvc.perform(get("/api/dashboard").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.incomeTotal").value(2000.00))
                .andExpect(jsonPath("$.categories[?(@.name == 'Wohnen')].target", contains(800.00)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Wohnen')].actual", contains(750.00)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Wohnen')].difference", contains(50.00)))
                .andExpect(jsonPath("$.categories[?(@.name == '" + REMAINDER + "')].target", contains(1200.00)))
                .andExpect(jsonPath("$.expenses.target").value(2000.00))
                .andExpect(jsonPath("$.expenses.actual").value(750.00))
                .andExpect(jsonPath("$.surplus").value(1250.00))
                .andExpect(jsonPath("$.surplusLabel").value("Monatlicher Überschuss"))
                .andExpect(jsonPath("$.changePercent").value(1.6667))
                .andExpect(jsonPath("$.changeLabel").value("Anstieg der Gesamteinsparung"))
                .andExpect(jsonPath("$.income[?(@.name == 'Gehalt/Lohn (netto)')].share", contains(1.0)));
    }

    @Test
    void appliesTargetFromItsMonthOnwardUntilANewerOneReplacesIt() throws Exception {
        long wohnen = categoryId("Wohnen", CategoryType.EXPENSE);
        putTarget(wohnen, "2026-11", "{\"targetAmount\": 800}");
        putTarget(wohnen, "2027-01", "{\"targetAmount\": 850}");

        assertWohnenTarget("2026-10", null);
        assertWohnenTarget("2026-11", 800.00);
        assertWohnenTarget("2026-12", 800.00);
        assertWohnenTarget("2027-01", 850.00);
        assertWohnenTarget("2027-06", 850.00);
    }

    @Test
    void countsYearlyTransactionAsTwelfthOnlyInItsValidMonths() throws Exception {
        long versicherungen = categoryId("Versicherungen", CategoryType.EXPENSE);
        mockMvc.perform(post("/api/yearly-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountPerYear": 1200, "description": "Hausrat", "validFrom": "2026-11",
                                 "validTo": "2027-01", "categoryId": %d}
                                """.formatted(versicherungen)))
                .andExpect(status().isCreated());

        assertVersicherungenActual("2026-10", 0.00);
        assertVersicherungenActual("2026-11", 100.00);
        assertVersicherungenActual("2027-01", 100.00);
        assertVersicherungenActual("2027-02", 0.00);
    }

    @Test
    void showsEmptyMonthWithoutErrors() throws Exception {
        mockMvc.perform(get("/api/dashboard").param("month", "2030-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomeTotal").value(0.00))
                .andExpect(jsonPath("$.expenses.actual").value(0.00))
                .andExpect(jsonPath("$.surplus").value(0.00))
                .andExpect(jsonPath("$.changePercent").doesNotExist())
                .andExpect(jsonPath("$.income[*].share", everyItem(is(0.0))));
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
        assertThat(targets).as("target in %s", month).hasSize(1);
        assertThat(targets.get(0)).as("target in %s", month).isEqualTo(expected);
    }

    private void assertVersicherungenActual(String month, double expected) throws Exception {
        mockMvc.perform(get("/api/dashboard").param("month", month))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.name == 'Versicherungen')].actual", contains(expected)));
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

    private long categoryId(String name, CategoryType type) {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals(name) && c.getType() == type)
                .findFirst().orElseThrow()
                .getId();
    }
}
