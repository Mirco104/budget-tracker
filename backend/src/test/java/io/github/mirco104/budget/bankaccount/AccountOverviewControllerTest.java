package io.github.mirco104.budget.bankaccount;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Konten (AP 27f): roles, the kind Sparen (P1), app balance and reconciliation (P11), linked reserve pot (P8). */
@SpringBootTest
@Transactional
class AccountOverviewControllerTest {

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
    private long girokonto;
    private long tagesgeld;

    @BeforeEach
    void signIn() throws Exception {
        user = TestUsers.create(userService, "person-b@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        today = LocalDate.now(clock);
        String accounts = mockMvc.perform(get("/api/bank-accounts")).andReturn().getResponse().getContentAsString();
        girokonto = ((Number) JsonPath.read(accounts, "$[0].id")).longValue();
        String created = mockMvc.perform(post("/api/bank-accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Tagesgeld\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        tagesgeld = ((Number) JsonPath.read(created, "$.id")).longValue();
        mockMvc.perform(put("/api/bank-accounts/{id}", tagesgeld).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Tagesgeld\", \"archived\": false, \"defaultAccount\": false, \"role\": \"SAVINGS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SAVINGS"));
    }

    @Test
    void savingCountsAsSavingAndBookingsOnSavingsAccountsCountNowhere() throws Exception {
        book("{\"amount\": 2000, \"description\": \"Gehalt\", \"date\": \"%s\", \"categoryId\": %d}"
                .formatted(today, category("Gehalt/Lohn (netto)")));
        // Sparen: a transfer from the everyday account
        book("{\"amount\": -300, \"description\": \"Auf Tagesgeld\", \"date\": \"%s\", \"saving\": true}".formatted(today));
        // Interest on the savings account counts in no tile (P1)
        book("{\"amount\": 5, \"description\": \"Zinsen\", \"date\": \"%s\", \"categoryId\": %d, \"accountId\": %d}"
                .formatted(today, category("Kapitalerträge"), tagesgeld));

        mockMvc.perform(get("/api/dashboard").param("month", YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.incomeTotal").value(2000.00))
                .andExpect(jsonPath("$.expenses.actual").value(0.00))
                .andExpect(jsonPath("$.saving.actual").value(300.00));
        mockMvc.perform(get("/api/accounts"))
                .andExpect(jsonPath("$[0].role").value("EVERYDAY"))
                .andExpect(jsonPath("$[0].total").value(1700.00))
                .andExpect(jsonPath("$[1].role").value("SAVINGS"))
                .andExpect(jsonPath("$[1].accounts[0].name").value("Tagesgeld"))
                .andExpect(jsonPath("$[1].total").value(5.00));
    }

    @Test
    void aBankBalanceShowsTheDifferenceAndBecomesTheStart() throws Exception {
        book("{\"amount\": -40, \"description\": \"Einkauf\", \"date\": \"%s\", \"categoryId\": %d}"
                .formatted(today, category("Lebensmittel")));

        mockMvc.perform(post("/api/bank-accounts/{id}/balances", girokonto).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"%s\", \"amount\": 1000}".formatted(today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appBalance").value(-40.00))
                .andExpect(jsonPath("$.difference").value(1040.00));
        mockMvc.perform(get("/api/accounts"))
                .andExpect(jsonPath("$[0].accounts[0].balance").value(1000.00))
                .andExpect(jsonPath("$[0].accounts[0].statementAmount").value(1000.00));
        mockMvc.perform(post("/api/bank-accounts/{id}/balances", girokonto).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"%s\", \"amount\": 1}".formatted(today.plusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPotLinkedToTheSavingsAccountHasItsBalance() throws Exception {
        String item = mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Kfz-Versicherung", "amount": -1200, "categoryId": %d, "rhythmUnit": "MONTH",
                         "rhythmCount": 12, "anchorDate": "%s", "entryMode": "MANUAL", "validFrom": "%s"}"""
                        .formatted(category("Mobilität"), today.plusMonths(2), YearMonth.from(today))))
                .andReturn().getResponse().getContentAsString();
        long itemId = ((Number) JsonPath.read(item, "$.id")).longValue();
        book("{\"amount\": 700, \"description\": \"Einzahlung\", \"date\": \"%s\", \"accountId\": %d, \"transfer\": true}"
                .formatted(today.minusMonths(1), tagesgeld));

        mockMvc.perform(put("/api/planned-items/{id}/reserve-account", itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": %d}".formatted(girokonto)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/planned-items/{id}/reserve-account", itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": %d}".formatted(tagesgeld)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserveAccountId").value(tagesgeld));

        mockMvc.perform(get("/api/dashboard").param("month", YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.reserves[0].balance").value(700.00));
    }

    private void book(String json) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private long category(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
