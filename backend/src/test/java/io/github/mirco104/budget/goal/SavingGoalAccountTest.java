package io.github.mirco104.budget.goal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Ticket #145 (decision 101): a saving goal may lie on an account, which then has to cover it like its reserves. */
@SpringBootTest
@Transactional
class SavingGoalAccountTest {

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
    private long tagesgeld;

    @BeforeEach
    void signIn() throws Exception {
        user = TestUsers.create(userService, "person-b@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        today = LocalDate.now(clock);
        tagesgeld = id(mockMvc.perform(post("/api/bank-accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Tagesgeld\"}")).andExpect(status().isCreated()));
    }

    @Test
    void aGoalKeepsItsAccountAndAnotherUsersAccountIsRejected() throws Exception {
        long goal = id(goal("{\"name\": \"Notgroschen\", \"accountId\": %d}".formatted(tagesgeld))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(tagesgeld))
                .andExpect(jsonPath("$.accountName").value("Tagesgeld")));

        mockMvc.perform(put("/api/saving-goals/{id}", goal).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Notgroschen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").doesNotExist());

        User other = TestUsers.create(userService, "person-c@example.test");
        String accounts = TestUsers.mockMvcFor(context, other).perform(get("/api/bank-accounts"))
                .andReturn().getResponse().getContentAsString();
        long foreign = ((Number) JsonPath.read(accounts, "$[0].id")).longValue();
        goal("{\"name\": \"Urlaub\", \"accountId\": %d}".formatted(foreign))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("accountId"));
    }

    @Test
    void theAccountOfTheGoalHasToHoldWhatTheGoalSaved() throws Exception {
        YearMonth previous = YearMonth.from(today).minusMonths(1);
        book("{\"amount\": 1000, \"description\": \"Gehalt\", \"date\": \"%s\", \"categoryId\": %d}"
                .formatted(previous.atDay(1), category("Gehalt/Lohn (netto)")));
        book("{\"amount\": -300, \"description\": \"Einkauf\", \"date\": \"%s\", \"categoryId\": %d}"
                .formatted(previous.atDay(2), category("Lebensmittel")));
        long goal = id(goal("{\"name\": \"Notgroschen\", \"accountId\": %d}".formatted(tagesgeld)));
        // P10: the leftover of the last month (700 €) goes to the goal
        mockMvc.perform(post("/api/month-closings/{month}", previous).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"savingGoalId\": %d}".formatted(goal)))
                .andExpect(status().isOk());
        book("{\"amount\": 500, \"description\": \"Einzahlung\", \"date\": \"%s\", \"accountId\": %d, \"transfer\": true}"
                .formatted(today, tagesgeld));

        mockMvc.perform(get("/api/reserves/by-account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountName").value("Tagesgeld"))
                .andExpect(jsonPath("$[0].saved").value(0.00))
                .andExpect(jsonPath("$[0].goalsSaved").value(700.00))
                .andExpect(jsonPath("$[0].accountBalance").value(500.00))
                .andExpect(jsonPath("$[0].missing").value(200.00))
                .andExpect(jsonPath("$[0].goals[0].name").value("Notgroschen"))
                .andExpect(jsonPath("$[0].goals[0].saved").value(700.00))
                .andExpect(jsonPath("$[0].pots.length()").value(0));
    }

    @Test
    void mergingTheAccountMovesTheGoalAndDeletingItUnlinksTheGoal() throws Exception {
        long goal = id(goal("{\"name\": \"Notgroschen\", \"accountId\": %d}".formatted(tagesgeld)));
        long depot = id(mockMvc.perform(post("/api/bank-accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Depot\"}")));

        mockMvc.perform(post("/api/bank-accounts/{id}/merge", tagesgeld).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\": %d}".formatted(depot)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/saving-goals"))
                .andExpect(jsonPath("$[0].accountName").value("Depot"));

        // An account whose only link is a goal can be deleted; the goal stays without account
        mockMvc.perform(delete("/api/bank-accounts/{id}", depot)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/saving-goals"))
                .andExpect(jsonPath("$[0].id").value(goal))
                .andExpect(jsonPath("$[0].accountId").doesNotExist());
    }

    private ResultActions goal(String json) throws Exception {
        return mockMvc.perform(post("/api/saving-goals").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void book(String json) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long category(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
