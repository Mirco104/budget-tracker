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
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.time.Clock;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class ReserveControllerTest {

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

    // --- Reference fixture F1 (spec section 12), end to end (R14, US13) ---

    @Test
    void referenceFixtureF1MatchesSpec() throws Exception {
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
        // Since decision 26 the salary is a monthly series from 2026-01 (spec section 12)
        postSeries("3000.00", "Gehalt", categoryId("Gehalt/Lohn (netto)"), "MONTHLY", "2026-01-01");
        postSeries("-900.00", "Miete", categoryId("Wohnen"), "MONTHLY", "2026-01-01");
        postSeries("-240.00", "Haftpflicht", categoryId("Versicherungen"), "YEARLY", "2026-10-15");
        long streaming = postSeries("-15.00", "Streaming", categoryId("Freizeit"), "MONTHLY", "2026-01-05");
        mockMvc.perform(delete("/api/recurring-transactions/{id}/occurrences/{date}", streaming, "2026-10-05")
                        .param("scope", "THIS"))
                .andExpect(status().isNoContent());

        // Look back with the default period of F1 (2025-10 to 2026-09)
        mockMvc.perform(get("/api/history").param("from", "2025-10").param("to", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2025-10"))
                .andExpect(jsonPath("$.to").value("2026-09"))
                .andExpect(jsonPath("$.months").value(9))
                .andExpect(jsonPath("$.averageIncome").value(3000.00))
                .andExpect(jsonPath("$.averageExpenses").value(1026.00))
                .andExpect(jsonPath("$.averageSurplus").value(1974.00))
                .andExpect(jsonPath("$.total.income").value(27000.00))
                .andExpect(jsonPath("$.total.expenses").value(9234.00))
                .andExpect(jsonPath("$.total.surplus").value(17766.00))
                .andExpect(jsonPath("$.history", hasSize(9)))
                .andExpect(jsonPath("$.history[0].month").value("2026-01"))
                .andExpect(jsonPath("$.history[0].income").value(3000.00))
                .andExpect(jsonPath("$.history[0].expenses").value(915.00))
                .andExpect(jsonPath("$.history[0].surplus").value(2085.00))
                .andExpect(jsonPath("$.history[0].incomeCategories[0].name").value("Gehalt/Lohn (netto)"))
                .andExpect(jsonPath("$.history[0].incomeCategories[0].icon").value("wallet"))
                .andExpect(jsonPath("$.history[8].month").value("2026-09"))
                .andExpect(jsonPath("$.history[8].expenses").value(1914.00))
                .andExpect(jsonPath("$.history[8].surplus").value(1086.00))
                .andExpect(jsonPath("$.history[8].expenseCategories[?(@.name == 'Lebensmittel')].amount",
                        contains(999.00)))
                .andExpect(jsonPath("$.history[8].expenseCategories[?(@.name == 'Wohnen')].icon",
                        contains("home")));

        mockMvc.perform(get("/api/history").param("from", "2026-09").param("to", "2026-09"))
                .andExpect(jsonPath("$.months").value(1))
                .andExpect(jsonPath("$.averageExpenses").value(1914.00));

        // Outlook from October 2026 (R14); the look back is not part of it any more (decision 29)
        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.history").doesNotExist())
                .andExpect(jsonPath("$.averageExpenses").doesNotExist())
                .andExpect(jsonPath("$.monthlyReserve").value(20.00))
                .andExpect(jsonPath("$.reserveBalance").value(240.00))
                .andExpect(jsonPath("$.reserves[*].description", contains("Haftpflicht")))
                .andExpect(jsonPath("$.reserves[0].categoryName").value("Versicherungen"))
                .andExpect(jsonPath("$.reserves[0].amount").value(240.00))
                .andExpect(jsonPath("$.reserves[0].nextDate").value("2026-10-15"))
                .andExpect(jsonPath("$.reserves[0].monthly").value(20.00))
                .andExpect(jsonPath("$.reserves[0].saved").value(240.00))
                .andExpect(jsonPath("$.reserves[0].ratesLeft").value(0))
                .andExpect(jsonPath("$.potMonths").doesNotExist())
                .andExpect(jsonPath("$.forecast[0].fromReserves").value(240.00))
                .andExpect(jsonPath("$.forecast[1].fromReserves").value(0.00))
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
                .andExpect(jsonPath("$.reserves[0].ratesLeft").value(11))
                .andExpect(jsonPath("$.reserveBalance").value(20.00));
    }

    @Test
    void forecastShowsSingleExpensesBookedForLaterMonths() throws Exception {
        postTransaction("-80.00", "Konzertkarten", "2026-12-20", categoryId("Freizeit"));

        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forecast[2].month").value("2026-12"))
                .andExpect(jsonPath("$.forecast[2].total").value(80.00))
                .andExpect(jsonPath("$.forecast[2].entries[0].description").value("Konzertkarten"));
    }

    @Test
    void lookBackShowsIncomeExpensesAndSurplusOfPastMonths() throws Exception {
        postTransaction("2500.00", "Gehalt", "2026-09-01", categoryId("Gehalt/Lohn (netto)"));
        postTransaction("-800.00", "Miete", "2026-09-03", categoryId("Wohnen"));
        postTransaction("-50.00", "Einkauf", "2026-10-02", categoryId("Lebensmittel"));

        mockMvc.perform(get("/api/history").param("from", "2026-01").param("to", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").value(1))
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.history[0].month").value("2026-09"))
                .andExpect(jsonPath("$.history[0].income").value(2500.00))
                .andExpect(jsonPath("$.history[0].expenses").value(800.00))
                .andExpect(jsonPath("$.history[0].surplus").value(1700.00))
                .andExpect(jsonPath("$.history[0].incomeCategories[0].name").value("Gehalt/Lohn (netto)"))
                .andExpect(jsonPath("$.history[0].expenseCategories[0].id").value(categoryId("Wohnen")))
                .andExpect(jsonPath("$.history[0].expenseCategories[0].amount").value(800.00))
                .andExpect(jsonPath("$.averageIncome").value(2500.00))
                .andExpect(jsonPath("$.averageSurplus").value(1700.00))
                // Decision 34: no shares any more
                .andExpect(jsonPath("$.history[0].expenseCategories[0].share").doesNotExist())
                .andExpect(jsonPath("$.total.expenses").value(800.00));
    }

    @Test
    void looksBackOverACalendarYear() throws Exception {
        postSeries("-900.00", "Miete", categoryId("Wohnen"), "MONTHLY", "2025-01-01");

        mockMvc.perform(get("/api/history").param("from", "2025-01").param("to", "2025-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").value(12))
                .andExpect(jsonPath("$.total.expenses").value(10800.00));
    }

    // Without parameters the 12 months before the current month (R14)
    @Test
    void looksBackOverTheTwelveMonthsBeforeTheCurrentMonthByDefault() throws Exception {
        YearMonth current = YearMonth.now(clock);
        postSeries("-100.00", "Abo", categoryId("Freizeit"), "MONTHLY", current.minusMonths(30).atDay(1).toString());

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(current.minusMonths(12).toString()))
                .andExpect(jsonPath("$.to").value(current.minusMonths(1).toString()))
                .andExpect(jsonPath("$.months").value(12))
                .andExpect(jsonPath("$.averageExpenses").value(100.00));
    }

    @Test
    void showsAnEmptyLookBackWithoutErrors() throws Exception {
        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").value(0))
                .andExpect(jsonPath("$.averageExpenses").value(nullValue()))
                .andExpect(jsonPath("$.total").value(nullValue()))
                .andExpect(jsonPath("$.history", hasSize(0)));
    }

    @Test
    void showsAnEmptyOutlookWithoutErrors() throws Exception {
        mockMvc.perform(get("/api/reserves").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyReserve").value(0.00))
                .andExpect(jsonPath("$.reserveBalance").value(0.00))
                .andExpect(jsonPath("$.reserves", hasSize(0)))
                .andExpect(jsonPath("$.forecast[0].fromReserves").value(0.00))
                .andExpect(jsonPath("$.forecast", hasSize(12)));
    }

    // Without a month the outlook starts in the current month (decision 29)
    @Test
    void outlookStartsInTheCurrentMonthByDefault() throws Exception {
        mockMvc.perform(get("/api/reserves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(YearMonth.now(clock).toString()))
                .andExpect(jsonPath("$.forecast[0].month").value(YearMonth.now(clock).toString()));
    }

    // --- validation of the look back (R14, US13) ---

    @Test
    void acceptsTheLastCompletedMonthAndExactly120Months() throws Exception {
        YearMonth last = YearMonth.now(clock).minusMonths(1);

        mockMvc.perform(get("/api/history").param("from", last.minusMonths(119).toString())
                        .param("to", last.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMoreThan120Months() throws Exception {
        YearMonth last = YearMonth.now(clock).minusMonths(1);

        mockMvc.perform(get("/api/history").param("from", last.minusMonths(120).toString())
                        .param("to", last.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Zeitraum darf höchstens 120 Monate lang sein."));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rejectsToInTheCurrentMonthOrLater(int monthsAhead) throws Exception {
        YearMonth to = YearMonth.now(clock).plusMonths(monthsAhead);

        mockMvc.perform(get("/api/history").param("from", to.minusMonths(3).toString()).param("to", to.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"))
                .andExpect(jsonPath("$.errors[0].message").value("„Bis“ muss vor dem aktuellen Monat liegen."));
    }

    @Test
    void rejectsFromAfterTo() throws Exception {
        mockMvc.perform(get("/api/history").param("from", "2026-05").param("to", "2026-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("„Bis“ darf nicht vor „Von“ liegen."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"from", "to"})
    void rejectsOnlyOneMonth(String given) throws Exception {
        String missing = given.equals("from") ? "to" : "from";
        mockMvc.perform(get("/api/history").param(given, "2026-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Parameter '" + missing + "' fehlt."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "Januar", "2026-01-01"})
    void rejectsInvalidMonthsInTheLookBack(String month) throws Exception {
        mockMvc.perform(get("/api/history").param("from", month).param("to", "2026-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "Oktober"})
    void rejectsInvalidMonthInTheOutlook(String month) throws Exception {
        mockMvc.perform(get("/api/reserves").param("month", month))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Anfrage"));
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
        return categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
