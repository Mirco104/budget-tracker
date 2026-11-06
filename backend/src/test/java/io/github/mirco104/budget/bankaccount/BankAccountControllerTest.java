package io.github.mirco104.budget.bankaccount;

import static org.hamcrest.Matchers.hasSize;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** R26, US30: bank accounts, the default account and the account of bookings. */
@SpringBootTest
@Transactional
class BankAccountControllerTest {

    // Example IBAN from the ISO 13616 documentation, not a real account of anyone
    private static final String IBAN = "DE89370400440532013000";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    private MockMvc mockMvc;
    private long categoryId;

    @BeforeEach
    void signIn() {
        User user = TestUsers.create(userService, "person-e@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        categoryId = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(user).getFirst().getId();
    }

    @Test
    void newUserHasTheDefaultAccountGirokonto() throws Exception {
        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Girokonto"))
                .andExpect(jsonPath("$[0].defaultAccount").value(true))
                .andExpect(jsonPath("$[0].archived").value(false))
                .andExpect(jsonPath("$[0].used").value(false))
                .andExpect(jsonPath("$[0].iban").isEmpty());
    }

    @Test
    void createsAnAccountWithNormalizedIban() throws Exception {
        create("{\"name\": \" Tagesgeld \", \"iban\": \"de89 3704 0044 0532 0130 00\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tagesgeld"))
                .andExpect(jsonPath("$.iban").value(IBAN))
                .andExpect(jsonPath("$.defaultAccount").value(false));

        // Default account first, then by name
        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("Girokonto", "Tagesgeld")));
    }

    @Test
    void rejectsInvalidIbanAndDuplicates() throws Exception {
        create("{\"name\": \"Tagesgeld\", \"iban\": \"DE88370400440532013000\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("iban"));
        create("{\"name\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        create("{\"name\": \"girokonto\"}").andExpect(status().isConflict());
        create("{\"name\": \"Tagesgeld\", \"iban\": \"" + IBAN + "\"}").andExpect(status().isCreated());
        create("{\"name\": \"Depot\", \"iban\": \"" + IBAN + "\"}").andExpect(status().isConflict());
    }

    @Test
    void makesAnotherAccountTheDefault() throws Exception {
        long girokonto = defaultAccountId();
        long tagesgeld = idOf(create("{\"name\": \"Tagesgeld\"}"));

        update(tagesgeld, "{\"name\": \"Tagesgeld\", \"archived\": false, \"defaultAccount\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAccount").value(true));

        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$[0].id").value(tagesgeld))
                .andExpect(jsonPath("$[1].id").value(girokonto))
                .andExpect(jsonPath("$[1].defaultAccount").value(false));
        // A booking without account goes to the new default account
        book("").andExpect(jsonPath("$.accountId").value(tagesgeld));
    }

    @Test
    void defaultAccountCannotBeUnsetArchivedOrDeleted() throws Exception {
        long girokonto = defaultAccountId();

        update(girokonto, "{\"name\": \"Girokonto\", \"archived\": false, \"defaultAccount\": false}")
                .andExpect(status().isConflict());
        update(girokonto, "{\"name\": \"Girokonto\", \"archived\": true, \"defaultAccount\": true}")
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/bank-accounts/{id}", girokonto)).andExpect(status().isConflict());
    }

    @Test
    void renamesArchivesAndDeletesAnUnusedAccount() throws Exception {
        long tagesgeld = idOf(create("{\"name\": \"Tagesgeld\"}"));

        update(tagesgeld, "{\"name\": \"Notgroschen\", \"iban\": \"" + IBAN + "\", \"archived\": true, "
                + "\"defaultAccount\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Notgroschen"))
                .andExpect(jsonPath("$.iban").value(IBAN))
                .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(delete("/api/bank-accounts/{id}", tagesgeld)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bank-accounts")).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(delete("/api/bank-accounts/{id}", tagesgeld)).andExpect(status().isNotFound());
    }

    @Test
    void usedAccountCanOnlyBeArchived() throws Exception {
        long tagesgeld = idOf(create("{\"name\": \"Tagesgeld\"}"));
        book(", \"accountId\": " + tagesgeld).andExpect(status().isCreated());

        mockMvc.perform(get("/api/bank-accounts")).andExpect(jsonPath("$[1].used").value(true));
        mockMvc.perform(delete("/api/bank-accounts/{id}", tagesgeld)).andExpect(status().isConflict());
        update(tagesgeld, "{\"name\": \"Tagesgeld\", \"archived\": true, \"defaultAccount\": false}")
                .andExpect(status().isOk());
        // Archived accounts stay bookable, so existing entries can be edited
        book(", \"accountId\": " + tagesgeld).andExpect(status().isCreated());
    }

    @Test
    void bookingsCarryTheirAccount() throws Exception {
        long girokonto = defaultAccountId();
        long tagesgeld = idOf(create("{\"name\": \"Tagesgeld\"}"));

        book("").andExpect(jsonPath("$.accountId").value(girokonto))
                .andExpect(jsonPath("$.accountName").value("Girokonto"));
        long id = idOf(book(", \"accountId\": " + tagesgeld)
                .andExpect(jsonPath("$.accountName").value("Tagesgeld")));
        mockMvc.perform(put("/api/transactions/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(transaction("")))
                .andExpect(jsonPath("$.accountId").value(girokonto));

        mockMvc.perform(post("/api/recurring-transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -900, "description": "Miete", "categoryId": %d, "interval": "MONTHLY",
                                 "startDate": "2026-10-01", "accountId": %d}""".formatted(categoryId, tagesgeld)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountName").value("Tagesgeld"));
        mockMvc.perform(get("/api/transactions").param("month", "2026-10"))
                .andExpect(jsonPath("$[?(@.kind == 'RECURRING')].accountName").value("Tagesgeld"));
    }

    @Test
    void unknownAccountInTheBodyIsInvalid() throws Exception {
        book(", \"accountId\": 999999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("accountId"));
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/bank-accounts").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(long id, String body) throws Exception {
        return mockMvc.perform(put("/api/bank-accounts/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions book(String extra) throws Exception {
        return mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .content(transaction(extra)));
    }

    private String transaction(String extra) {
        return """
                {"amount": -12.30, "description": "Bäckerei", "date": "2026-10-03", "categoryId": %d%s}"""
                .formatted(categoryId, extra);
    }

    private long defaultAccountId() throws Exception {
        String json = mockMvc.perform(get("/api/bank-accounts")).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
    }

    private static long idOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
