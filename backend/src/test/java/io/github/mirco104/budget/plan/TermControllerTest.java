package io.github.mirco104.budget.plan;

import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Terms (P5), automatic entries (P6), tick-off and skip (P7). Dates are relative to today. */
@SpringBootTest
@Transactional
class TermControllerTest {

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
    void anAutomaticItemIsEnteredOnItsDueDayOnceAndCountsAsFix() throws Exception {
        long id = item("Miete", "-900", "Wohnen", today, "AUTO");

        mockMvc.perform(get("/api/transactions").param("month", month))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Miete"))
                .andExpect(jsonPath("$[0].amount").value(-900.00))
                .andExpect(jsonPath("$[0].auto").value(true))
                .andExpect(jsonPath("$[0].fix").value(true));
        // A second read enters nothing again
        mockMvc.perform(get("/api/dashboard").param("month", month))
                .andExpect(jsonPath("$.expenses.actual").value(900.00))
                .andExpect(jsonPath("$.expenses.recurring").value(900.00));
        terms().andExpect(jsonPath("$[?(@.itemId == " + id + ")].status").value("FULFILLED"));
    }

    @Test
    void aDeletedAutomaticEntryIsNotEnteredAgain() throws Exception {
        item("Miete", "-900", "Wohnen", today, "AUTO");
        String list = mockMvc.perform(get("/api/transactions").param("month", month))
                .andReturn().getResponse().getContentAsString();
        long booking = ((Number) JsonPath.read(list, "$[0].id")).longValue();

        mockMvc.perform(delete("/api/transactions/{id}", booking)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions").param("month", month)).andExpect(jsonPath("$", hasSize(0)));
        terms().andExpect(jsonPath("$[0].status").value("SKIPPED"));
    }

    // 27f2: an automatic item enters its terms from the month "Gilt ab" on, also those before the day it was created,
    // but none of earlier months
    @Test
    void anItemEntersItsTermsFromTheMonthGiltAbOn() throws Exception {
        LocalDate lastMonth = today.minusMonths(1);
        mockMvc.perform(post("/api/planned-items/past-terms").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Miete", "amount": -900, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                         "anchorDate": "%s", "entryMode": "AUTO", "validFrom": "%s"}"""
                        .formatted(category("Wohnen"), lastMonth, YearMonth.from(lastMonth))))
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Miete", "amount": -900, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                         "anchorDate": "%s", "entryMode": "AUTO", "validFrom": "%s"}"""
                        .formatted(category("Wohnen"), lastMonth, month)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions").param("from", lastMonth.minusDays(1).toString())
                        .param("to", today.toString()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value(today.toString()))
                .andExpect(jsonPath("$[0].auto").value(true));
    }

