package io.github.mirco104.budget.category;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** US10: create, rename, reorder, archive and delete categories. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryManagementControllerTest {

    private static final String MONTH = "2026-10";
    private static final String IN_USE =
            "Es gibt Einträge unter dieser Kategorie. Die Kategorie kann nur archiviert werden.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    // --- POST /api/categories ---

    @Test
    void createsCategoryAtTheEndWithTrimmedName() throws Exception {
        int lastSortOrder = categoryRepository.findTopByOrderBySortOrderDesc().orElseThrow().getSortOrder();

        var result = postCategory("{\"name\": \"  Haustier  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Haustier"))
                .andExpect(jsonPath("$.sortOrder").value(lastSortOrder + 10))
                .andExpect(jsonPath("$.archived").value(false));
        int id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        result.andExpect(header().string("Location", endsWith("/api/categories/" + id)));

        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$", hasSize(21)))
                .andExpect(jsonPath("$[20].name").value("Haustier"));
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

        putCategory(category.getId(), "Reisen", category.getSortOrder(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reisen"));
    }

    @Test
    void keepsOwnNameWithDifferentCase() throws Exception {
        Category category = category("Urlaub");

        putCategory(category.getId(), "URLAUB", category.getSortOrder(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("URLAUB"));
    }

    @Test
    void rejectsRenameToExistingName() throws Exception {
        Category category = category("Urlaub");

        putCategory(category.getId(), "Freizeit", category.getSortOrder(), false)
                .andExpect(status().isConflict());
    }

    @Test
    void swapsPlacesWithTheCategoryThatHasTheRequestedSortOrder() throws Exception {
        Category wohnen = category("Wohnen");
        Category nebenkosten = category("Nebenkosten");
        int wohnenOrder = wohnen.getSortOrder();
        int nebenkostenOrder = nebenkosten.getSortOrder();

        putCategory(nebenkosten.getId(), "Nebenkosten", wohnenOrder, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortOrder").value(wohnenOrder));

        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[0].name").value("Nebenkosten"))
                .andExpect(jsonPath("$[1].name").value("Wohnen"))
                .andExpect(jsonPath("$[1].sortOrder").value(nebenkostenOrder));
    }

    @Test
    void archivesAndReactivatesCategory() throws Exception {
        Category category = category("Kredite");

        putCategory(category.getId(), "Kredite", category.getSortOrder(), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get("/api/categories").param("month", MONTH))
                .andExpect(jsonPath("$[?(@.name == 'Kredite')].archived", contains(true)));

        putCategory(category.getId(), "Kredite", category.getSortOrder(), false)
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
        putCategory(999_999L, "Neu", 10, false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Kategorie 999999 wurde nicht gefunden."));
    }

    // Past months keep showing an archived category (US10)
    @Test
    void dashboardStillShowsArchivedCategoryInPastMonths() throws Exception {
        Category category = category("Geschenke");
        postTransaction(category.getId(), "2026-09-10").andExpect(status().isCreated());

        putCategory(category.getId(), "Geschenke", category.getSortOrder(), true).andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard").param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.name == 'Geschenke')].actual", contains(20.00)));
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

    private ResultActions putCategory(long id, String name, int sortOrder, boolean archived) throws Exception {
        return mockMvc.perform(put("/api/categories/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"sortOrder\": %d, \"archived\": %b}".formatted(name, sortOrder, archived)));
    }

    private ResultActions postTransaction(long categoryId, String date) throws Exception {
        return mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": -20, "description": "Test", "date": "%s", "categoryId": %d}
                        """.formatted(date, categoryId)));
    }

    private Category category(String name) {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow();
    }
}
