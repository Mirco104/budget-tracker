package io.github.mirco104.budget.reserve;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** P8: reserve pots in the budget lines, "aus Rücklage / Ersparnissen bezahlen" and saving goals (AP 27e). */
@SpringBootTest
@Transactional
class ReservePotControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private Clock clock;

    private MockMvc mockMvc;
    private User user;
    private LocalDate today;
    private String month;

    @BeforeEach
    void signIn() {
        user = TestUsers.create(userService, "person-b@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        today = LocalDate.now(clock);
        month = YearMonth.from(today).toString();
    }

    @Test
    void thePotPaysTheYearlyPaymentAndTheBudgetCountsOnlyTheMonthlyShare() throws Exception {
        String itemKey = yearlyKfz();
        book("-1200", "Kfz-Versicherung", null, null);

        mockMvc.perform(get("/api/dashboard").param("month", month))
                .andExpect(status().isOk())
                // The tile counts the real booking (P1), the budget line the monthly share (P8)
                .andExpect(jsonPath("$.expenses.actual").value(1200.00))
                .andExpect(jsonPath("$.categories[?(@.name == 'Mobilität')].actual").value(100.00))
                .andExpect(jsonPath("$.budget.actual").value(100.00))
                .andExpect(jsonPath("$.reserves[0].name").value("Kfz-Versicherung"))
                .andExpect(jsonPath("$.reserves[0].balance").value(1200.00))
                .andExpect(jsonPath("$.reserves[0].due").value(true))
                .andExpect(jsonPath("$.reserves[0].fromPot").value(1200.00))
                .andExpect(jsonPath("$.reserves[0].shortfall").value(0.00));
        // An explicit choice of the pot is checked
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -50, "description": "Reparatur", "date": "%s", "categoryId": %d,
                         "reserveItemKey": "%s"}""".formatted(today, category("Mobilität"), itemKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reserveItemKey").value(itemKey));
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -50, "description": "Reparatur", "date": "%s", "categoryId": %d,
                         "reserveItemKey": "00000000-0000-0000-0000-000000000009"}"""
                        .formatted(today, category("Mobilität"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reserveItemKey"));
        // 1,250 € from the pot of 1,200 €: 50 € are missing and count into the budget
        mockMvc.perform(get("/api/dashboard").param("month", month))
                .andExpect(jsonPath("$.reserves[0].fromPot").value(1200.00))
                .andExpect(jsonPath("$.reserves[0].shortfall").value(50.00))
                .andExpect(jsonPath("$.categories[?(@.name == 'Mobilität')].actual").value(150.00));
    }

    @Test
    void aPaymentFromASavingGoalDoesNotCountInTheBudgetAndLowersTheGoal() throws Exception {
        String goal = mockMvc.perform(post("/api/saving-goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Notgroschen\", \"targetAmount\": 3000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long goalId = ((Number) JsonPath.read(goal, "$.id")).longValue();

        book("-300", "Waschmaschine", null, goalId);

        mockMvc.perform(get("/api/dashboard").param("month", month))
                .andExpect(jsonPath("$.expenses.actual").value(300.00))
                .andExpect(jsonPath("$.budget.actual").value(0.00));
        mockMvc.perform(get("/api/saving-goals"))
                .andExpect(jsonPath("$[0].name").value("Notgroschen"))
                .andExpect(jsonPath("$[0].saved").value(-300.00));
    }

    private String yearlyKfz() throws Exception {
        String json = mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Kfz-Versicherung", "amount": -1200, "categoryId": %d, "rhythmUnit": "MONTH",
                         "rhythmCount": 12, "anchorDate": "%s", "entryMode": "MANUAL", "validFrom": "%s"}"""
                        .formatted(category("Mobilität"), today, YearMonth.from(today).minusMonths(11))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.itemKey");
    }

    private void book(String amount, String description, String reserveItemKey, Long goalId) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": %s, "description": "%s", "date": "%s", "categoryId": %d,
                         "reserveItemKey": %s, "savingGoalId": %s}""".formatted(amount, description, today,
                        category("Mobilität"), reserveItemKey == null ? "null" : "\"" + reserveItemKey + "\"", goalId)))
                .andExpect(status().isCreated());
    }

    private long category(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
