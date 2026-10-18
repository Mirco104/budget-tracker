package io.github.mirco104.budget.yearly;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class YearlyTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    // --- POST /api/yearly-transactions (US6): success ---

    @Test
    void createsYearlyTransactionWithLocationAndScaledAmount() throws Exception {
        long categoryId = expenseId("Versicherungen");

        postYearly("""
                {"amountPerYear": 600.5, "description": "  Kfz-Versicherung  ", "dueDate": "2026-11-15",
                 "validFrom": "2026-11", "validTo": "2027-10", "categoryId": %d}
                """.formatted(categoryId))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/yearly-transactions/\\d+$")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.amountPerYear").value(600.50))
                .andExpect(jsonPath("$.amountPerMonth").value(50.04))
                .andExpect(jsonPath("$.description").value("Kfz-Versicherung"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-15"))
                .andExpect(jsonPath("$.validFrom").value("2026-11"))
                .andExpect(jsonPath("$.validTo").value("2027-10"))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.categoryName").value("Versicherungen"))
                .andExpect(jsonPath("$.categoryType").value("EXPENSE"));
    }

    @Test
    void createsOpenEndedYearlyTransactionWithoutDueDate() throws Exception {
        postYearly(body("120", "Rundfunk", "2026-10", null, expenseId("Nebenkosten")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validTo").doesNotExist())
                .andExpect(jsonPath("$.dueDate").doesNotExist());
    }

    @Test
    void acceptsValidToEqualToValidFrom() throws Exception {
        postYearly(body("120", "Einmalig", "2026-10", "2026-10", expenseId("Nebenkosten")))
                .andExpect(status().isCreated());
    }

    // --- POST /api/yearly-transactions (US6): validation ---

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "1.234"})
    void rejectsInvalidAmount(String amount) throws Exception {
        postYearly(body(amount, "Test", "2026-10", null, expenseId("Versicherungen")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"))
                .andExpect(jsonPath("$.errors[0].field").value("amountPerYear"));
    }

    @Test
    void rejectsBlankDescription() throws Exception {
        postYearly(body("10", "   ", "2026-10", null, expenseId("Versicherungen")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    void rejectsDescriptionLongerThan100Characters() throws Exception {
        postYearly(body("10", "x".repeat(101), "2026-10", null, expenseId("Versicherungen")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    void rejectsMissingValidFrom() throws Exception {
        postYearly(body("10", "Test", null, null, expenseId("Versicherungen")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("validFrom"));
    }

    @Test
    void rejectsValidToBeforeValidFrom() throws Exception {
        postYearly(body("10", "Test", "2026-10", "2026-09", expenseId("Versicherungen")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("validTo"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Der Monat „gültig bis“ darf nicht vor „gültig ab“ liegen."));
    }

    @Test
    void rejectsInvalidMonthFormatInBody() throws Exception {
        postYearly(body("10", "Test", "2026-13", null, expenseId("Versicherungen")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        postYearly(body("10", "Test", "2026-10", null, 999_999))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"))
                .andExpect(jsonPath("$.errors[0].message").value("Die Kategorie existiert nicht."));
    }

    // --- GET /api/yearly-transactions?month= ---

    @Test
    void listsOnlyYearlyTransactionsValidInTheMonth() throws Exception {
        long categoryId = expenseId("Versicherungen");
        create(body("1", "Abgelaufen", "2026-01", "2026-09", categoryId));
        create(body("2", "Endet im Oktober", "2026-01", "2026-10", categoryId));
        create(body("3", "Beginnt im Oktober", "2026-10", null, categoryId));
        create(body("4", "Beginnt im November", "2026-11", null, categoryId));

        mockMvc.perform(get("/api/yearly-transactions").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("Beginnt im Oktober"))
                .andExpect(jsonPath("$[1].description").value("Endet im Oktober"));
    }

    @Test
    void rejectsMissingMonth() throws Exception {
        mockMvc.perform(get("/api/yearly-transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Parameter 'month' fehlt."));
    }

    // --- PUT /api/yearly-transactions/{id} ---

    @Test
    void updatesYearlyTransaction() throws Exception {
        long id = create(body("100", "Alt", "2026-10", null, expenseId("Versicherungen")));

        putYearly(id, body("240", "Neu", "2026-11", "2027-10", expenseId("Nebenkosten")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.amountPerYear").value(240.00))
                .andExpect(jsonPath("$.description").value("Neu"))
                .andExpect(jsonPath("$.validFrom").value("2026-11"))
                .andExpect(jsonPath("$.validTo").value("2027-10"))
                .andExpect(jsonPath("$.categoryName").value("Nebenkosten"));
    }

    @Test
    void updateOfUnknownYearlyTransactionReturns404() throws Exception {
        putYearly(999_999, body("10", "Test", "2026-10", null, expenseId("Versicherungen")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Nicht gefunden"));
    }

    @Test
    void updateWithValidToBeforeValidFromReturns400() throws Exception {
        long id = create(body("100", "Test", "2026-10", null, expenseId("Versicherungen")));

        putYearly(id, body("100", "Test", "2026-10", "2026-01", expenseId("Versicherungen")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("validTo"));
    }

    // --- DELETE /api/yearly-transactions/{id} ---

    @Test
    void deletesYearlyTransaction() throws Exception {
        long id = create(body("100", "Test", "2026-10", null, expenseId("Versicherungen")));

        mockMvc.perform(delete("/api/yearly-transactions/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/yearly-transactions").param("month", "2026-10"))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(delete("/api/yearly-transactions/{id}", id))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private static String body(String amount, String description, String validFrom, String validTo,
            long categoryId) {
        return """
                {"amountPerYear": %s, "description": "%s", "validFrom": %s, "validTo": %s, "categoryId": %d}
                """.formatted(amount, description, quoted(validFrom), quoted(validTo), categoryId);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private long create(String body) throws Exception {
        String json = postYearly(body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions postYearly(String body) throws Exception {
        return mockMvc.perform(post("/api/yearly-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions putYearly(long id, String body) throws Exception {
        return mockMvc.perform(put("/api/yearly-transactions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long expenseId(String name) {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals(name) && c.getType() == CategoryType.EXPENSE)
                .findFirst().orElseThrow()
                .getId();
    }
}
