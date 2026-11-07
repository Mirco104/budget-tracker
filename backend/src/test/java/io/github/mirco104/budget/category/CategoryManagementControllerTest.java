package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** US10: create, rename, reorder, archive and delete categories. */
@SpringBootTest
@Transactional
class CategoryManagementControllerTest {

    private static final String MONTH = "2026-10";
    private static final String IN_USE =
            "Es gibt Einträge unter dieser Kategorie. Die Kategorie kann nur archiviert werden.";

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

    @Autowired
    private CategoryRepository categoryRepository;

    // --- POST /api/categories ---

    // Decision 38: a new category takes its alphabetical place
    @Test
    void createsCategoryAtItsAlphabeticalPlaceWithTrimmedName() throws Exception {
        var result = postCategory("{\"name\": \"  Haustier  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Haustier"))
                .andExpect(jsonPath("$.sortOrder").doesNotExist())
                .andExpect(jsonPath("$.archived").value(false));
        int id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        result.andExpect(header().string("Location", endsWith("/api/categories/" + id)));

        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$", hasSize(21)))
                .andExpect(jsonPath("$[4].name").value("Gesundheit"))
                .andExpect(jsonPath("$[5].name").value("Haustier"));
    }

    // Umlauts sort like their base letter and case does not matter (German collation)
    @Test
    void sortsUmlautsAndLowerCaseLikeGerman() throws Exception {
        postCategory("{\"name\": \"ärztliche Kosten\"}").andExpect(status().isCreated());
        postCategory("{\"name\": \"Zoo\"}").andExpect(status().isCreated());
        postCategory("{\"name\": \"bücher\"}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[0].name").value("ärztliche Kosten"))
                .andExpect(jsonPath("$[1].name").value("bücher"))
                .andExpect(jsonPath("$[2].name").value("Einnahmen Selbstständigkeit"))
                .andExpect(jsonPath("$[22].name").value("Zoo"));
    }

    @Test
    void rejectsBlankName() throws Exception {
        postCategory("{\"name\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Name ist Pflicht."));
    }

    @Test
    void rejectsNameLongerThan50Characters() throws Exception {
        postCategory("{\"name\": \"" + "x".repeat(51) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Der Name darf höchstens 50 Zeichen haben."));
        postCategory("{\"name\": \"" + "x".repeat(50) + "\"}").andExpect(status().isCreated());
    }

    @Test
    void rejectsDuplicateNameIgnoringCase() throws Exception {
        postCategory("{\"name\": \"wohnen\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Konflikt"))
                .andExpect(jsonPath("$.detail").value("Eine Kategorie mit dem Namen \"wohnen\" gibt es schon."));
    }

    // --- PUT /api/categories/{id} ---

    @Test
    void renamesCategory() throws Exception {
        Category category = category("Urlaub");

        putCategory(category.getId(), "Reisen", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reisen"));
    }

    @Test
    void keepsOwnNameWithDifferentCase() throws Exception {
        Category category = category("Urlaub");

        putCategory(category.getId(), "URLAUB", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("URLAUB"));
    }

    @Test
    void rejectsRenameToExistingName() throws Exception {
        Category category = category("Urlaub");

        putCategory(category.getId(), "Freizeit", false)
                .andExpect(status().isConflict());
    }

    // Decision 38: there is no own order any more; a renamed category moves to its alphabetical place
    @Test
    void movesARenamedCategoryToItsAlphabeticalPlace() throws Exception {
        Category wohnen = category("Wohnen");

        putCategory(wohnen.getId(), "Abo und Miete", false).andExpect(status().isOk());

        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[0].name").value("Abo und Miete"))
                .andExpect(jsonPath("$[1].name").value("Einnahmen Selbstständigkeit"));
    }

    // Older clients still send a sortOrder; it is ignored
    @Test
    void ignoresASortOrderInTheRequest() throws Exception {
        Category wohnen = category("Wohnen");

        putCategory(wohnen.getId(), "{\"name\": \"Wohnen\", \"sortOrder\": 10, \"archived\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortOrder").doesNotExist());

        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[0].name").value("Einnahmen Selbstständigkeit"))
                .andExpect(jsonPath("$[19].name").value("Wohnen"));
    }

    @Test
    void archivesAndReactivatesCategory() throws Exception {
        Category category = category("Kredite");

        putCategory(category.getId(), "Kredite", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[?(@.name == 'Kredite')].archived", contains(true)));

        putCategory(category.getId(), "Kredite", false)
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void rejectsIncompleteUpdate() throws Exception {
        mockMvc.perform(put("/api/categories/{id}", category("Kredite").getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Kredite\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404WhenUpdatingUnknownCategory() throws Exception {
        putCategory(999_999L, "Neu", false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Kategorie 999999 wurde nicht gefunden."));
    }

    // Past months keep showing an archived category (US10)
    @Test
    void dashboardStillShowsArchivedCategoryInPastMonths() throws Exception {
        Category category = category("Geschenke");
        postTransaction(category.getId(), "2026-09-10").andExpect(status().isCreated());

        putCategory(category.getId(), "Geschenke", true).andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard").param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.name == 'Geschenke')].actual", contains(20.00)));
    }

    // --- Icons (US28) ---

    @Test
    void newCategoryWithoutIconGetsTag() throws Exception {
        postCategory("{\"name\": \"Haustier\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.icon").value("tag"));
    }

    @Test
    void createsCategoryWithIcon() throws Exception {
        postCategory("{\"name\": \"Haustier\", \"icon\": \"heart\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.icon").value("heart"));

        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[?(@.name == 'Haustier')].icon", contains("heart")));
    }

    @Test
    void rejectsUnknownIconOnCreate() throws Exception {
        postCategory("{\"name\": \"Haustier\", \"icon\": \"rocket\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("icon"))
                .andExpect(jsonPath("$.errors[0].message").value("Das Icon gibt es nicht."));
    }

    @Test
    void changesIcon() throws Exception {
        Category category = category("Urlaub");

        putCategory(category.getId(), "{\"name\": \"Urlaub\", \"archived\": false, \"icon\": \"car\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icon").value("car"));
    }

    @Test
    void keepsIconWhenUpdateHasNone() throws Exception {
        Category category = category("Urlaub");

        putCategory(category.getId(), "Ferien", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icon").value("suitcase"));
    }

    @Test
    void rejectsUnknownIconOnUpdate() throws Exception {
        Category category = category("Urlaub");

        putCategory(category.getId(), "{\"name\": \"Urlaub\", \"archived\": false, \"icon\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("icon"));
        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[?(@.name == 'Urlaub')].icon", contains("suitcase")));
    }

    // R15: another user's category is 404, also when only the icon changes
    @Test
    void returns404WhenChangingIconOfAnotherUsersCategory() throws Exception {
        User other = TestUsers.create(userService, "person-c@example.test");
        Category foreign = categoryRepository.findAllByOwner(other).stream()
                .filter(category -> category.getName().equals("Wohnen"))
                .findFirst().orElseThrow();

        putCategory(foreign.getId(), "{\"name\": \"Wohnen\", \"archived\": false, \"icon\": \"car\"}")
                .andExpect(status().isNotFound());
        assertThat(categoryRepository.findById(foreign.getId()).orElseThrow().getIcon())
                .isEqualTo("home");
    }

    // --- DELETE /api/categories/{id} ---

    @Test
    void deletesUnusedCategory() throws Exception {
        long id = category("Spenden").getId();

        mockMvc.perform(delete("/api/categories/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categories").param("month", MONTH)).andExpect(jsonPath("$", hasSize(19)));
    }

    @Test
    void rejectsDeletingCategoryWithTransaction() throws Exception {
        long id = category("Spenden").getId();
        postTransaction(id, "2026-10-01").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categories/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(IN_USE));
    }

    @Test
    void rejectsDeletingCategoryWithSeries() throws Exception {
        long id = category("Spenden").getId();
        mockMvc.perform(post("/api/recurring-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -10, "description": "Spende", "categoryId": %d,
                                 "interval": "MONTHLY", "startDate": "2026-10-01"}
                                """.formatted(id)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categories/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(IN_USE));
    }

    @Test
    void rejectsDeletingCategoryWithTarget() throws Exception {
        long id = category("Spenden").getId();
        mockMvc.perform(put("/api/categories/{id}/target", id)
                        .param("month", MONTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAmount\": 10}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/categories/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(IN_USE));
    }

    @Test
    void returns404WhenDeletingUnknownCategory() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", 999_999L)).andExpect(status().isNotFound());
    }

    private ResultActions postCategory(String body) throws Exception {
        return mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putCategory(long id, String name, boolean archived) throws Exception {
        return putCategory(id, "{\"name\": \"%s\", \"archived\": %b}".formatted(name, archived));
    }

    private ResultActions putCategory(long id, String body) throws Exception {
        return mockMvc.perform(put("/api/categories/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions postTransaction(long categoryId, String date) throws Exception {
        return mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": -20, "description": "Test", "date": "%s", "categoryId": %d}
                        """.formatted(date, categoryId)));
    }

    private Category category(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow();
    }
}
