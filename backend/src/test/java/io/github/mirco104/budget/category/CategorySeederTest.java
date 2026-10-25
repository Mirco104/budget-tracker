package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CategorySeederTest {

    @Autowired
    private CategorySeeder categorySeeder;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void seedsCategoriesOnStartup() {
        assertThat(categoryRepository.count()).isEqualTo(21);
        assertThat(categoryRepository.findAll())
                .filteredOn(Category::isSystem)
                .singleElement()
                .satisfies(c -> assertThat(c.getGroup()).isEqualTo(CategoryGroup.REMAINDER));
    }

    @Test
    void seedsUniqueNamesIncludingRenamedIncomeCategories() {
        List<String> names = categoryRepository.findAll().stream().map(Category::getName).toList();

        assertThat(names).doesNotHaveDuplicates()
                .contains("Selbstständigkeit", "Einnahmen Selbstständigkeit", "Sonstige Einnahmen");
        assertThat(categoryRepository.findAll())
                .filteredOn(c -> c.getGroup() == null)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Gehalt/Lohn (netto)", "Nebenjob", "Einnahmen Selbstständigkeit",
                        "Kapitalerträge", "Staatliche Leistungen", "Sonstige Einnahmen");
    }

    @Test
    void doesNotSeedTwice() {
        categorySeeder.run(new DefaultApplicationArguments());

        assertThat(categoryRepository.count()).isEqualTo(21);
    }
}
