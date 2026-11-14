package io.github.mirco104.budget.importer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.category.CategoryRepository;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.nio.charset.StandardCharsets;
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

/** R33, US38: bank categories in the preview and their stored mappings. Fictional C24 file from BankCsvTest. */
@SpringBootTest
@Transactional
class BankCategoryImportTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    private MockMvc mockMvc;
    private MockMvc other;
    private User user;

    @BeforeEach
    void signIn() {
        user = TestUsers.create(userService, "bank-categories-a@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
        other = TestUsers.mockMvcFor(context, TestUsers.create(userService, "bank-categories-b@example.test"));
    }

    // Pending rows and transfers have none; the list is alphabetical
    @Test
    void previewListsTheBankCategoriesOfTheNewRowsWithTheirMatch() throws Exception {
        preview(mockMvc, BankCsvTest.C24)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankCategories[*].bankCategory", contains(
                        "Einkommen › Gehalt", "Freizeit & Unterhaltung › Digitale Produkte & Dienste", "Wohnen")))
                .andExpect(jsonPath("$.bankCategories[0].parent").value("Einkommen"))
                .andExpect(jsonPath("$.bankCategories[0].child").value("Gehalt"))
                .andExpect(jsonPath("$.bankCategories[0].rows").value(1))
                .andExpect(jsonPath("$.bankCategories[0].parentStatus").value("NEW"))
                .andExpect(jsonPath("$.bankCategories[0].childStatus").value("NEW"))
                .andExpect(jsonPath("$.bankCategories[2].parentStatus").value("EXISTING"))
                .andExpect(jsonPath("$.bankCategories[2].parentCategoryId").value(id("Wohnen")))
                .andExpect(jsonPath("$.bankCategories[2].childStatus").doesNotExist())
                .andExpect(jsonPath("$.bankCategories[2].mappedCategoryId").doesNotExist())
                .andExpect(jsonPath("$.entries[?(@.description == 'Person B · Miete November')].mappedCategoryId",
                        contains((Object) null)));
    }

    @Test
    void storedMappingsSetTheCategoryOfTheRowsInTheNextPreview() throws Exception {
        long freizeit = id("Freizeit");
        map(mockMvc, """
                {"profile": "C24", "mappings": [
                  {"bankCategory": "Freizeit & Unterhaltung › Digitale Produkte & Dienste", "categoryId": %d}
                ]}""".formatted(freizeit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(freizeit));

        preview(mockMvc, BankCsvTest.C24)
                .andExpect(jsonPath("$.bankCategories[1].mappedCategoryId").value(freizeit))
                .andExpect(jsonPath("$.entries[?(@.counterparty == 'SPIELE SHOP')].mappedCategoryId",
                        contains((int) freizeit)));
    }

    // US38: a new parent is created once for two bank categories, the child below it
    @Test
    void createsNewCategoriesWithTheirParentOnce() throws Exception {
        String body = map(mockMvc, """
                {"profile": "C24", "mappings": [
                  {"bankCategory": "Einkommen › Gehalt", "parentName": "Einkommen", "name": "Gehalt"},
                  {"bankCategory": "Einkommen › Bonus", "parentName": "Einkommen", "name": "Bonus"},
                  {"bankCategory": "Wohnen › Garage", "parentId": %d, "name": "Garage"}
                ]}""".formatted(id("Wohnen")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$[?(@.path == 'Einkommen')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.path == 'Einkommen › Gehalt')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.path == 'Einkommen › Bonus')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.path == 'Wohnen › Garage')].icon", contains("home")));
        long gehalt = ((Number) JsonPath.read(body, "$[0].categoryId")).longValue();
        preview(mockMvc, BankCsvTest.C24)
                .andExpect(jsonPath("$.bankCategories[0].parentStatus").value("EXISTING"))
                .andExpect(jsonPath("$.bankCategories[0].childStatus").value("EXISTING"))
                .andExpect(jsonPath("$.bankCategories[0].childCategoryId").value(gehalt))
                .andExpect(jsonPath("$.bankCategories[0].mappedCategoryId").value(gehalt));
    }

    @Test
    void replacesAMapping() throws Exception {
        String mapping = """
                {"profile": "C24", "mappings": [{"bankCategory": "Wohnen", "categoryId": %d}]}""";
        map(mockMvc, mapping.formatted(id("Freizeit"))).andExpect(status().isOk());
        map(mockMvc, mapping.formatted(id("Wohnen"))).andExpect(status().isOk());

        preview(mockMvc, BankCsvTest.C24).andExpect(jsonPath("$.bankCategories[2].mappedCategoryId").value(id("Wohnen")));
    }

    // R33: a mapping to an archived category does not count
    @Test
    void ignoresAMappingToAnArchivedCategory() throws Exception {
        long urlaub = id("Urlaub");
        map(mockMvc, """
                {"profile": "C24", "mappings": [{"bankCategory": "Wohnen", "categoryId": %d}]}""".formatted(urlaub))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/categories/{id}", urlaub).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Urlaub\", \"archived\": true}"))
                .andExpect(status().isOk());

        preview(mockMvc, BankCsvTest.C24).andExpect(jsonPath("$.bankCategories[2].mappedCategoryId").doesNotExist());
    }

    // Decision 62: a certain history goes before the mapping
    @Test
    void aCertainHistoryNamingAnotherCategoryKeepsTheRowOpen() throws Exception {
        long freizeit = id("Freizeit");
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": -1000, "description": "Person B", "date": "2026-10-03", "categoryId": %d}
                        """.formatted(id("Wohnen"))))
                .andExpect(status().isCreated());
        map(mockMvc, """
                {"profile": "C24", "mappings": [{"bankCategory": "Wohnen", "categoryId": %d}]}""".formatted(freizeit))
                .andExpect(status().isOk());

        preview(mockMvc, BankCsvTest.C24)
                .andExpect(jsonPath("$.entries[?(@.description == 'Person B · Miete November')].certain", contains(true)))
                .andExpect(jsonPath("$.entries[?(@.description == 'Person B · Miete November')].mappedCategoryId",
                        contains((Object) null)));
    }

    // R15: another user's category is 400; mappings are per user
    @Test
    void rejectsAForeignCategoryAndKeepsMappingsPerUser() throws Exception {
        map(other, """
                {"profile": "C24", "mappings": [{"bankCategory": "Wohnen", "categoryId": %d}]}""".formatted(id("Wohnen")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));

        map(mockMvc, """
                {"profile": "C24", "mappings": [{"bankCategory": "Wohnen", "categoryId": %d}]}""".formatted(id("Wohnen")))
                .andExpect(status().isOk());
        preview(other, BankCsvTest.C24).andExpect(jsonPath("$.bankCategories[2].mappedCategoryId").doesNotExist());
    }

    @Test
    void rejectsAMappingWithoutCategory() throws Exception {
        map(mockMvc, """
                {"profile": "C24", "mappings": [{"bankCategory": "Wohnen"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    // R33: deleting the category deletes the mapping
    @Test
    void deletingTheCategoryDeletesItsMapping() throws Exception {
        String body = map(mockMvc, """
                {"profile": "C24", "mappings": [{"bankCategory": "Wohnen › Garage", "parentId": %d, "name": "Garage"}]}"""
                .formatted(id("Wohnen"))).andReturn().getResponse().getContentAsString();
        long garage = ((Number) JsonPath.read(body, "$[0].categoryId")).longValue();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/categories/{id}", garage))
                .andExpect(status().isNoContent());
    }

    private ResultActions preview(MockMvc client, String file) throws Exception {
        return client.perform(multipart("/api/imports/preview")
                .file(new MockMultipartFile("file", "export.csv", "text/csv", file.getBytes(StandardCharsets.UTF_8))));
    }

    private ResultActions map(MockMvc client, String body) throws Exception {
        return client.perform(put("/api/bank-category-mappings").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long id(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }
}
