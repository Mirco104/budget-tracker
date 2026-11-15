package io.github.mirco104.budget.notification;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 27h (decision 65): the bell with month closing (P10), lower limit (P12), price change (P13) and overplanned. */
@SpringBootTest
@Transactional
class NotificationControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private DismissedNotificationRepository dismissedRepository;

    private MockMvc mockMvc;
    private User user;
    private YearMonth current;
    private YearMonth previous;

    @BeforeEach
    void signIn() {
        user = TestUsers.create(userService, "person-b@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        current = YearMonth.now(clock);
        previous = current.minusMonths(1);
    }

    @Test
    void theBellAsksWhereTheLeftoverOfTheLastMonthGoesAndTheGoalGetsIt() throws Exception {
        book("1000", "Gehalt", previous.atDay(1), "Gehalt/Lohn (netto)");
        book("-300", "Einkauf", previous.atDay(2), "Lebensmittel");

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind == 'MONTH_CLOSING')].month").value(previous.toString()))
                .andExpect(jsonPath("$[?(@.kind == 'MONTH_CLOSING')].amount").value(700.00));
        String goal = mockMvc.perform(post("/api/saving-goals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Notgroschen\"}"))
                .andReturn().getResponse().getContentAsString();
        long goalId = ((Number) JsonPath.read(goal, "$.id")).longValue();

        mockMvc.perform(post("/api/month-closings/{month}", previous).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"savingGoalId\": %d}".formatted(goalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leftover").value(700.00))
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.savingGoalName").value("Notgroschen"))
                .andExpect(jsonPath("$.accounts[0].name").value("Girokonto"))
                .andExpect(jsonPath("$.accounts[0].change").value(700.00));
        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[*].kind", not(hasItem("MONTH_CLOSING"))));
        mockMvc.perform(get("/api/saving-goals"))
                .andExpect(jsonPath("$[0].saved").value(700.00));
        // A goal with a closed month cannot be deleted
        mockMvc.perform(delete("/api/saving-goals/{id}", goalId)).andExpect(status().isConflict());

        // "Rückgängig" opens the month again
        mockMvc.perform(delete("/api/month-closings/{month}", previous)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/saving-goals")).andExpect(jsonPath("$[0].saved").value(0.00));
        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[*].kind", hasItem("MONTH_CLOSING")));
        // "Nicht zuordnen" closes it without a goal
        mockMvc.perform(post("/api/month-closings/{month}", previous))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.savingGoalId").doesNotExist());
        mockMvc.perform(post("/api/month-closings/{month}", current)).andExpect(status().isBadRequest());
    }

    @Test
    void aMonthWithoutLeftoverOrBeforeTheFirstBookingIsNotAsked() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[*].kind", not(hasItem("MONTH_CLOSING"))));
        book("-50", "Einkauf", previous.atDay(3), "Lebensmittel");
        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[*].kind", not(hasItem("MONTH_CLOSING"))));
    }

    @Test
    void anExpectedPaymentBelowTheLowerLimitAndAnOverplannedMonthAreReported() throws Exception {
        item("Miete", "-900", "Wohnen", current.atEndOfMonth(), current.toString());

        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[?(@.kind == 'LOWER_LIMIT')].accountName").value("Girokonto"))
                .andExpect(jsonPath("$[?(@.kind == 'LOWER_LIMIT')].expected").value(-900.00))
                .andExpect(jsonPath("$[?(@.kind == 'LOWER_LIMIT')].limit").value(0.00))
                .andExpect(jsonPath("$[?(@.kind == 'OVERPLANNED')].amount").value(900.00));
        String key = JsonPath.read(mockMvc.perform(get("/api/notifications")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.kind == 'LOWER_LIMIT')].key").toString().replaceAll("[\\[\\]\"]", "");

        // "Erledigt" hides it
        mockMvc.perform(post("/api/notifications/done").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"%s\"}".formatted(key)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[*].kind", not(hasItem("LOWER_LIMIT"))))
                .andExpect(jsonPath("$[*].kind", hasItem("OVERPLANNED")));

        // One row per subject: overplanned dismissed, then a higher amount brings it back, a second "Erledigt"
        // overwrites the row
        dismiss("overplanned");
        mockMvc.perform(get("/api/notifications")).andExpect(jsonPath("$[*].kind", not(hasItem("OVERPLANNED"))));
        item("Strom", "-80", "Wohnen", current.atEndOfMonth(), current.toString());
        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[?(@.kind == 'OVERPLANNED')].amount").value(980.00));
        dismiss("overplanned");
        mockMvc.perform(get("/api/notifications")).andExpect(jsonPath("$[*].kind", not(hasItem("OVERPLANNED"))));
        org.assertj.core.api.Assertions.assertThat(dismissedRepository.findByOwner(user))
                .extracting(DismissedNotification::getKey)
                .containsExactlyInAnyOrder(key, "overplanned");

        // A key that is no notification now is rejected
        mockMvc.perform(post("/api/notifications/done").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"overplanned:anything\"}"))
                .andExpect(status().isBadRequest());
    }

    private void dismiss(String key) throws Exception {
        mockMvc.perform(post("/api/notifications/done").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"%s\"}".formatted(key)))
                .andExpect(status().isNoContent());
    }

    @Test
    void aDismissedPriceChangeGoesWithItsItem() throws Exception {
        YearMonth start = current.minusMonths(3);
        long itemId = item("Streaming", "-12.99", "Freizeit", start.atDay(5), start.toString());
        for (YearMonth month : new YearMonth[] {current.minusMonths(2), current.minusMonths(1)}) {
            mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                            {"amount": -14.99, "description": "Streaming", "date": "%s", "categoryId": %d,
                             "replaces": {"itemId": %d, "date": "%s"}}""".formatted(month.atDay(5),
                            category("Freizeit"), itemId, month.atDay(5))))
                    .andExpect(status().isCreated());
        }
        String key = JsonPath.read(mockMvc.perform(get("/api/notifications")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.kind == 'PRICE_CHANGE')].key").toString().replaceAll("[\\[\\]\"]", "");
        dismiss(key);
        org.assertj.core.api.Assertions.assertThat(dismissedRepository.findByOwner(user)).hasSize(1);

        mockMvc.perform(delete("/api/planned-items/{id}", itemId)).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(dismissedRepository.findByOwner(user)).isEmpty();
    }

    @Test
    void twoPaymentsWithAnotherAmountAreAPriceChangeThatCanBeTakenOver() throws Exception {
        YearMonth start = current.minusMonths(3);
        long itemId = item("Streaming", "-12.99", "Freizeit", start.atDay(5), start.toString());
        for (YearMonth month : new YearMonth[] {current.minusMonths(2), current.minusMonths(1)}) {
            mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                            {"amount": -14.99, "description": "Streaming", "date": "%s", "categoryId": %d,
                             "replaces": {"itemId": %d, "date": "%s"}}""".formatted(month.atDay(5),
                            category("Freizeit"), itemId, month.atDay(5))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[?(@.kind == 'PRICE_CHANGE')].itemName").value("Streaming"))
                .andExpect(jsonPath("$[?(@.kind == 'PRICE_CHANGE')].oldAmount").value(-12.99))
                .andExpect(jsonPath("$[?(@.kind == 'PRICE_CHANGE')].newAmount").value(-14.99));
        mockMvc.perform(post("/api/notifications/price-change").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\": %d, \"amount\": -14.99}".formatted(itemId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/plan").param("month", current.toString()))
                .andExpect(jsonPath("$.items[?(@.name == 'Streaming')].amount").value(-14.99));
        mockMvc.perform(get("/api/notifications"))
                .andExpect(jsonPath("$[*].kind", not(hasItem("PRICE_CHANGE"))));
    }

    private void book(String amount, String description, LocalDate date, String category) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": %s, "description": "%s", "date": "%s", "categoryId": %d}"""
                        .formatted(amount, description, date, category(category))))
                .andExpect(status().isCreated());
    }

    private long item(String name, String amount, String category, LocalDate anchor, String validFrom) throws Exception {
        String json = mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "%s", "amount": %s, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                         "anchorDate": "%s", "entryMode": "MANUAL", "validFrom": "%s"}"""
                        .formatted(name, amount, category(category), anchor, validFrom)))
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
