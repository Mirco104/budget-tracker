package io.github.mirco104.budget.bankaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Ticket #139, R26: merging an account into another moves everything on it and deletes it. */
@SpringBootTest
@Transactional
class BankAccountMergeTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BankStatementRepository statementRepository;

    @Autowired
    private BankAccountRepository accountRepository;

    @Autowired
    private io.github.mirco104.budget.plan.PlannedItemRepository itemRepository;

    private MockMvc mockMvc;
    private User user;
    private long categoryId;
    private long girokonto;
    private long vrBank;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        user = TestUsers.create(userService, "person-f@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        categoryId = categoryRepository.findAllByOwner(user).getFirst().getId();
        girokonto = idOf(mockMvc.perform(get("/api/bank-accounts")), "$[0].id");
        vrBank = idOf(createAccount("VR-Bank"), "$.id");
    }

    @Test
    void movesBookingsItemsAndTheDefaultAndDeletesTheAccount() throws Exception {
        book(-12.30, "Bäckerei", girokonto);
        book(-40, "Tanken", girokonto);
        book(-5, "Schon auf VR", vrBank);
        long tagesgeld = idOf(createAccount("Tagesgeld"), "$.id");
        transfer(-100, "Aufs Tagesgeld", girokonto, tagesgeld);
        createItem(girokonto);
        mockMvc.perform(put("/api/bank-accounts/{id}", girokonto).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Girokonto\", \"archived\": false, \"defaultAccount\": true}"))
                .andExpect(status().isOk());

        merge(girokonto, vrBank)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(vrBank))
                .andExpect(jsonPath("$.account.defaultAccount").value(true))
                .andExpect(jsonPath("$.movedTransactions").value(3))
                .andExpect(jsonPath("$.deletedTransfers").value(0));

        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("VR-Bank"))
                .andExpect(jsonPath("$[0].defaultAccount").value(true));
        mockMvc.perform(get("/api/transactions").param("from", today.minusDays(1).toString())
                        .param("to", today.toString()))
                .andExpect(jsonPath("$[?(@.accountName == 'Girokonto')]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.accountName == 'VR-Bank')]", hasSize(4)))
                // The other side of a transfer to a third account now names the target
                .andExpect(jsonPath("$[?(@.accountName == 'Tagesgeld')].transferPeerAccountName")
                        .value(org.hamcrest.Matchers.contains("VR-Bank")));
        mockMvc.perform(get("/api/plan").param("month", YearMonth.from(today).toString()))
                .andExpect(status().isOk());
        assertThat(accountRepository.findById(girokonto)).isEmpty();
        assertThat(itemRepository.existsByAccountId(vrBank)).isTrue();
    }

    // Decision with the user: transfers between the two accounts would be transfers on one account, so both sides go
    @Test
    void deletesTransfersBetweenTheTwoAccountsAndTheBalancesOfTheMergedOne() throws Exception {
        book(-12.30, "Bäckerei", girokonto);
        transfer(-200, "Auf VR", girokonto, vrBank);
        balance(girokonto, 500);
        balance(vrBank, 1000);

        merge(girokonto, vrBank)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedTransactions").value(1))
                .andExpect(jsonPath("$.deletedTransfers").value(1));

        mockMvc.perform(get("/api/transactions").param("from", today.minusDays(1).toString())
                        .param("to", today.toString()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Bäckerei"));
        List<BankStatement> statements = statementRepository.findByOwnerOrderByDateAsc(user);
        assertThat(statements).hasSize(1);
        assertThat(statements.getFirst().getAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void rejectsTheSameAnUnknownOrAnArchivedTarget() throws Exception {
        merge(girokonto, girokonto)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetId"));
        merge(girokonto, 999_999)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetId"));
        mockMvc.perform(put("/api/bank-accounts/{id}", vrBank).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"VR-Bank\", \"archived\": true, \"defaultAccount\": false}"))
                .andExpect(status().isOk());
        merge(girokonto, vrBank)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetId"));
        mockMvc.perform(post("/api/bank-accounts/{id}/merge", girokonto).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetId"));
    }

    // R15: another user's account in the URL is 404, in the body 400
    @Test
    void scopesBothAccountsToTheUser() throws Exception {
        User other = TestUsers.create(userService, "person-g@example.test");
        long foreign = idOf(TestUsers.mockMvcFor(context, other).perform(get("/api/bank-accounts")), "$[0].id");

        merge(foreign, vrBank).andExpect(status().isNotFound());
        merge(vrBank, foreign)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetId"));
    }

    private ResultActions merge(long id, long targetId) throws Exception {
        return mockMvc.perform(post("/api/bank-accounts/{id}/merge", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetId\": %d}".formatted(targetId)));
    }

    private ResultActions createAccount(String name) throws Exception {
        return mockMvc.perform(post("/api/bank-accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated());
    }

    private void book(double amount, String description, long accountId) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": %s, "description": "%s", "date": "%s", "categoryId": %d, "accountId": %d}"""
                        .formatted(amount, description, today, categoryId, accountId)))
                .andExpect(status().isCreated());
    }

    private void transfer(double amount, String description, long from, long to) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": %s, "description": "%s", "date": "%s", "transfer": true, "accountId": %d,
                         "targetAccountId": %d}""".formatted(amount, description, today, from, to)))
                .andExpect(status().isCreated());
    }

    private void createItem(long accountId) throws Exception {
        mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Miete", "amount": -900, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                         "anchorDate": "%s", "validFrom": "%s", "accountId": %d}"""
                        .formatted(categoryId, today.plusMonths(1).withDayOfMonth(1), YearMonth.from(today), accountId)))
                .andExpect(status().isCreated());
    }

    private void balance(long accountId, int amount) throws Exception {
        mockMvc.perform(post("/api/bank-accounts/{id}/balances", accountId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"%s\", \"amount\": %d}".formatted(today, amount)))
                .andExpect(status().isOk());
    }

    private static long idOf(ResultActions result, String path) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, path)).longValue();
    }
}
