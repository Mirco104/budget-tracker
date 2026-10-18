package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
                .andExpect(jsonPath("$", hasSize(21)))
                .andExpect(jsonPath("$[0].name").value("Wohnen"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[0].group").value("FIXED"))
                .andReturn().getResponse().getContentAsString();

        List<Integer> sortOrders = JsonPath.read(json, "$[*].sortOrder");
        assertThat(sortOrders).isSorted();
    }

    @Test
    void remainderCategoryIsMarkedAsSystem() throws Exception {
        getCategories(MONTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.group == 'REMAINDER')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.group == 'REMAINDER')].name").value("Sonstiges (berechneter Rest)"))
                .andExpect(jsonPath("$[?(@.group == 'REMAINDER')].system").value(true));
    }

    @Test
    void incomeCategoriesHaveNoGroupAndNoTarget() throws Exception {
        String json = getCategories(MONTH)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Object> incomeGroups = JsonPath.read(json, "$[?(@.type == 'INCOME')].group");
        List<Object> incomeAmounts = JsonPath.read(json, "$[?(@.type == 'INCOME')].targetAmount");
        List<Object> incomePercents = JsonPath.read(json, "$[?(@.type == 'INCOME')].targetPercent");
        assertThat(incomeGroups).hasSize(6).containsOnlyNulls();
        assertThat(incomeAmounts).containsOnlyNulls();
        assertThat(incomePercents).containsOnlyNulls();
    }

    // --- PUT /api/categories/{id}/target (US2): success ---

    @Test
    void setsTargetAmountWithTwoDecimals() throws Exception {
        long id = expenseId("Wohnen");

        putTarget(id, "{\"targetAmount\": 750.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(content().string(containsString("\"targetAmount\":750.50")))
                .andExpect(jsonPath("$.targetPercent").value(nullValue()));
    }

    @Test
    void setsTargetPercent() throws Exception {
        long id = expenseId("Lebensmittel");

        putTarget(id, "{\"targetPercent\": 0.15}")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"targetPercent\":0.1500")))
                .andExpect(jsonPath("$.targetAmount").value(nullValue()));
    }

    @Test
    void storesAmountAndPercentTogether() throws Exception {
        long id = expenseId("Freizeit");

        putTarget(id, "{\"targetAmount\": 100, \"targetPercent\": 0.05}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(100.00))
                .andExpect(jsonPath("$.targetPercent").value(0.05));
    }

    @Test
    void clearsTargetWhenBothValuesAreNull() throws Exception {
        long id = expenseId("Urlaub");
        putTarget(id, "{\"targetAmount\": 200}").andExpect(status().isOk());

        putTarget(id, "{\"targetAmount\": null, \"targetPercent\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(nullValue()))
                .andExpect(jsonPath("$.targetPercent").value(nullValue()));
    }

    @Test
    void acceptsBoundaryValues() throws Exception {
        long id = expenseId("Geschenke");

        putTarget(id, "{\"targetAmount\": 0}").andExpect(status().isOk());
        putTarget(id, "{\"targetPercent\": 0}").andExpect(status().isOk());
        putTarget(id, "{\"targetPercent\": 1}").andExpect(status().isOk());
    }

    @Test
    void persistsTarget() throws Exception {
        long id = expenseId("Mobilität");
        putTarget(id, "{\"targetAmount\": 120.25}").andExpect(status().isOk());

        getCategories(MONTH)
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetAmount").value(120.25));
    }

    // --- Month-dependent targets (US2, R3) ---

    @Test
    void targetAppliesFromItsMonthOnward() throws Exception {
        long id = expenseId("Lebensmittel");
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
        long id = expenseId("Lebensmittel");
        putTarget(id, "2026-11", "{\"targetAmount\": 300}").andExpect(status().isOk());
        putTarget(id, "2027-01", "{\"targetPercent\": 0.15}").andExpect(status().isOk());

        getCategories("2026-12")
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetAmount", contains(300.00)));
        getCategories("2027-02")
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetAmount", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetPercent", contains(0.15)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].targetValidFrom", contains("2027-01")));
    }

    @Test
    void savingAgainForTheSameMonthOverwritesTheEntry() throws Exception {
        long id = expenseId("Lebensmittel");
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
        mockMvc.perform(put("/api/categories/{id}/target", expenseId("Wohnen"))
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
        putTarget(expenseId("Wohnen"), "Oktober", "{\"targetAmount\": 10}")
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
        putTarget(expenseId("Wohnen"), "{\"targetAmount\": -1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"))
                .andExpect(jsonPath("$.errors[0].field").value("targetAmount"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Soll-Betrag darf nicht negativ sein."));
    }

    @Test
    void rejectsTargetPercentAboveOne() throws Exception {
        putTarget(expenseId("Wohnen"), "{\"targetPercent\": 1.01}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetPercent"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Prozentwert muss zwischen 0 und 100 % liegen."));
    }

    @Test
    void rejectsNegativeTargetPercent() throws Exception {
        putTarget(expenseId("Wohnen"), "{\"targetPercent\": -0.01}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetPercent"));
    }

    @Test
    void rejectsTargetAmountWithMoreThanTwoDecimals() throws Exception {
        putTarget(expenseId("Wohnen"), "{\"targetAmount\": 12.345}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetAmount"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Soll-Betrag darf höchstens 2 Nachkommastellen haben."));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        putTarget(expenseId("Wohnen"), "{\"targetAmount\": ")
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

    // --- 409 ---

    @Test
    void rejectsChangesToRemainderCategory() throws Exception {
        Category remainder = categoryRepository.findAll().stream()
                .filter(Category::isSystem)
                .findFirst().orElseThrow();

        putTarget(remainder.getId(), "{\"targetAmount\": 10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Konflikt"))
                .andExpect(jsonPath("$.detail").value(
                        "Die Rest-Kategorie wird automatisch berechnet und kann nicht geändert werden."));

        assertThat(categoryTargetRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsTargetForIncomeCategory() throws Exception {
        long id = categoryId("Nebenjob", CategoryType.INCOME);

        putTarget(id, "{\"targetAmount\": 10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Für Einnahmen-Kategorien kann kein Soll festgelegt werden."));
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

    private long expenseId(String name) {
        return categoryId(name, CategoryType.EXPENSE);
    }

    private long categoryId(String name, CategoryType type) {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals(name) && c.getType() == type)
                .findFirst().orElseThrow()
                .getId();
    }
}
