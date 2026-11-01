package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Every new user gets the default categories from the spec, without targets (section 4, US15). */
@SpringBootTest
@Transactional
class DefaultCategoriesTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryTargetRepository categoryTargetRepository;

    @Test
    void seedsUniqueNamesAsOneListInSpecOrder() {
        User user = TestUsers.create(userService, "person-b@example.test");

        List<String> names = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(user).stream()
                .map(Category::getName)
                .toList();

        assertThat(names).doesNotHaveDuplicates().containsExactly(
                "Wohnen", "Nebenkosten", "Versicherungen", "Mobilität", "Kredite", "Spenden",
                "Lebensmittel", "Freizeit", "Urlaub", "Persönlicher Bedarf", "Geschenke", "Gesundheit",
                "Investitionen", "Selbstständigkeit",
                "Gehalt/Lohn (netto)", "Nebenjob", "Einnahmen Selbstständigkeit",
                "Kapitalerträge", "Staatliche Leistungen", "Sonstige Einnahmen");
        assertThat(names).doesNotContain("Sonstiges (berechneter Rest)");
    }

    @Test
    void seedsOwnCategoriesWithoutTargetsForEveryUser() {
        User b = TestUsers.create(userService, "person-b@example.test");
        User c = TestUsers.create(userService, "person-c@example.test");

        List<Category> ofB = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(b);
        List<Category> ofC = categoryRepository.findAllByOwnerOrderBySortOrderAscIdAsc(c);

        assertThat(ofB).hasSize(20);
        assertThat(ofC).hasSize(20);
        assertThat(ofB).extracting(Category::getId).doesNotContainAnyElementsOf(
                ofC.stream().map(Category::getId).toList());
        assertThat(ofB).extracting(Category::getSortOrder).startsWith(10, 20, 30).endsWith(200);
        assertThat(categoryTargetRepository.findAll()).isEmpty();
    }
}
