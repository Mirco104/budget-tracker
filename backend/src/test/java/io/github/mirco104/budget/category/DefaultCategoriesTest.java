package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.TestUsers;
import io.github.mirco104.budget.user.User;
import io.github.mirco104.budget.user.UserService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every new user gets the default categories from the spec, without targets (section 4, US15). The repository
 * returns them in no particular order (PostgreSQL and H2 differ); the app sorts them alphabetically (decision 38).
 */
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
    void seedsTheUniqueNamesOfTheSpec() {
        User user = TestUsers.create(userService, "person-b@example.test");

        List<String> names = categoryRepository.findAllByOwner(user).stream()
                .map(Category::getName)
                .toList();

        assertThat(names).doesNotHaveDuplicates().containsExactlyInAnyOrder(
                "Wohnen", "Nebenkosten", "Versicherungen", "Mobilität", "Kredite", "Spenden",
                "Lebensmittel", "Freizeit", "Urlaub", "Persönlicher Bedarf", "Geschenke", "Gesundheit",
                "Investitionen", "Selbstständigkeit",
                "Gehalt/Lohn (netto)", "Nebenjob", "Einnahmen Selbstständigkeit",
                "Kapitalerträge", "Staatliche Leistungen", "Sonstige Einnahmen");
        assertThat(names).doesNotContain("Sonstiges (berechneter Rest)");
    }

    // US28: icons of the default categories (section 4)
    @Test
    void seedsMatchingIcons() {
        User user = TestUsers.create(userService, "person-b@example.test");

        Map<String, String> icons = categoryRepository.findAllByOwner(user).stream()
                .collect(Collectors.toMap(Category::getName, Category::getIcon));

        assertThat(icons).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("Wohnen", "home"), Map.entry("Nebenkosten", "bolt"),
                Map.entry("Versicherungen", "shield"), Map.entry("Mobilität", "car"),
                Map.entry("Kredite", "card"), Map.entry("Spenden", "heart"),
                Map.entry("Lebensmittel", "cart"), Map.entry("Freizeit", "music"),
                Map.entry("Urlaub", "suitcase"), Map.entry("Persönlicher Bedarf", "bag"),
                Map.entry("Geschenke", "gift"), Map.entry("Gesundheit", "pulse"),
                Map.entry("Investitionen", "trend"), Map.entry("Selbstständigkeit", "briefcase"),
                Map.entry("Gehalt/Lohn (netto)", "wallet"), Map.entry("Nebenjob", "laptop"),
                Map.entry("Einnahmen Selbstständigkeit", "receipt"), Map.entry("Kapitalerträge", "coins"),
                Map.entry("Staatliche Leistungen", "landmark"), Map.entry("Sonstige Einnahmen", "plus")));
    }

    @Test
    void seedsOwnCategoriesWithoutTargetsForEveryUser() {
        User b = TestUsers.create(userService, "person-b@example.test");
        User c = TestUsers.create(userService, "person-c@example.test");

        List<Category> ofB = categoryRepository.findAllByOwner(b);
        List<Category> ofC = categoryRepository.findAllByOwner(c);

        assertThat(ofB).hasSize(20);
        assertThat(ofC).hasSize(20);
        assertThat(ofB).extracting(Category::getId).doesNotContainAnyElementsOf(
                ofC.stream().map(Category::getId).toList());
        assertThat(categoryTargetRepository.findAll()).isEmpty();
    }
}
