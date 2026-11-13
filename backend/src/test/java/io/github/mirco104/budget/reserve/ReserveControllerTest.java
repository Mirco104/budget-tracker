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
import java.time.LocalDate;
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

    // A monthly payment booked in every month from the start until the month before today (only bookings count, P1)
    private void postSeries(String amount, String description, long categoryId, String interval, String startDate)
            throws Exception {
        LocalDate date = LocalDate.parse(startDate);
        YearMonth current = YearMonth.now(clock);
        for (; YearMonth.from(date).isBefore(current); date = date.plusMonths(1)) {
            postTransaction(amount, description, date.toString(), categoryId);
        }
    }

    private long categoryId(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