    @Test
    void ticksOffATermAndUndoesIt() throws Exception {
        LocalDate date = today.plusDays(3);
        long id = item("Notgroschen", "-150", "Sonstige Einnahmen", date, "TICK");
        terms().andExpect(jsonPath("$[0].status").value("EXPECTED"))
                .andExpect(jsonPath("$[0].entryMode").value("TICK"));

        mockMvc.perform(post("/api/terms/paid").contentType(MediaType.APPLICATION_JSON).content("""
                        {"itemId": %d, "date": "%s"}""".formatted(id, date)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(-150.00))
                .andExpect(jsonPath("$.date").value(date.toString()));
        terms().andExpect(jsonPath("$[0].status").value("FULFILLED")).andExpect(jsonPath("$[0].linked").value(true));
        // Twice is 400
        mockMvc.perform(post("/api/terms/paid").contentType(MediaType.APPLICATION_JSON).content("""
                        {"itemId": %d, "date": "%s"}""".formatted(id, date)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/terms/paid").param("itemId", String.valueOf(id)).param("date", date.toString()))
                .andExpect(status().isNoContent());
        terms().andExpect(jsonPath("$[0].status").value("EXPECTED"));
        mockMvc.perform(get("/api/transactions").param("from", today.toString()).param("to", date.toString()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ticksOffWithAnotherAmountAndDate() throws Exception {
        LocalDate date = today.plusDays(3);
        long id = item("Strom", "-80", "Nebenkosten", date, "TICK");

        mockMvc.perform(post("/api/terms/paid").contentType(MediaType.APPLICATION_JSON).content("""
                        {"itemId": %d, "date": "%s", "amount": 84.50, "bookingDate": "%s"}"""
                        .formatted(id, date, date.plusDays(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(-84.50))
                .andExpect(jsonPath("$.date").value(date.plusDays(1).toString()));
    }

    @Test
    void skipsATermAndTakesItBack() throws Exception {
        LocalDate date = today.plusDays(3);
        long id = item("Verein", "-20", "Freizeit", date, "TICK");

        mockMvc.perform(post("/api/terms/skip").contentType(MediaType.APPLICATION_JSON).content("""
                        {"itemId": %d, "date": "%s"}""".formatted(id, date)))
                .andExpect(status().isNoContent());
        terms().andExpect(jsonPath("$[0].status").value("SKIPPED"));

        mockMvc.perform(delete("/api/terms/skip").param("itemId", String.valueOf(id)).param("date", date.toString()))
                .andExpect(status().isNoContent());
        terms().andExpect(jsonPath("$[0].status").value("EXPECTED"));
    }

    @Test
    void aMatchingBookingByHandFulfilsTheTermWithoutLink() throws Exception {
        LocalDate date = today.plusDays(3);
        item("Strom Stadtwerke", "-80", "Nebenkosten", date, "TICK");

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -82, "description": "Stadtwerke Abschlag", "date": "%s", "categoryId": %d}"""
                        .formatted(date.plusDays(1), category("Nebenkosten"))))
                .andExpect(status().isCreated());

        terms().andExpect(jsonPath("$[0].status").value("FULFILLED")).andExpect(jsonPath("$[0].linked").value(false));
        mockMvc.perform(get("/api/transactions").param("from", today.toString()).param("to", date.plusDays(2).toString()))
                .andExpect(jsonPath("$[0].fix").value(true));
    }

    @Test
    void rejectsADateThatIsNoTermAndAnotherUsersItem() throws Exception {
        LocalDate date = today.plusDays(3);
        long id = item("Verein", "-20", "Freizeit", date, "TICK");

        mockMvc.perform(post("/api/terms/paid").contentType(MediaType.APPLICATION_JSON).content("""
                        {"itemId": %d, "date": "%s"}""".formatted(id, date.plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        MockMvc other = TestUsers.mockMvcFor(context, TestUsers.create(userService, "person-c@example.test"));
        other.perform(post("/api/terms/paid").contentType(MediaType.APPLICATION_JSON).content("""
                        {"itemId": %d, "date": "%s"}""".formatted(id, date)))
                .andExpect(status().isBadRequest());
        other.perform(get("/api/terms").param("from", today.toString()).param("to", date.toString()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // AP 27d: the overview shows the planned saving and the payments still expected in the month
    @Test
    void theOverviewShowsSavingAndExpectedCards() throws Exception {
        LocalDate date = today.plusDays(3);
        item("Verein", "-20", "Freizeit", date, "TICK");
        mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Notgroschen", "amount": 150, "saving": true, "rhythmUnit": "MONTH", "rhythmCount": 1,
                         "anchorDate": "%s", "validFrom": "%s"}""".formatted(date, month)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dashboard").param("month", YearMonth.from(date).toString()))
                .andExpect(jsonPath("$.saving.planned").value(150.00))
                .andExpect(jsonPath("$.saving.rate").doesNotExist())
                .andExpect(jsonPath("$.saving.items[0].name").value("Notgroschen"))
                .andExpect(jsonPath("$.expected.count").value(2))
                .andExpect(jsonPath("$.expected.total").value(-170.00));
    }

    private ResultActions terms() throws Exception {
        return mockMvc.perform(get("/api/terms")
                        .param("from", today.minusDays(10).toString())
                        .param("to", today.plusDays(10).toString()))
                .andExpect(status().isOk());
    }

    // A monthly item with its first term on the given day, valid from that month
    private long item(String name, String amount, String category, LocalDate anchor, String mode) throws Exception {
        String json = mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "%s", "amount": %s, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                         "anchorDate": "%s", "entryMode": "%s", "validFrom": "%s"}"""
                        .formatted(name, amount, category(category), anchor, mode, YearMonth.from(anchor))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long category(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
