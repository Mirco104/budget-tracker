package io.github.mirco104.budget.transaction;

import static org.hamcrest.Matchers.contains;
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
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class TransactionControllerTest {

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

    // --- POST /api/transactions (US3): success ---

    @Test
    void storesSignedAmounts() throws Exception {
        postTransaction(body("-45.99", "Tanken", "2026-10-05", categoryId("Mobilität")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(-45.99));
        postTransaction(body("30", "Erstattung", "2026-10-06", categoryId("Lebensmittel")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(30.00));
    }

    @Test
    void acceptsDescriptionWithExactly100Characters() throws Exception {
        postTransaction(body("10", "x".repeat(100), "2026-10-05", categoryId("Freizeit")))
                .andExpect(status().isCreated());
    }

    @Test
    void trimsDescription() throws Exception {
        postTransaction(body("10", "  Kino  ", "2026-10-05", categoryId("Freizeit")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Kino"));
    }

    // --- POST /api/transactions (US3): validation ---

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0", "1.234", "-1.234"})
    void rejectsInvalidAmount(String amount) throws Exception {
        postTransaction(body(amount, "Test", "2026-10-05", categoryId("Freizeit")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void rejectsMissingAmount() throws Exception {
        postTransaction("{\"description\": \"Test\", \"date\": \"2026-10-05\", \"categoryId\": "
                + categoryId("Freizeit") + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Betrag ist Pflicht."));
    }

    @Test
    void rejectsBlankDescription() throws Exception {
        postTransaction(body("10", "   ", "2026-10-05", categoryId("Freizeit")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    void rejectsDescriptionLongerThan100Characters() throws Exception {
        postTransaction(body("10", "x".repeat(101), "2026-10-05", categoryId("Freizeit")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Die Beschreibung darf höchstens 100 Zeichen lang sein."));
    }

    @Test
    void rejectsMissingDate() throws Exception {
        postTransaction("{\"amount\": 10, \"description\": \"Test\", \"categoryId\": "
                + categoryId("Freizeit") + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
    }

    @Test
    void rejectsMissingCategory() throws Exception {
        postTransaction("{\"amount\": 10, \"description\": \"Test\", \"date\": \"2026-10-05\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        postTransaction(body("10", "Test", "2026-10-05", 999_999))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"))
                .andExpect(jsonPath("$.errors[0].message").value("Die Kategorie existiert nicht."));
    }

    @Test
    void rejectsZeroAmountWithMessage() throws Exception {
        postTransaction(body("0", "Test", "2026-10-05", categoryId("Freizeit")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Betrag darf nicht 0 sein."));
    }

    // --- GET /api/transactions?month= ---

    @Test
    void listsOnlyTransactionsOfTheMonthNewestFirst() throws Exception {
        long categoryId = categoryId("Freizeit");
        create(body("1", "September", "2026-09-30", categoryId));
        create(body("2", "Oktober Anfang", "2026-10-01", categoryId));
        create(body("3", "Oktober Ende", "2026-10-31", categoryId));
        create(body("4", "November", "2026-11-01", categoryId));

        mockMvc.perform(get("/api/transactions").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("Oktober Ende"))
                .andExpect(jsonPath("$[1].description").value("Oktober Anfang"));
    }

    @Test
    void returnsEmptyListForMonthWithoutTransactions() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2030-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // Without month and period it asks for the month, the short form (US26)
    @Test
    void rejectsMissingMonth() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Parameter 'month' fehlt."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "abc", "2026-10-01"})
    void rejectsInvalidMonth(String month) throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", month))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
    }

    // --- GET /api/transactions?from=&to= (US26) ---

    @Test
    void listsTransactionsOfAPeriodIncludingBothDaysNewestFirst() throws Exception {
        long categoryId = categoryId("Freizeit");
        create(body("-1", "Davor", "2026-08-14", categoryId));
        create(body("-2", "Erster Tag", "2026-08-15", categoryId));
        create(body("-3", "Mitte", "2026-09-30", categoryId));
        create(body("-4", "Letzter Tag", "2026-10-10", categoryId));
        create(body("-5", "Danach", "2026-10-11", categoryId));

        mockMvc.perform(get("/api/transactions").param("from", "2026-08-15").param("to", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].description", contains("Letzter Tag", "Mitte", "Erster Tag")));
    }

    @Test
    void acceptsASingleDay() throws Exception {
        long categoryId = categoryId("Freizeit");
        create(body("-1", "Heute", "2026-10-10", categoryId));
        create(body("-1", "Morgen", "2026-10-11", categoryId));

        mockMvc.perform(get("/api/transactions").param("from", "2026-10-10").param("to", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].description", contains("Heute")));
    }

    // "mehr als 10 Jahre" is 400: exactly 10 years (to before from + 10 years) is the longest period
    @Test
    void acceptsExactlyTenYears() throws Exception {
        mockMvc.perform(get("/api/transactions").param("from", "2016-01-01").param("to", "2025-12-31"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMoreThanTenYears() throws Exception {
        mockMvc.perform(get("/api/transactions").param("from", "2016-01-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Zeitraum darf höchstens 10 Jahre lang sein."));
    }

    @Test
    void rejectsFromAfterTo() throws Exception {
        mockMvc.perform(get("/api/transactions").param("from", "2026-10-11").param("to", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"))
                .andExpect(jsonPath("$.errors[0].message").value("„Bis“ darf nicht vor „Von“ liegen."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"from", "to"})
    void rejectsAPeriodWithOnlyOneDay(String given) throws Exception {
        String missing = given.equals("from") ? "to" : "from";
        mockMvc.perform(get("/api/transactions").param(given, "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Parameter '" + missing + "' fehlt."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10", "2026-02-30", "abc", "10.10.2026"})
    void rejectsInvalidDates(String date) throws Exception {
        mockMvc.perform(get("/api/transactions").param("from", date).param("to", "2026-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
    }

    @Test
    void rejectsMonthTogetherWithAPeriod() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("month", "2026-10").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Entweder 'month' oder 'from' und 'to' angeben."));
    }

    // R15: the period only shows the own entries
    @Test
    void listsOnlyTheOwnTransactionsOfThePeriod() throws Exception {
        create(body("-1", "Meins", "2026-10-10", categoryId("Freizeit")));
        User other = TestUsers.create(userService, "person-c@example.test");
        MockMvc otherMvc = TestUsers.mockMvcFor(context, other);

        otherMvc.perform(get("/api/transactions").param("from", "2026-01-01").param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --- PUT /api/transactions/{id} (US4) ---

    @Test
    void updatesTransactionIncludingCategory() throws Exception {
        long id = create(body("10", "Alt", "2026-10-05", categoryId("Freizeit")));
        long salaryId = categoryId("Gehalt/Lohn (netto)");

        putTransaction(id, body("2000", "Neu", "2026-10-28", salaryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.amount").value(2000.00))
                .andExpect(jsonPath("$.description").value("Neu"))
                .andExpect(jsonPath("$.date").value("2026-10-28"))
                .andExpect(jsonPath("$.categoryName").value("Gehalt/Lohn (netto)"));

        mockMvc.perform(get("/api/transactions").param("month", "2026-10"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Neu"));
    }

    @Test
    void updateOfUnknownTransactionReturns404() throws Exception {
        putTransaction(999_999, body("10", "Test", "2026-10-05", categoryId("Freizeit")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Nicht gefunden"));
    }

    @Test
    void updateWithInvalidBodyReturns400() throws Exception {
        long id = create(body("10", "Test", "2026-10-05", categoryId("Freizeit")));

        putTransaction(id, body("0", "Test", "2026-10-05", categoryId("Freizeit")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    // --- DELETE /api/transactions/{id} (US4) ---

    @Test
    void deletesTransaction() throws Exception {
        long id = create(body("10", "Test", "2026-10-05", categoryId("Freizeit")));

        mockMvc.perform(delete("/api/transactions/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/transactions").param("month", "2026-10"))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(delete("/api/transactions/{id}", id))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private static String body(String amount, String description, String date, long categoryId) {
        return """
                {"amount": %s, "description": "%s", "date": "%s", "categoryId": %d}
                """.formatted(amount, description, date, categoryId);
    }

    private long create(String body) throws Exception {
        String json = postTransaction(body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions postTransaction(String body) throws Exception {
        return mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions putTransaction(long id, String body) throws Exception {
        return mockMvc.perform(put("/api/transactions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long categoryId(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
