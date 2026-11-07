package io.github.mirco104.budget.suggestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.Category;
import io.github.mirco104.budget.category.CategoryRepository;
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

/** R29, US32: GET /api/booking-suggestion suggests from the user's own transactions and series only (R15). */
@SpringBootTest
@Transactional
class BookingSuggestionControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    private MockMvc asA;
    private MockMvc asB;
    private User a;

    @BeforeEach
    void signIn() {
        a = TestUsers.create(userService, "person-g@example.test");
        User b = TestUsers.create(userService, "person-h@example.test");
        asA = TestUsers.mockMvcFor(context, a);
        asB = TestUsers.mockMvcFor(context, b);
    }

    @Test
    void suggestsFromTransactionsAndSeries() throws Exception {
        book(asA, -42.10, "REWE Markt Nord", category(a, "Lebensmittel"));
        asA.perform(post("/api/recurring-transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -900, "description": "Miete Wohnung", "categoryId": %d, "interval": "MONTHLY",
                         "startDate": "2026-01-01"}""".formatted(category(a, "Wohnen"))))
                .andExpect(status().isCreated());

        asA.perform(get("/api/booking-suggestion").param("text", "rewe markt süd").param("amount", "-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(category(a, "Lebensmittel")))
                .andExpect(jsonPath("$.certain").value(true));
        asA.perform(get("/api/booking-suggestion").param("text", "Miete Wohnung"))
                .andExpect(jsonPath("$.categoryId").value(category(a, "Wohnen")));
    }

    @Test
    void theSignComesFromIncomeOrTheAmount() throws Exception {
        book(asA, 2500, "Firma Lohn", category(a, "Gehalt/Lohn (netto)"));

        asA.perform(get("/api/booking-suggestion").param("text", "Firma Lohn"))
                .andExpect(jsonPath("$.categoryId").isEmpty());
        asA.perform(get("/api/booking-suggestion").param("text", "Firma Lohn").param("amount", "2500"))
                .andExpect(jsonPath("$.categoryId").value(category(a, "Gehalt/Lohn (netto)")));
        asA.perform(get("/api/booking-suggestion").param("text", "Firma Lohn").param("income", "true"))
                .andExpect(jsonPath("$.categoryId").value(category(a, "Gehalt/Lohn (netto)")));
    }

    @Test
    void transfersAndArchivedCategoriesAreNoHistory() throws Exception {
        asA.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -500, "description": "Tagesgeld Sparen", "date": "2026-10-02", "transfer": true}"""))
                .andExpect(status().isCreated());
        long freizeit = category(a, "Freizeit");
        book(asA, -30, "Verein Sport", freizeit);
        asA.perform(put("/api/categories/{id}", freizeit).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Freizeit\", \"sortOrder\": 80, \"archived\": true}"))
                .andExpect(status().isOk());

        asA.perform(get("/api/booking-suggestion").param("text", "Tagesgeld Sparen"))
                .andExpect(jsonPath("$.categoryId").isEmpty())
                .andExpect(jsonPath("$.certain").value(false));
        asA.perform(get("/api/booking-suggestion").param("text", "Verein Sport"))
                .andExpect(jsonPath("$.categoryId").isEmpty());
    }

    @Test
    void anotherUsersBookingsAreNoHistory() throws Exception {
        book(asA, -42.10, "REWE Markt Nord", category(a, "Lebensmittel"));

        asB.perform(get("/api/booking-suggestion").param("text", "REWE Markt Nord"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").isEmpty());
    }

    private void book(MockMvc mockMvc, double amount, String description, long categoryId) throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": %s, "description": "%s", "date": "2026-10-02", "categoryId": %d}"""
                        .formatted(amount, description, categoryId)))
                .andExpect(status().isCreated());
    }

    private long category(User owner, String name) {
        return categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(owner).stream()
                .filter(c -> c.getName().equals(name))
                .map(Category::getId)
                .findFirst()
                .orElseThrow();
    }
}
