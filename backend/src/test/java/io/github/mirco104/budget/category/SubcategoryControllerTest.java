package io.github.mirco104.budget.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** R32, US37: subcategories with one level, names unique per level, archive and delete with the parent. */
@SpringBootTest
@Transactional
class SubcategoryControllerTest {

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
        user = TestUsers.create(userService, "subcategories-a@example.test");
        mockMvc = TestUsers.mockMvcFor(context, user);
    }

    @Test
    void createsSubcategoryWithPathAndTheIconOfItsParent() throws Exception {
        long parent = id("Versicherungen");

        postCategory("{\"name\": \"Haftpflicht\", \"parentId\": " + parent + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Haftpflicht"))
                .andExpect(jsonPath("$.parentId").value(parent))
                .andExpect(jsonPath("$.path").value("Versicherungen › Haftpflicht"))
                .andExpect(jsonPath("$.icon").value("shield"));
    }

    @Test
    void topLevelCategoryHasNoParentAndItsNameAsPath() throws Exception {
        postCategory("{\"name\": \"Haustier\"}")
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.path").value("Haustier"));
    }

    // Decision 38 and R32: parents alphabetically, their subcategories right below them
    @Test
    void listsSubcategoriesRightBelowTheirParent() throws Exception {
        long parent = id("Versicherungen");
        postCategory("{\"name\": \"Rente\", \"parentId\": " + parent + "}").andExpect(status().isCreated());
        postCategory("{\"name\": \"Haftpflicht\", \"parentId\": " + parent + "}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$[18].name").value("Versicherungen"))
                .andExpect(jsonPath("$[19].path").value("Versicherungen › Haftpflicht"))
                .andExpect(jsonPath("$[20].path").value("Versicherungen › Rente"))
                .andExpect(jsonPath("$[21].name").value("Wohnen"));
    }

    // R32: the combination of parent and name is unique, a top-level name may come back as a subcategory
    @Test
    void allowsTheSameNameUnderDifferentParentsAndOnTopLevel() throws Exception {
        postCategory("{\"name\": \"Sonstiges\"}").andExpect(status().isCreated());
        postCategory("{\"name\": \"Sonstiges\", \"parentId\": " + id("Freizeit") + "}")
                .andExpect(status().isCreated());
        postCategory("{\"name\": \"Sonstiges\", \"parentId\": " + id("Wohnen") + "}")
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsDuplicateNameUnderTheSameParentIgnoringCase() throws Exception {
        long parent = id("Freizeit");
        postCategory("{\"name\": \"Kino\", \"parentId\": " + parent + "}").andExpect(status().isCreated());

        postCategory("{\"name\": \"kino\", \"parentId\": " + parent + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Eine Kategorie mit dem Namen \"kino\" gibt es schon."));
    }

    @Test
    void rejectsRenamingIntoADuplicateUnderTheSameParent() throws Exception {
        long parent = id("Freizeit");
        postCategory("{\"name\": \"Kino\", \"parentId\": " + parent + "}").andExpect(status().isCreated());
        long theater = created(postCategory("{\"name\": \"Theater\", \"parentId\": " + parent + "}"));

        putCategory(theater, "{\"name\": \"Kino\", \"archived\": false, \"parentId\": " + parent + "}")
                .andExpect(status().isConflict());
    }

    // R15: another user's category is 400 on parentId, like an unknown one
    @Test
    void rejectsUnknownOrForeignParent() throws Exception {
        User other = TestUsers.create(userService, "subcategories-b@example.test");
        long foreign = categoryRepository.findAllByOwner(other).getFirst().getId();

        for (long parent : new long[] {999_999L, foreign}) {
            postCategory("{\"name\": \"Haftpflicht\", \"parentId\": " + parent + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("parentId"))
                    .andExpect(jsonPath("$.errors[0].message").value("Die Oberkategorie existiert nicht."));
        }
    }

    @Test
    void rejectsArchivedParent() throws Exception {
        long parent = id("Urlaub");
        putCategory(parent, "{\"name\": \"Urlaub\", \"archived\": true}").andExpect(status().isOk());

        postCategory("{\"name\": \"Flüge\", \"parentId\": " + parent + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parentId"))
                .andExpect(jsonPath("$.errors[0].message").value("Die Oberkategorie ist archiviert."));
    }

    // R32: only one level
    @Test
    void rejectsASubcategoryAsParent() throws Exception {
        long sub = created(postCategory("{\"name\": \"Kino\", \"parentId\": " + id("Freizeit") + "}"));

        postCategory("{\"name\": \"Popcorn\", \"parentId\": " + sub + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parentId"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Eine Unterkategorie kann keine Unterkategorien haben."));
    }

    @Test
    void rejectsAParentForACategoryWithSubcategories() throws Exception {
        long freizeit = id("Freizeit");
        postCategory("{\"name\": \"Kino\", \"parentId\": " + freizeit + "}").andExpect(status().isCreated());

        putCategory(freizeit, "{\"name\": \"Freizeit\", \"archived\": false, \"parentId\": " + id("Urlaub") + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parentId"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Eine Kategorie mit Unterkategorien kann keine Oberkategorie haben."));
    }

    @Test
    void rejectsTheCategoryItselfAsParent() throws Exception {
        long urlaub = id("Urlaub");

        putCategory(urlaub, "{\"name\": \"Urlaub\", \"archived\": false, \"parentId\": " + urlaub + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parentId"));
    }

    // R32: a category can get or lose a parent at any time
    @Test
    void movesACategoryUnderAParentAndBack() throws Exception {
        long urlaub = id("Urlaub");
        long freizeit = id("Freizeit");

        putCategory(urlaub, "{\"name\": \"Urlaub\", \"archived\": false, \"parentId\": " + freizeit + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("Freizeit › Urlaub"));
        putCategory(urlaub, "{\"name\": \"Urlaub\", \"archived\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.path").value("Urlaub"));
    }

    // R32: archiving a parent archives its subcategories; a subcategory comes back only with an active parent
    @Test
    void archivesSubcategoriesWithTheirParent() throws Exception {
        long freizeit = id("Freizeit");
        long kino = created(postCategory("{\"name\": \"Kino\", \"parentId\": " + freizeit + "}"));

        putCategory(freizeit, "{\"name\": \"Freizeit\", \"archived\": true}").andExpect(status().isOk());
        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$[?(@.id == " + kino + ")].archived").value(true));

        putCategory(kino, "{\"name\": \"Kino\", \"archived\": false, \"parentId\": " + freizeit + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Die Oberkategorie ist archiviert. Reaktiviere zuerst die Oberkategorie."));

        putCategory(freizeit, "{\"name\": \"Freizeit\", \"archived\": false}").andExpect(status().isOk());
        putCategory(kino, "{\"name\": \"Kino\", \"archived\": false, \"parentId\": " + freizeit + "}")
                .andExpect(status().isOk());
    }

    @Test
    void rejectsDeletingAParentWithSubcategories() throws Exception {
        long freizeit = id("Freizeit");
        long kino = created(postCategory("{\"name\": \"Kino\", \"parentId\": " + freizeit + "}"));

        mockMvc.perform(delete("/api/categories/{id}", freizeit))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Die Kategorie hat Unterkategorien. Lösche oder verschiebe zuerst die Unterkategorien."));

        mockMvc.perform(delete("/api/categories/{id}", kino)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/categories/{id}", freizeit)).andExpect(status().isNoContent());
    }

    // US16: deleting an account removes subcategories before their parents
    @Test
    void deletesAllCategoriesOfAUserWithSubcategories() throws Exception {
        postCategory("{\"name\": \"Kino\", \"parentId\": " + id("Freizeit") + "}").andExpect(status().isCreated());

        categoryRepository.deleteByOwner(user);

        org.assertj.core.api.Assertions.assertThat(categoryRepository.findAllByOwner(user)).isEmpty();
    }

    private ResultActions postCategory(String body) throws Exception {
        return mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putCategory(long id, String body) throws Exception {
        return mockMvc.perform(put("/api/categories/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long created(ResultActions result) throws Exception {
        result.andExpect(status().isCreated());
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long id(String name) {
        return categoryRepository.findAllByOwner(user).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }
}
