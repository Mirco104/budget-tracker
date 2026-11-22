package io.github.mirco104.budget.bankaccount;

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
    void savingCountsAsSavingAndBookingsOnSavingsAccountsCountByTheirKind() throws Exception {
        book("{\"amount\": 2000, \"description\": \"Gehalt\", \"date\": \"%s\", \"categoryId\": %d}"
                .formatted(today, category("Gehalt/Lohn (netto)")));
        // Sparen: a transfer from the everyday account
        book("{\"amount\": -300, \"description\": \"Auf Tagesgeld\", \"date\": \"%s\", \"saving\": true}".formatted(today));
        // 27f4 (decision 60, P1): interest on the savings account is income like on every account
        book("{\"amount\": 5, \"description\": \"Zinsen\", \"date\": \"%s\", \"categoryId\": %d, \"accountId\": %d}"
                .formatted(today, category("Kapitalerträge"), tagesgeld));

        mockMvc.perform(get("/api/dashboard").param("month", YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.incomeTotal").value(2005.00))
                .andExpect(jsonPath("$.expenses.actual").value(0.00))
                .andExpect(jsonPath("$.saving.actual").value(300.00));
        mockMvc.perform(get("/api/accounts"))
                .andExpect(jsonPath("$[0].role").value("EVERYDAY"))
                .andExpect(jsonPath("$[0].total").value(1700.00))
                .andExpect(jsonPath("$[1].role").value("SAVINGS"))
                .andExpect(jsonPath("$[1].accounts[0].name").value("Tagesgeld"))
                .andExpect(jsonPath("$[1].total").value(5.00));
    }

    // R36 (stage 2): the balance on the last day of a month gives the variable spending of the month
    @Test
    void theBalanceOnTheLastDayOfTheMonthGivesTheVariableSpending() throws Exception {
        java.time.YearMonth previous = java.time.YearMonth.from(today).minusMonths(1);
        mockMvc.perform(post("/api/bank-accounts/{id}/balances", girokonto).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"%s\", \"amount\": 1000}".formatted(previous.minusMonths(1).atEndOfMonth())))
                .andExpect(status().isOk());
        book("{\"amount\": 2000, \"description\": \"Gehalt\", \"date\": \"%s\", \"categoryId\": %d}"
                .formatted(previous.atDay(28), category("Gehalt/Lohn (netto)")));
        mockMvc.perform(get("/api/accounts/variable").param("month", previous.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").doesNotExist());

        mockMvc.perform(post("/api/bank-accounts/{id}/balances", girokonto).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"%s\", \"amount\": 2600}".formatted(previous.atEndOfMonth())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/accounts/variable").param("month", previous.toString()))
                .andExpect(jsonPath("$.amount").value(400.00))
                .andExpect(jsonPath("$.moreThanExpected").value(false))
                .andExpect(jsonPath("$.accounts").value(1));
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
    void theHistoryListsTheBalancesNewestFirstAndAWrongOneCanBeDeleted() throws Exception {
        book("{\"amount\": -40, \"description\": \"Einkauf\", \"date\": \"%s\", \"categoryId\": %d}"
                .formatted(today, category("Lebensmittel")));
        balance(girokonto, today.minusDays(1), "1000");
        balance(girokonto, today, "900");
        balance(tagesgeld, today, "50");

        String history = mockMvc.perform(get("/api/bank-accounts/{id}/balances", girokonto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value(today.toString()))
                .andExpect(jsonPath("$[0].amount").value(900.00))
                .andExpect(jsonPath("$[0].appBalance").value(960.00))
                .andExpect(jsonPath("$[0].difference").value(-60.00))
                .andExpect(jsonPath("$[1].date").value(today.minusDays(1).toString()))
                .andReturn().getResponse().getContentAsString();
        long newest = ((Number) JsonPath.read(history, "$[0].id")).longValue();

        // Another user sees neither the account nor its balances
        User other = TestUsers.create(userService, "person-c@example.test");
        MockMvc otherMvc = TestUsers.mockMvcFor(context, other);
        otherMvc.perform(get("/api/bank-accounts/{id}/balances", girokonto)).andExpect(status().isNotFound());
        otherMvc.perform(delete("/api/bank-accounts/{id}/balances/{b}", girokonto, newest))
                .andExpect(status().isNotFound());
        // A balance of another account is not found under this one
        mockMvc.perform(delete("/api/bank-accounts/{id}/balances/{b}", tagesgeld, newest))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/bank-accounts/{id}/balances/{b}", girokonto, newest))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bank-accounts/{id}/balances", girokonto))
                .andExpect(jsonPath("$.length()").value(1));
        // The app balance counts from the balance before again: 1000 - 40
        mockMvc.perform(get("/api/accounts"))
                .andExpect(jsonPath("$[0].accounts[0].balance").value(960.00));
        mockMvc.perform(delete("/api/bank-accounts/{id}/balances/{b}", girokonto, newest))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPotLinkedToTheSavingsAccountIsCoveredByIt() throws Exception {
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
                // 27f4 (decision 60): an everyday account may hold a reserve too
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserveAccountId").value(girokonto));
        mockMvc.perform(put("/api/planned-items/{id}/reserve-account", itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": %d}".formatted(tagesgeld)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserveAccountId").value(tagesgeld));

        // 27f2: the pot keeps its calculated balance (one month of 100 €); the account covers it
        mockMvc.perform(get("/api/dashboard").param("month", YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.reserves[0].balance").value(100.00));
        mockMvc.perform(get("/api/reserves/by-account"))
                .andExpect(jsonPath("$[0].accountName").value("Tagesgeld"))
                .andExpect(jsonPath("$[0].saved").value(100.00))
                .andExpect(jsonPath("$[0].target").value(1200.00))
                .andExpect(jsonPath("$[0].accountBalance").value(700.00))
                .andExpect(jsonPath("$[0].missing").value(0.00))
                .andExpect(jsonPath("$[0].pots[0].name").value("Kfz-Versicherung"));
    }

    @Test
    void aPaymentFromTheLinkedSavingsAccountIsPaidFromTheReserveAndEmptiesIt() throws Exception {
        String item = mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Kfz-Versicherung", "amount": -1200, "categoryId": %d, "rhythmUnit": "MONTH",
                         "rhythmCount": 12, "anchorDate": "%s", "entryMode": "MANUAL", "validFrom": "%s"}"""
                        .formatted(category("Mobilität"), today, YearMonth.from(today).minusMonths(11))))
                .andReturn().getResponse().getContentAsString();
        long itemId = ((Number) JsonPath.read(item, "$.id")).longValue();
        mockMvc.perform(put("/api/planned-items/{id}/reserve-account", itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": %d}".formatted(tagesgeld)))
                .andExpect(status().isOk());
        book("{\"amount\": 1300, \"description\": \"Einzahlung\", \"date\": \"%s\", \"accountId\": %d, \"transfer\": true}"
                .formatted(today.minusMonths(1), tagesgeld));
        // 27f4 (decision 60): the expense from the savings account counts (P1) and is paid from the reserve (P8)
        book("{\"amount\": -1200, \"description\": \"Kfz-Versicherung\", \"date\": \"%s\", \"categoryId\": %d, \"accountId\": %d}"
                .formatted(today, category("Mobilität"), tagesgeld));

        mockMvc.perform(get("/api/dashboard").param("month", YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.expenses.actual").value(1200.00))
                .andExpect(jsonPath("$.fromReserves").value(1200.00))
                .andExpect(jsonPath("$.categories[?(@.name == 'Mobilität')].actual").value(100.00));
        // After the payment the pot is empty: the account has to hold nothing for it any more
        mockMvc.perform(get("/api/reserves/by-account"))
                .andExpect(jsonPath("$[0].saved").value(0.00))
                .andExpect(jsonPath("$[0].accountBalance").value(100.00))
                .andExpect(jsonPath("$[0].missing").value(0.00));
    }

    // P12 (27h): the lower limit replaces the comfort amount; everyday accounts start at 0 €, others without one
    @Test
    void aBalanceBelowTheLowerLimitIsMarked() throws Exception {
        book("{\"amount\": 400, \"description\": \"Einzahlung\", \"date\": \"%s\", \"accountId\": %d, \"transfer\": true}"
                .formatted(today, tagesgeld));
        mockMvc.perform(get("/api/accounts"))
                .andExpect(jsonPath("$[0].accounts[0].lowerLimitMode").value("ZERO"))
                .andExpect(jsonPath("$[0].accounts[0].lowerLimit").value(0))
                .andExpect(jsonPath("$[0].accounts[0].belowLimit").value(false));
        mockMvc.perform(put("/api/bank-accounts/{id}/lower-limit", tagesgeld).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\": \"CUSTOM\", \"amount\": 500}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/accounts"))
                .andExpect(jsonPath("$[1].accounts[0].lowerLimitMode").value("CUSTOM"))
                .andExpect(jsonPath("$[1].accounts[0].lowerLimit").value(500.00))
                .andExpect(jsonPath("$[1].accounts[0].belowLimit").value(true));
        mockMvc.perform(put("/api/bank-accounts/{id}/lower-limit", tagesgeld).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\": \"CUSTOM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        mockMvc.perform(put("/api/bank-accounts/{id}/lower-limit", tagesgeld).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\": \"CUSTOM\", \"amount\": -1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/bank-accounts/{id}/lower-limit", tagesgeld).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\": \"OFF\", \"amount\": 500}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/accounts"))
                .andExpect(jsonPath("$[1].accounts[0].lowerLimit").doesNotExist())
                .andExpect(jsonPath("$[1].accounts[0].belowLimit").value(false));
    }

    private void balance(long account, LocalDate date, String amount) throws Exception {
        mockMvc.perform(post("/api/bank-accounts/{id}/balances", account).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"%s\", \"amount\": %s}".formatted(date, amount)))
                .andExpect(status().isOk());
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
