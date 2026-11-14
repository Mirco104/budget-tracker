package io.github.mirco104.budget.category;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * R32 (decision 61): until plan and actuals per subcategory get their own work package, every calculation treats a
 * subcategory like its parent. Fixture: Versicherungen with the subcategory Haftpflicht.
 */
@SpringBootTest
@Transactional
class SubcategoryCalculationTest {

    private static final String MONTH = "2026-09";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    private MockMvc mockMvc;
    private User user;
    private long versicherungen;
    private long haftpflicht;

    @BeforeEach
    void setUp() throws Exception {
        user = TestUsers.create(userService, "subcategories-calc@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        versicherungen = categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals("Versicherungen")).findFirst().orElseThrow().getId();
        String body = mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Haftpflicht\", \"parentId\": " + versicherungen + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        haftpflicht = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void dashboardCountsBookingsAndPlanOfASubcategoryInItsParent() throws Exception {
        postItem("Hausrat", "-20", versicherungen);
        postItem("Haftpflicht", "-10", haftpflicht);
        postTransaction("-20", "Hausrat", versicherungen);
        postTransaction("-10", "Haftpflicht", haftpflicht);

        mockMvc.perform(get("/api/dashboard").param("month", MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.name == 'Haftpflicht')]", hasSize(0)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Versicherungen')].actual", contains(30.00)))
                .andExpect(jsonPath("$.categories[?(@.name == 'Versicherungen')].target", contains(30.00)))
                .andExpect(jsonPath("$.expenses.actual").value(30.00));
    }

    @Test
    void dashboardCountsIncomeOfASubcategoryInItsParent() throws Exception {
        long gehalt = categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals("Gehalt/Lohn (netto)")).findFirst().orElseThrow().getId();
        long bonus = id(mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bonus\", \"parentId\": " + gehalt + "}"))
                .andReturn().getResponse().getContentAsString());
        postTransaction("2000", "Gehalt", gehalt);
        postTransaction("300", "Bonus", bonus);

        mockMvc.perform(get("/api/dashboard").param("month", MONTH))
                .andExpect(jsonPath("$.income[*].name", contains("Gehalt/Lohn (netto)")))
                .andExpect(jsonPath("$.income[0].actual").value(2300.00));
    }

    @Test
    void planShowsOnlyTheParentWithTheSumAndItemsWithTheirPlanCategory() throws Exception {
        postItem("Hausrat", "-20", versicherungen);
        postItem("Haftpflicht", "-10", haftpflicht);

        mockMvc.perform(get("/api/plan").param("month", MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.categoryName == 'Haftpflicht')]", hasSize(0)))
                .andExpect(jsonPath("$.categories[?(@.categoryName == 'Versicherungen')].total", contains(30.00)))
                .andExpect(jsonPath("$.items[?(@.name == 'Haftpflicht')].categoryId", contains((int) haftpflicht)))
                .andExpect(jsonPath("$.items[?(@.name == 'Haftpflicht')].planCategoryId",
                        contains((int) versicherungen)))
                .andExpect(jsonPath("$.items[?(@.name == 'Haftpflicht')].categoryName",
                        contains("Versicherungen › Haftpflicht")));
    }

    @Test
    void historyCountsASubcategoryInItsParent() throws Exception {
        postTransaction("-20", "Hausrat", versicherungen);
        postTransaction("-10", "Haftpflicht", haftpflicht);

        mockMvc.perform(get("/api/history").param("from", MONTH).param("to", MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].expenseCategories", hasSize(1)))
                .andExpect(jsonPath("$.history[0].expenseCategories[0].id").value(versicherungen))
                .andExpect(jsonPath("$.history[0].expenseCategories[0].amount").value(30.00));
    }

    @Test
    void transactionListShowsThePathOfASubcategory() throws Exception {
        postTransaction("-10", "Haftpflicht", haftpflicht);

        mockMvc.perform(get("/api/transactions").param("from", MONTH + "-01").param("to", MONTH + "-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.description == 'Haftpflicht')].categoryName",
                        contains("Versicherungen › Haftpflicht")));
    }

    private void postTransaction(String amount, String description, long categoryId) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": %s, "description": "%s", "date": "%s-15", "categoryId": %d}
                        """.formatted(amount, description, MONTH, categoryId)))
                .andExpect(status().isCreated());
    }

    // A budget without a fixed date (P4)
    private void postItem(String name, String amount, long categoryId) throws Exception {
        mockMvc.perform(post("/api/planned-items").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "%s", "amount": %s, "categoryId": %d, "validFrom": "%s"}
                        """.formatted(name, amount, categoryId, MONTH)))
                .andExpect(status().isCreated());
    }

    private static long id(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
