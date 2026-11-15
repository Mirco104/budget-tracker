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
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 27i (decision 65): suggestions from monthly series of bookings (R31) that no item covers yet. */
@SpringBootTest
@Transactional
class PlanSuggestionControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private io.github.mirco104.budget.notification.DismissedNotificationRepository dismissedRepository;

    private MockMvc mockMvc;
    private User user;
    private YearMonth current;

    @BeforeEach
    void signIn() {
        user = TestUsers.create(userService, "person-b@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        current = YearMonth.now(clock);
    }

    @Test
    void aMonthlySeriesOfBookingsIsSuggestedAndCanBeHidden() throws Exception {
        for (int i = 3; i >= 1; i--) {
            book("-12.99", "Streaming Abo", current.minusMonths(i).atDay(3).toString(), "Freizeit");
        }
        book("-40", "Kino", current.minusMonths(1).atDay(10).toString(), "Freizeit");

        String json = mockMvc.perform(get("/api/plan/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Streaming Abo"))
                .andExpect(jsonPath("$[0].amount").value(-12.99))
                .andExpect(jsonPath("$[0].categoryName").value("Freizeit"))
                .andExpect(jsonPath("$[0].months").value(3))
                .andExpect(jsonPath("$[0].group").value("FIXED"))
                .andExpect(jsonPath("$[0].anchorDate").value(current.minusMonths(1).atDay(3).toString()))
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(json, "$[0].key");

        mockMvc.perform(post("/api/notifications/done").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"%s\"}".formatted(key)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/plan/suggestions")).andExpect(jsonPath("$", hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(dismissedRepository.findByOwner(user)).hasSize(1);

        // Without bookings of the series in the last 12 months its row goes
        for (int i = 3; i >= 1; i--) {
            for (Number id : JsonPath.<List<Number>>read(mockMvc.perform(get("/api/transactions").param("month",
                            current.minusMonths(i).toString())).andReturn().getResponse().getContentAsString(),
                    "$[?(@.description == 'Streaming Abo')].id")) {
                mockMvc.perform(delete("/api/transactions/{id}", id.longValue())).andExpect(status().isNoContent());
            }
        }
        mockMvc.perform(get("/api/plan/suggestions")).andExpect(jsonPath("$", hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(dismissedRepository.findByOwner(user)).isEmpty();

        // A key that is no suggestion now is rejected
        mockMvc.perform(post("/api/notifications/done").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"suggestion:nothing|-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSeriesThatStoppedIsNotSuggested() throws Exception {
        for (int i = 6; i >= 3; i--) {
            book("-24", "Drogerie", current.minusMonths(i).atDay(6).toString(), "Persönlicher Bedarf");
        }
        mockMvc.perform(get("/api/plan/suggestions")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aSeriesWithAnItemOfTheSameNameIsNotSuggested() throws Exception {
        for (int i = 3; i >= 1; i--) {
            book("3000", "Gehalt", current.minusMonths(i).atDay(1).toString(), "Gehalt/Lohn (netto)");
        }
        mockMvc.perform(get("/api/plan/suggestions"))
                .andExpect(jsonPath("$[0].group").value("INCOME"));
        mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Gehalt", "amount": 3000, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                         "anchorDate": "%s", "entryMode": "MANUAL", "validFrom": "%s"}"""
                        .formatted(category("Gehalt/Lohn (netto)"), current.atDay(1), current)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/plan/suggestions")).andExpect(jsonPath("$", hasSize(0)));
    }

    private void book(String amount, String description, String date, String category) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": %s, "description": "%s", "date": "%s", "categoryId": %d}"""
                        .formatted(amount, description, date, category(category))))
                .andExpect(status().isCreated());
    }

    private long category(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
