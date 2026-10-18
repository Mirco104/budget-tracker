package io.github.mirco104.budget.dashboard;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.category.CategoryType;
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
        mockMvc.perform(put("/api/categories/{id}/target", wohnen)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\": 800}"))
                .andExpect(status().isOk());
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
