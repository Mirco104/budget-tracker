package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class CategoryControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void signIn() {
        user = TestUsers.create(userService, "person-b@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
    }

    private static final String MONTH = "2026-10";

    @Autowired
    private CategoryRepository categoryRepository;

    // --- GET /api/categories (US1) ---

    // Decision 38: categories are listed alphabetically, ignoring case, umlauts sorted like German
    @Test
    void listsAllSeededCategoriesAlphabetically() throws Exception {
        String json = getCategories(MONTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[0].name").value("Einnahmen Selbstständigkeit"))
                .andExpect(jsonPath("$[19].name").value("Wohnen"))
                .andExpect(jsonPath("$[19].icon").value("home"))
                .andExpect(jsonPath("$[0].sortOrder").doesNotExist())
                .andExpect(jsonPath("$[0].type").doesNotExist())
                .andExpect(jsonPath("$[0].system").doesNotExist())
                .andExpect(jsonPath("$[0].group").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(json, "$[*].name");
        assertThat(names).containsExactly(
                "Einnahmen Selbstständigkeit", "Freizeit", "Gehalt/Lohn (netto)", "Geschenke", "Gesundheit",
                "Investitionen", "Kapitalerträge", "Kredite", "Lebensmittel", "Mobilität", "Nebenjob",
                "Nebenkosten", "Persönlicher Bedarf", "Selbstständigkeit", "Sonstige Einnahmen", "Spenden",
                "Staatliche Leistungen", "Urlaub", "Versicherungen", "Wohnen");
    }

    // --- incomeOnly (R24, decision 33) ---

    @Test
    void marksCategoriesWhoseEntriesAreAllPositive() throws Exception {
        postCreated("/api/transactions", """
                {"amount": 2000, "description": "Gehalt", "date": "2026-10-01", "categoryId": %d}
                """.formatted(categoryId("Gehalt/Lohn (netto)")));
        postCreated("/api/planned-items", """
                {"name": "Nebenjob", "amount": 300, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                 "anchorDate": "2026-01-15", "validFrom": "2026-01"}
                """.formatted(categoryId("Nebenjob")));
        // An expense and a refund: not only income
        postCreated("/api/transactions", """
                {"amount": -50, "description": "Einkauf", "date": "2026-10-02", "categoryId": %d}
                """.formatted(categoryId("Lebensmittel")));
        postCreated("/api/transactions", """
                {"amount": 20, "description": "Erstattung", "date": "2026-10-03", "categoryId": %d}
                """.formatted(categoryId("Lebensmittel")));
        // Positive transactions but a negative planned item
        postCreated("/api/transactions", """
                {"amount": 100, "description": "Rückzahlung", "date": "2026-10-04", "categoryId": %d}
                """.formatted(categoryId("Kredite")));
        postCreated("/api/planned-items", """
                {"name": "Rate", "amount": -200, "categoryId": %d, "rhythmUnit": "MONTH", "rhythmCount": 1,
                 "anchorDate": "2026-01-01", "validFrom": "2026-01"}
                """.formatted(categoryId("Kredite")));

        getCategories(MONTH)
                .andExpect(status().isOk())
                .andExpect(incomeOnly("Gehalt/Lohn (netto)", true))
                .andExpect(incomeOnly("Nebenjob", true))
                .andExpect(incomeOnly("Lebensmittel", false))
                .andExpect(incomeOnly("Kredite", false))
                // Without entries a category is not "only income"
                .andExpect(incomeOnly("Sonstige Einnahmen", false));
    }

    @Test
    void anotherUsersEntriesDoNotCount() throws Exception {
        User other = TestUsers.create(userService, "person-c@example.test");
        long otherGehalt = categoryRepository.findAllByOwner(other).stream()
                .filter(c -> c.getName().equals("Gehalt/Lohn (netto)"))
                .findFirst().orElseThrow().getId();
        TestUsers.mockMvcFor(context, other).perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 2000, "description": "Gehalt", "date": "2026-10-01", "categoryId": %d}
                                """.formatted(otherGehalt)))
                .andExpect(status().isCreated());

        getCategories(MONTH).andExpect(incomeOnly("Gehalt/Lohn (netto)", false));
    }

    private void postCreated(String path, String body) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private static ResultMatcher incomeOnly(String name, boolean expected) {
        return jsonPath("$[?(@.name == '" + name + "')].incomeOnly", contains(expected));
    }

    private ResultActions getCategories(String month) throws Exception {
        return mockMvc.perform(get("/api/categories").param("month", month));
    }
    private long categoryId(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow()
                .getId();
    }
}
