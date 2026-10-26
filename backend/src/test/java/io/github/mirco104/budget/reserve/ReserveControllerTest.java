package io.github.mirco104.budget.reserve;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
        postSeries("-900.00", "Miete", categoryId("Wohnen"), "MONTHLY", "2026-01-01");
        postSeries("-240.00", "Haftpflicht", categoryId("Versicherungen"), "YEARLY", "2026-10-15");
        long streaming = postSeries("-15.00", "Streaming", categoryId("Freizeit"), "MONTHLY", "2026-01-05");
        mockMvc.perform(delete("/api/recurring-transactions/{id}/occurrences/{date}", streaming, "2026-10-05")
                        .param("scope", "THIS"))
                .andExpect(status().isNoContent());
        // Income series and single transactions do not count (R14)
        postSeries("3000.00", "Gehalt", categoryId("Gehalt/Lohn (netto)"), "MONTHLY", "2026-01-01");
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -640.00, "description": "Sparplan", "date": "2026-10-15",
                                 "categoryId": %d}
                                """.formatted(categoryId("Investitionen"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.averageMonthlyCosts").value(935.00))
                .andExpect(jsonPath("$.reserve").value(20.00))
                .andExpect(jsonPath("$.series[*].description", contains("Miete", "Streaming", "Haftpflicht")))
                .andExpect(jsonPath("$.series[*].monthlyEquivalent", contains(900.00, 15.00, 20.00)))
                .andExpect(jsonPath("$.series[2].categoryName").value("Versicherungen"))
                .andExpect(jsonPath("$.series[2].interval").value("YEARLY"))
                .andExpect(jsonPath("$.forecast", hasSize(12)))
                .andExpect(jsonPath("$.forecast[0].month").value("2026-10"))
                .andExpect(jsonPath("$.forecast[0].total").value(1140.00))
                .andExpect(jsonPath("$.forecast[0].occurrences[*].description", contains("Miete", "Haftpflicht")))
                .andExpect(jsonPath("$.forecast[0].occurrences[1].date").value("2026-10-15"))
                .andExpect(jsonPath("$.forecast[0].occurrences[1].amount").value(240.00))
                .andExpect(jsonPath("$.forecast[1].total").value(915.00))
                .andExpect(jsonPath("$.forecast[11].month").value("2027-09"))
                .andExpect(jsonPath("$.forecast[11].total").value(915.00));
    }

    @Test
    void showsEmptyMonthWithoutErrors() throws Exception {
        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageMonthlyCosts").value(0.00))
                .andExpect(jsonPath("$.reserve").value(0.00))
                .andExpect(jsonPath("$.series", hasSize(0)))
                .andExpect(jsonPath("$.forecast", hasSize(12)));
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
