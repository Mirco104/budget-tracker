package io.github.mirco104.budget.recurring;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** R28, US35: a booking replaces a planned occurrence of a series, so nothing counts twice. */
@SpringBootTest
@Transactional
class ReplacementControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    private MockMvc mockMvc;
    private User user;
    private long wohnen;
    private long rent;

    @BeforeEach
    void signIn() throws Exception {
        user = TestUsers.create(userService, "person-k@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        wohnen = category("Wohnen");
        rent = idOf(series(-900, "Miete Wohnung", wohnen, "2026-10-01"));
    }

    @Test
    void aBookingReplacesTheOccurrenceAndCountsAsRecurring() throws Exception {
        book(-905, "Miete November", "2026-11-02", wohnen, rent, "2026-11-01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replaces.recurringId").value(rent))
                .andExpect(jsonPath("$.replaces.date").value("2026-11-01"));

        mockMvc.perform(get("/api/transactions").param("month", "2026-11"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].kind").value("SINGLE"))
                .andExpect(jsonPath("$[0].replaces.recurringId").value(rent));
        mockMvc.perform(get("/api/dashboard").param("month", "2026-11"))
                .andExpect(jsonPath("$.expenses.actual").value(905.0))
                .andExpect(jsonPath("$.expenses.recurring").value(905.0))
                .andExpect(jsonPath("$.expenses.oneOff").value(0.0));
        // Other months keep their occurrence
        mockMvc.perform(get("/api/transactions").param("month", "2026-12"))
                .andExpect(jsonPath("$[0].kind").value("RECURRING"));
    }

    @Test
    void deletingTheBookingBringsTheOccurrenceBack() throws Exception {
        long id = idOf(book(-905, "Miete November", "2026-11-02", wohnen, rent, "2026-11-01"));

        mockMvc.perform(delete("/api/transactions/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions").param("month", "2026-11"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].kind").value("RECURRING"));
    }

    @Test
    void onlyACountingOccurrenceOfAnOwnSeriesCanBeReplaced() throws Exception {
        book(-905, "Miete", "2026-11-02", wohnen, rent, "2026-11-02")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("replaces"));
        book(-905, "Miete", "2026-11-02", wohnen, rent, "2026-11-01").andExpect(status().isCreated());
        // Replaced already
        book(-905, "Miete", "2026-11-02", wohnen, rent, "2026-11-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("replaces"));

        User other = TestUsers.create(userService, "person-l@example.test");
        MockMvc asOther = TestUsers.mockMvcFor(context, other);
        long othersCategory = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(other).getFirst().getId();
        asOther.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -900, "description": "Fremd", "date": "2026-12-01", "categoryId": %d,
                         "replaces": {"recurringId": %d, "date": "2026-12-01"}}""".formatted(othersCategory, rent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("replaces"));
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -900, "description": "Umbuchung", "date": "2026-12-01", "transfer": true,
                         "replaces": {"recurringId": %d, "date": "2026-12-01"}}""".formatted(rent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("replaces"));
    }

    @Test
    void aTransferReplacesNoOccurrence() throws Exception {
        long id = idOf(book(-905, "Miete", "2026-11-02", wohnen, rent, "2026-11-01"));

        mockMvc.perform(put("/api/transactions/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -905, "description": "Doch Umbuchung", "date": "2026-11-02", "transfer": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replaces").isEmpty());

        mockMvc.perform(get("/api/transactions").param("month", "2026-11"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.kind == 'RECURRING')]", hasSize(1)));
    }

    @Test
    void anEditKeepsTheLink() throws Exception {
        long id = idOf(book(-905, "Miete", "2026-11-02", wohnen, rent, "2026-11-01"));

        mockMvc.perform(put("/api/transactions/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -910, "description": "Miete mit Nachzahlung", "date": "2026-11-02", "categoryId": %d}"""
                        .formatted(wohnen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replaces.recurringId").value(rent));
    }

    @Test
    void splittingTheSeriesMovesTheLinkWhenTheNewSeriesHasTheDate() throws Exception {
        book(-905, "Miete Dezember", "2026-12-02", wohnen, rent, "2026-12-01");

        // "Dieser und alle folgenden" from November, new series on the same day of the month
        String json = mockMvc.perform(put("/api/recurring-transactions/{id}/occurrences/{date}", rent, "2026-11-01")
                        .param("scope", "FOLLOWING").contentType(MediaType.APPLICATION_JSON).content("""
                                {"amount": -950, "description": "Miete Wohnung", "categoryId": %d, "interval": "MONTHLY",
                                 "startDate": "2026-11-01"}""".formatted(wohnen)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long following = ((Number) JsonPath.read(json, "$.id")).longValue();

        mockMvc.perform(get("/api/transactions").param("month", "2026-12"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].replaces.recurringId").value(following));
    }

    @Test
    void splittingTheSeriesDropsTheLinkWhenTheNewSeriesHasNoOccurrenceOnTheDate() throws Exception {
        book(-905, "Miete Dezember", "2026-12-02", wohnen, rent, "2026-12-01");

        mockMvc.perform(put("/api/recurring-transactions/{id}/occurrences/{date}", rent, "2026-11-01")
                        .param("scope", "FOLLOWING").contentType(MediaType.APPLICATION_JSON).content("""
                                {"amount": -950, "description": "Miete Wohnung", "categoryId": %d, "interval": "MONTHLY",
                                 "startDate": "2026-11-15"}""".formatted(wohnen)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/transactions").param("month", "2026-12"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.kind == 'SINGLE')].replaces", contains(nullValue())));
    }

    @Test
    void deletingTheSeriesKeepsTheBooking() throws Exception {
        book(-905, "Miete November", "2026-11-02", wohnen, rent, "2026-11-01");

        mockMvc.perform(delete("/api/recurring-transactions/{id}/occurrences/{date}", rent, "2026-10-01")
                        .param("scope", "FOLLOWING"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions").param("month", "2026-11"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Miete November"))
                .andExpect(jsonPath("$[0].replaces").isEmpty());
    }

    // --- R28 matches in the booking dialog and the import ---

    @Test
    void theSuggestionNamesTheOccurrenceAndItsCategory() throws Exception {
        mockMvc.perform(get("/api/booking-suggestion").param("text", "Miete Oktober")
                        .param("amount", "-900").param("date", "2026-11-03"))
                .andExpect(jsonPath("$.categoryId").value(wohnen))
                .andExpect(jsonPath("$.occurrence.recurringId").value(rent))
                .andExpect(jsonPath("$.occurrence.date").value("2026-11-01"))
                .andExpect(jsonPath("$.occurrence.description").value("Miete Wohnung"))
                .andExpect(jsonPath("$.occurrence.amount").value(900.0));
        // Without date, or 6 days away, there is no occurrence
        mockMvc.perform(get("/api/booking-suggestion").param("text", "Miete").param("amount", "-900"))
                .andExpect(jsonPath("$.occurrence").isEmpty());
        mockMvc.perform(get("/api/booking-suggestion").param("text", "Miete").param("amount", "-900")
                        .param("date", "2026-11-07"))
                .andExpect(jsonPath("$.occurrence").isEmpty());
    }

    @Test
    void theImportReplacesOccurrencesAndItsUndoBringsThemBack() throws Exception {
        long nebenkosten = category("Nebenkosten");
        long power = idOf(series(-80, "Strom Stadtwerke", nebenkosten, "2026-01-15"));
        String file = """
                "Booking Date","Value Date","Partner Name","Partner Iban",Type,"Payment Reference","Account Name","Amount (EUR)"
                2026-11-16,2026-11-16,"Stadtwerke Nord",,"Direct Debit","Abschlag November",Hauptkonto,-84.00
                """;
        String preview = mockMvc.perform(multipart("/api/imports/preview")
                        .file(new MockMultipartFile("file", "n26.csv", "text/csv", file.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].replaces.recurringId").value(power))
                .andExpect(jsonPath("$.entries[0].replaces.date").value("2026-11-15"))
                .andExpect(jsonPath("$.entries[0].categoryId").value(nebenkosten))
                .andExpect(jsonPath("$.entries[0].certain").value(true))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> entry = JsonPath.<List<Map<String, Object>>>read(preview, "$.entries").getFirst();

        String result = mockMvc.perform(post("/api/imports").contentType(MediaType.APPLICATION_JSON).content("""
                        {"profile": "N26", "sources": [{"key": "N26:Hauptkonto", "newAccountName": "N26 Hauptkonto"}],
                         "rows": [{"fingerprint": "%s", "source": "N26:Hauptkonto", "date": "2026-11-16", "amount": -84.00,
                                   "description": "%s", "counterparty": "Stadtwerke Nord", "categoryId": %d,
                                   "replaces": {"recurringId": %d, "date": "2026-11-15"}}]}"""
                        .formatted(entry.get("fingerprint"), entry.get("description"), nebenkosten, power)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/transactions").param("from", "2026-11-14").param("to", "2026-11-16"))
                .andExpect(jsonPath("$[?(@.recurringId == %d)]".formatted(power), empty()))
                .andExpect(jsonPath("$[?(@.description == 'Stadtwerke Nord · Abschlag November')].replaces.recurringId")
                        .value((int) power));

        long batch = ((Number) JsonPath.read(result, "$.batch.id")).longValue();
        mockMvc.perform(delete("/api/imports/{id}", batch)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/transactions").param("from", "2026-11-14").param("to", "2026-11-16"))
                .andExpect(jsonPath("$[?(@.recurringId == %d)]".formatted(power), hasSize(1)));
    }

    private ResultActions series(int amount, String description, long categoryId, String start) throws Exception {
        return mockMvc.perform(post("/api/recurring-transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": %d, "description": "%s", "categoryId": %d, "interval": "MONTHLY", "startDate": "%s"}"""
                        .formatted(amount, description, categoryId, start)))
                .andExpect(status().isCreated());
    }

    private ResultActions book(int amount, String description, String date, long categoryId, long recurringId,
            String occurrence) throws Exception {
        return mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"amount": %d, "description": "%s", "date": "%s", "categoryId": %d,
                 "replaces": {"recurringId": %d, "date": "%s"}}"""
                .formatted(amount, description, date, categoryId, recurringId, occurrence)));
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long category(String name) {
        return categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(user).stream()
                .filter(c -> c.getName().equals(name))
                .map(Category::getId)
                .findFirst()
                .orElseThrow();
    }
}
