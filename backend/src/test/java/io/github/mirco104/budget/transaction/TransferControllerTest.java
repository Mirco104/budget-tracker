package io.github.mirco104.budget.transaction;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** R27, US31: a transfer between own accounts has no category and counts in no calculation. */
@SpringBootTest
@Transactional
class TransferControllerTest {

    /** Today is 2026-11-15, so October is a past month and counts for the averages (R23). */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(LocalDate.of(2026, 11, 15).atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant(),
                    ZoneId.of("Europe/Berlin"));
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    private MockMvc mockMvc;
    private long wohnen;
    private long gehalt;

    @BeforeEach
    void signIn() {
        User user = TestUsers.create(userService, "person-f@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        wohnen = categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals("Wohnen")).findFirst().orElseThrow().getId();
        gehalt = categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().startsWith("Gehalt")).findFirst().orElseThrow().getId();
    }

    @Test
    void createsATransferWithoutCategory() throws Exception {
        transfer(-500, "2026-10-02")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transfer").value(true))
                .andExpect(jsonPath("$.categoryId").isEmpty())
                .andExpect(jsonPath("$.categoryName").isEmpty())
                .andExpect(jsonPath("$.accountName").value("Girokonto"));

        mockMvc.perform(get("/api/transactions").param("month", "2026-10"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].transfer").value(true));
    }

    @Test
    void aTransferHasNoCategoryAndOtherBookingsNeedOne() throws Exception {
        post("""
                {"amount": -500, "description": "Umbuchung", "date": "2026-10-02", "categoryId": %d,
                 "transfer": true}""".formatted(wohnen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        post("""
                {"amount": -500, "description": "Einkauf", "date": "2026-10-02"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"))
                .andExpect(jsonPath("$.errors[0].message").value("Die Kategorie ist Pflicht."));
        // A normal booking needs no field transfer
        book(-20, wohnen, "2026-10-03").andExpect(status().isCreated())
                .andExpect(jsonPath("$.transfer").value(false));
    }

    @Test
    void aBookingCanBecomeATransferAndBack() throws Exception {
        long id = idOf(book(-500, wohnen, "2026-10-02"));

        mockMvc.perform(put("/api/transactions/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -500, "description": "Aufs Tagesgeld", "date": "2026-10-02", "transfer": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer").value(true))
                .andExpect(jsonPath("$.categoryId").isEmpty());
        mockMvc.perform(put("/api/transactions/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -500, "description": "Miete", "date": "2026-10-02", "categoryId": %d}"""
                        .formatted(wohnen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer").value(false))
                .andExpect(jsonPath("$.categoryId").value(wohnen));
    }

    @Test
    void transfersCountInNoCalculation() throws Exception {
        book(3000, gehalt, "2026-10-01");
        book(-900, wohnen, "2026-10-03");
        transfer(-1000, "2026-10-05");
        transfer(1000, "2026-10-05");
        // A transfer before every other entry does not move the first entry month (R14, R23)
        transfer(-50, "2025-01-10");

        mockMvc.perform(get("/api/dashboard").param("month", "2026-10"))
                .andExpect(jsonPath("$.incomeTotal").value(3000.0))
                .andExpect(jsonPath("$.expenses.actual").value(900.0))
                .andExpect(jsonPath("$.surplus").value(2100.0));
        mockMvc.perform(get("/api/dashboard").param("month", "2026-11"))
                .andExpect(jsonPath("$.averageIncomeMonths").value(1))
                .andExpect(jsonPath("$.averageIncome").value(3000.0))
                .andExpect(jsonPath("$.averageExpenses").value(900.0));
        mockMvc.perform(get("/api/history").param("from", "2025-01").param("to", "2026-10"))
                .andExpect(jsonPath("$.months").value(1))
                .andExpect(jsonPath("$.total.expenses").value(900.0));
        mockMvc.perform(get("/api/categories").param("month", "2026-10"))
                .andExpect(jsonPath("$[?(@.name == 'Gehalt/Lohn (netto)')].incomeOnly").value(true));
    }

    @Test
    void transfersAreMissingInTheForecast() throws Exception {
        transfer(-1000, "2026-11-20");
        book(-30, wohnen, "2026-11-21");

        mockMvc.perform(get("/api/reserves").param("month", "2026-11"))
                .andExpect(jsonPath("$.forecast[0].total").value(30.0))
                .andExpect(jsonPath("$.forecast[0].entries", hasSize(1)));
    }

    @Test
    void aTransferCannotFulfilATerm() throws Exception {
        String json = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "Sparen", "amount": -500, "categoryId": %d, "rhythmUnit": "MONTH",
                                 "rhythmCount": 1, "anchorDate": "2026-10-01", "entryMode": "MANUAL", "validFrom": "2026-10"}"""
                                .formatted(wohnen)))
                .andReturn().getResponse().getContentAsString();
        long item = ((Number) JsonPath.read(json, "$.id")).longValue();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                                {"amount": -500, "description": "Sparen", "date": "2026-10-01", "transfer": true,
                                 "replaces": {"itemId": %d, "date": "2026-10-01"}}""".formatted(item)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("replaces"));
        mockMvc.perform(get("/api/transactions").param("month", "2026-10"))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void theExportMarksTransfers() throws Exception {
        transfer(-500, "2026-10-02");

        mockMvc.perform(get("/api/account/export"))
                .andExpect(jsonPath("$.transactions[0].transfer").value(true))
                .andExpect(jsonPath("$.transactions[0].category").isEmpty());
    }

    private ResultActions transfer(int amount, String date) throws Exception {
        return post("""
                {"amount": %d, "description": "Umbuchung", "date": "%s", "transfer": true}"""
                .formatted(amount, date));
    }

    private ResultActions book(int amount, long categoryId, String date) throws Exception {
        return post("""
                {"amount": %d, "description": "Buchung", "date": "%s", "categoryId": %d}"""
                .formatted(amount, date, categoryId));
    }

    private ResultActions post(String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
