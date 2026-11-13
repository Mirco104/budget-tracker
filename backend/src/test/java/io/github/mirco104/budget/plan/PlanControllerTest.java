package io.github.mirco104.budget.plan;

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

/** Planning (section 19): planned items, "gilt ab" versions and the plan of a month (P2–P4, P9). */
@SpringBootTest
@Transactional
class PlanControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void signIn() {
        user = TestUsers.create(userService, "person-b@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
    }

    @Test
    void planSumsTheMonthlySharesPerGroup() throws Exception {
        create(item("Gehalt", "2500", "Gehalt/Lohn (netto)", "\"MONTH\"", 1, "\"2026-01-01\"", "2026-01"));
        create(item("Miete", "-900", "Wohnen", "\"MONTH\"", 1, "\"2026-01-01\"", "2026-01"));
        create(item("Kfz-Versicherung", "-1200", "Mobilität", "\"MONTH\"", 12, "\"2026-03-15\"", "2026-01"));
        create(item("Benzin", "-10", "Mobilität", "\"WEEK\"", 1, "\"2026-01-05\"", "2026-01"));
        create(item("Lebensmittel", "-400", "Lebensmittel", "null", null, "null", "2026-01"));
        create("""
                {"name": "Notgroschen", "amount": 300, "saving": true, "rhythmUnit": "MONTH", "rhythmCount": 1,
                 "anchorDate": "2026-01-28", "validFrom": "2026-01"}""");

        mockMvc.perform(get("/api/plan").param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(2500.00))
                .andExpect(jsonPath("$.fixed").value(1043.33))
                .andExpect(jsonPath("$.budgets").value(400.00))
                .andExpect(jsonPath("$.saving").value(300.00))
                .andExpect(jsonPath("$.unplanned").value(756.67))
                .andExpect(jsonPath("$.savingRate").value(12.0))
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.items[?(@.name == 'Kfz-Versicherung')].monthlyShare").value(100.00))
                .andExpect(jsonPath("$.items[?(@.name == 'Kfz-Versicherung')].group").value("FIXED"))
                .andExpect(jsonPath("$.items[?(@.name == 'Lebensmittel')].entryMode").value("MANUAL"))
                .andExpect(jsonPath("$.items[?(@.name == 'Notgroschen')].entryMode").value("TICK"))
                .andExpect(jsonPath("$.items[?(@.name == 'Notgroschen')].amount").value(-300.00))
                .andExpect(jsonPath("$.items[?(@.name == 'Miete')].entryMode").value("AUTO"))
                .andExpect(jsonPath("$.categories[?(@.group == 'FIXED' && @.categoryName == 'Mobilität')].total")
                        .value(143.33));

        // The dashboard takes the plan per category as the target of its line (P3)
        mockMvc.perform(get("/api/dashboard").param("month", "2026-11"))
                .andExpect(jsonPath("$.categories[?(@.name == 'Mobilität')].target").value(143.33));
    }

    // 27f4: the planning page shows Ē(M) and Emin(M) with their months and whether the plan lies above Emin(M)
    @Test
    void thePlanNamesTheMonthsOfTheAverageIncomeAndWhetherItPlansMoreThanTheWeakestMonth() throws Exception {
        create("""
                {"name": "Gehalt", "amount": 2500, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                 "anchorDate": "2026-01-01", "entryMode": "MANUAL", "validFrom": "2026-01"}"""
                .formatted(category("Gehalt/Lohn (netto)")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": 2000, "description": "Gehalt", "date": "2026-10-15", "categoryId": %d}"""
                        .formatted(category("Gehalt/Lohn (netto)"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/plan").param("month", "2026-11"))
                .andExpect(jsonPath("$.minimumIncome").value(2000.00))
                .andExpect(jsonPath("$.averageFrom").value("2026-10"))
                .andExpect(jsonPath("$.averageTo").value("2026-10"))
                .andExpect(jsonPath("$.incomeAboveMinimum").value(true));
    }

    @Test
    void aChangeFromALaterMonthIsANewVersion() throws Exception {
        long id = create(item("Miete", "-900", "Wohnen", "\"MONTH\"", 1, "\"2026-01-01\"", "2026-01"));

        mockMvc.perform(put("/api/planned-items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(item("Miete", "-950", "Wohnen", "\"MONTH\"", 1, "\"2026-01-01\"", "2026-11")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validFrom").value("2026-11"));

        mockMvc.perform(get("/api/plan").param("month", "2026-10"))
                .andExpect(jsonPath("$.items[0].amount").value(-900.00))
                .andExpect(jsonPath("$.items[0].validTo").value("2026-10"));
        mockMvc.perform(get("/api/plan").param("month", "2026-11"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].amount").value(-950.00));

        // A change in the month of the version changes it
        mockMvc.perform(put("/api/planned-items/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(item("Miete", "-910", "Wohnen", "\"MONTH\"", 1, "\"2026-01-01\"", "2026-01")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/plan").param("month", "2026-10"))
                .andExpect(jsonPath("$.items[0].amount").value(-910.00));
    }

    @Test
    void endsAnItemFromAMonthOrDeletesIt() throws Exception {
        long id = create(item("Abo", "-10", "Freizeit", "\"MONTH\"", 1, "\"2026-01-10\"", "2026-01"));

        mockMvc.perform(delete("/api/planned-items/{id}", id).param("from", "2026-11"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/plan").param("month", "2026-10")).andExpect(jsonPath("$.items", hasSize(1)));
        mockMvc.perform(get("/api/plan").param("month", "2026-11")).andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(delete("/api/planned-items/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/plan").param("month", "2026-10")).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void rejectsInvalidItems() throws Exception {
        post(item("Miete", "0", "Wohnen", "null", null, "null", "2026-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        post("""
                {"name": "Miete", "amount": -900, "rhythmUnit": "MONTH", "rhythmCount": 1, "anchorDate": "2026-01-01",
                 "validFrom": "2026-01"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        post(item("Miete", "-900", "Wohnen", "\"MONTH\"", 1, "null", "2026-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("anchorDate"));
        post(item("Miete", "-900", "Wohnen", "\"MONTH\"", 0, "\"2026-01-01\"", "2026-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rhythmCount"));
        post("{\"name\": \"Miete\", \"amount\": -900, \"categoryId\": " + category("Wohnen") + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("validFrom"));
    }

    @Test
    void anotherUsersItemIsNotFound() throws Exception {
        long id = create(item("Miete", "-900", "Wohnen", "\"MONTH\"", 1, "\"2026-01-01\"", "2026-01"));
        MockMvc other = TestUsers.mockMvcFor(context, TestUsers.create(userService, "person-c@example.test"));

        other.perform(delete("/api/planned-items/{id}", id)).andExpect(status().isNotFound());
        other.perform(get("/api/plan").param("month", "2026-10")).andExpect(jsonPath("$.items", hasSize(0)));
    }

    private String item(String name, String amount, String category, String unit, Integer count, String anchor,
            String validFrom) {
        return """
                {"name": "%s", "amount": %s, "categoryId": %d, "rhythmUnit": %s, "rhythmCount": %s,
                 "anchorDate": %s, "validFrom": "%s"}""".formatted(
                name, amount, category(category), unit, count, anchor, validFrom);
    }

    private ResultActions post(String body) throws Exception {
        return mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/planned-items")
                        .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long create(String body) throws Exception {
        String json = post(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long category(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
