package io.github.mirco104.budget.category;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the default categories from the spec (section 4) as one flat list when the table is empty.
 * Targets stay empty; the user sets them in the app.
 */
@Component
public class CategorySeeder implements ApplicationRunner {

    private static final List<String> NAMES = List.of(
            "Wohnen", "Nebenkosten", "Versicherungen", "Mobilität", "Kredite", "Spenden",
            "Lebensmittel", "Freizeit", "Urlaub", "Persönlicher Bedarf", "Geschenke", "Gesundheit",
            "Investitionen", "Selbstständigkeit",
            "Gehalt/Lohn (netto)", "Nebenjob", "Einnahmen Selbstständigkeit",
            "Kapitalerträge", "Staatliche Leistungen", "Sonstige Einnahmen");

    private final CategoryRepository categoryRepository;

    public CategorySeeder(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            return;
        }

        List<Category> categories = new ArrayList<>();
        for (String name : NAMES) {
            categories.add(new Category(name, (categories.size() + 1) * 10));
        }
        categoryRepository.saveAll(categories);
    }
}
