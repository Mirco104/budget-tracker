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
        assertThat(categoryRepository.count()).isEqualTo(20);
        assertThat(categoryRepository.findAll()).extracting(Category::getName)
                .doesNotContain("Sonstiges (berechneter Rest)");
    }

    @Test
    void seedsUniqueNamesAsOneListInSpecOrder() {
        List<String> names = categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(Category::getName)
                .toList();

        assertThat(names).doesNotHaveDuplicates().containsExactly(
                "Wohnen", "Nebenkosten", "Versicherungen", "Mobilität", "Kredite", "Spenden",
                "Lebensmittel", "Freizeit", "Urlaub", "Persönlicher Bedarf", "Geschenke", "Gesundheit",
                "Investitionen", "Selbstständigkeit",
                "Gehalt/Lohn (netto)", "Nebenjob", "Einnahmen Selbstständigkeit",
                "Kapitalerträge", "Staatliche Leistungen", "Sonstige Einnahmen");
    }

    @Test
    void doesNotSeedTwice() {
        categorySeeder.run(new DefaultApplicationArguments());

        assertThat(categoryRepository.count()).isEqualTo(20);
    }
}
