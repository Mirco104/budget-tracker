package io.github.mirco104.budget.reserve;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.category.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReserveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    // --- Reference fixture F1 (spec section 12), end to end (R14, US13) ---

    @Test
    void referenceFixtureF1MatchesSpec() throws Exception {
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

        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.averageExpenses").value(1026.00))
                .andExpect(jsonPath("$.averageMonths").value(9))
                .andExpect(jsonPath("$.averageIncome").value(0.00))
                .andExpect(jsonPath("$.averageSurplus").value(-1026.00))
                .andExpect(jsonPath("$.history", hasSize(9)))
                .andExpect(jsonPath("$.history[0].month").value("2026-01"))
                .andExpect(jsonPath("$.history[8].month").value("2026-09"))
                .andExpect(jsonPath("$.history[8].expenses").value(1914.00))
                .andExpect(jsonPath("$.history[8].surplus").value(-1914.00))
                .andExpect(jsonPath("$.history[8].expenseCategories[?(@.name == 'Lebensmittel')].amount",
                        contains(999.00)))
                .andExpect(jsonPath("$.monthlyReserve").value(20.00))
                .andExpect(jsonPath("$.reserveBalance").value(240.00))
                .andExpect(jsonPath("$.reserves[*].description", contains("Haftpflicht")))
                .andExpect(jsonPath("$.reserves[0].categoryName").value("Versicherungen"))
                .andExpect(jsonPath("$.reserves[0].amount").value(240.00))
                .andExpect(jsonPath("$.reserves[0].nextDate").value("2026-10-15"))
                .andExpect(jsonPath("$.reserves[0].monthly").value(20.00))
                .andExpect(jsonPath("$.reserves[0].saved").value(240.00))
                .andExpect(jsonPath("$.forecast", hasSize(12)))
                .andExpect(jsonPath("$.forecast[0].month").value("2026-10"))
                .andExpect(jsonPath("$.forecast[0].total").value(2636.49))
                .andExpect(jsonPath("$.forecast[0].entries", hasSize(10)))
                .andExpect(jsonPath("$.forecast[0].entries[0].description").value("Miete"))
                .andExpect(jsonPath("$.forecast[0].entries[0].kind").value("RECURRING"))
                .andExpect(jsonPath("$.forecast[0].entries[1].description").value("Strom"))
                .andExpect(jsonPath("$.forecast[0].entries[1].kind").value("SINGLE"))
                .andExpect(jsonPath("$.forecast[1].total").value(915.00))
                .andExpect(jsonPath("$.forecast[11].month").value("2027-09"))
                .andExpect(jsonPath("$.forecast[11].total").value(915.00));

        // November: the pot starts again with one monthly rate (R14)
        mockMvc.perform(get("/api/reserves").param("month", "2026-11"))
                .andExpect(jsonPath("$.reserves[0].nextDate").value("2027-10-15"))
                .andExpect(jsonPath("$.reserveBalance").value(20.00))
                .andExpect(jsonPath("$.averageMonths").value(10));
    }

    @Test
    void forecastShowsSingleExpensesBookedForLaterMonths() throws Exception {
        postTransaction("-80.00", "Konzertkarten", "2026-12-20", categoryId("Freizeit"));

        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forecast[2].month").value("2026-12"))
                .andExpect(jsonPath("$.forecast[2].total").value(80.00))
                .andExpect(jsonPath("$.forecast[2].entries[0].description").value("Konzertkarten"))
                // The first entry lies after October, so no completed month counts yet
                .andExpect(jsonPath("$.averageMonths").value(0))
                .andExpect(jsonPath("$.averageExpenses").value(nullValue()));
    }

    @Test
    void lookBackShowsIncomeExpensesAndSurplusOfPastMonths() throws Exception {
        postTransaction("2500.00", "Gehalt", "2026-09-01", categoryId("Gehalt/Lohn (netto)"));
        postTransaction("-800.00", "Miete", "2026-09-03", categoryId("Wohnen"));
        postTransaction("-50.00", "Einkauf", "2026-10-02", categoryId("Lebensmittel"));

        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageMonths").value(1))
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.history[0].month").value("2026-09"))
                .andExpect(jsonPath("$.history[0].income").value(2500.00))
                .andExpect(jsonPath("$.history[0].expenses").value(800.00))
                .andExpect(jsonPath("$.history[0].surplus").value(1700.00))
                .andExpect(jsonPath("$.history[0].incomeCategories[0].name").value("Gehalt/Lohn (netto)"))
                .andExpect(jsonPath("$.history[0].expenseCategories[0].name").value("Wohnen"))
                .andExpect(jsonPath("$.averageIncome").value(2500.00))
                .andExpect(jsonPath("$.averageSurplus").value(1700.00))
                .andExpect(jsonPath("$.history[0].incomeShare").value(1.0))
                .andExpect(jsonPath("$.history[0].expensesShare").value(0.32))
                .andExpect(jsonPath("$.history[0].surplusShare").value(0.68))
                .andExpect(jsonPath("$.history[0].expenseCategories[0].share").value(0.32))
                .andExpect(jsonPath("$.totalIncome").value(2500.00))
                .andExpect(jsonPath("$.totalExpenses").value(800.00))
                .andExpect(jsonPath("$.totalSurplus").value(1700.00))
                .andExpect(jsonPath("$.incomeShare").value(1.0))
                .andExpect(jsonPath("$.expensesShare").value(0.32))
                .andExpect(jsonPath("$.surplusShare").value(0.68));
    }

    @Test
    void showsEmptyMonthWithoutErrors() throws Exception {
        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageExpenses").value(nullValue()))
                .andExpect(jsonPath("$.averageMonths").value(0))
                .andExpect(jsonPath("$.history", hasSize(0)))
                .andExpect(jsonPath("$.totalExpenses").value(nullValue()))
                .andExpect(jsonPath("$.monthlyReserve").value(0.00))
                .andExpect(jsonPath("$.reserveBalance").value(0.00))
                .andExpect(jsonPath("$.reserves", hasSize(0)))
                .andExpect(jsonPath("$.forecast", hasSize(12)));
    }

    // --- chosen period (R14) ---

    @Test
    void averagesAndLooksBackOverTheChosenPeriod() throws Exception {
        postSeries("-900.00", "Miete", categoryId("Wohnen"), "MONTHLY", "2026-01-01");
        postTransaction("-999.00", "Einkauf September", "2026-09-30", categoryId("Lebensmittel"));

        // (6 × 900 + 999) / 6
        mockMvc.perform(get("/api/reserves").param("month", "2026-10").param("months", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageMonths").value(6))
                .andExpect(jsonPath("$.history", hasSize(6)))
                .andExpect(jsonPath("$.history[0].month").value("2026-04"))
                .andExpect(jsonPath("$.averageExpenses").value(1066.50))
                .andExpect(jsonPath("$.totalExpenses").value(6399.00))
                .andExpect(jsonPath("$.forecast", hasSize(12)));

        // Without the parameter 12 months, limited by the first entry in January
        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(jsonPath("$.averageMonths").value(9))
                .andExpect(jsonPath("$.history[0].month").value("2026-01"));

        // 24 months are limited by the first entry as well
        mockMvc.perform(get("/api/reserves").param("month", "2026-10").param("months", "24"))
                .andExpect(jsonPath("$.averageMonths").value(9));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "120"})
    void acceptsThePeriodBounds(String months) throws Exception {
        mockMvc.perform(get("/api/reserves").param("month", "2026-10").param("months", months))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "121"})
    void rejectsPeriodOutsideTheBounds(String months) throws Exception {
        mockMvc.perform(get("/api/reserves").param("month", "2026-10").param("months", months))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"))
                .andExpect(jsonPath("$.errors[0].field").value("months"));
    }

    @Test
    void rejectsPeriodThatIsNoNumber() throws Exception {
        mockMvc.perform(get("/api/reserves").param("month", "2026-10").param("months", "zwölf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
    }

    // --- validation ---

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "Oktober", ""})
    void rejectsInvalidMonth(String month) throws Exception {
        mockMvc.perform(get("/api/reserves").param("month", month))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
    }

    @Test
    void rejectsMissingMonth() throws Exception {
        mockMvc.perform(get("/api/reserves"))
                .andExpect(status().isBadRequest())
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

    private long categoryId(String name) {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
