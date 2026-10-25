package io.github.mirco104.budget.category;

import static io.github.mirco104.budget.category.CategoryGroup.FIXED;
import static io.github.mirco104.budget.category.CategoryGroup.FLEXIBLE;
import static io.github.mirco104.budget.category.CategoryGroup.SAVINGS;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the default categories from the spec (section 4) when the table is empty.
 * Targets stay empty; the user sets them in the app.
 */
@Component
public class CategorySeeder implements ApplicationRunner {

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
        addGroup(categories, FIXED,
                "Wohnen", "Nebenkosten", "Versicherungen", "Mobilität", "Kredite", "Spenden");
        addGroup(categories, FLEXIBLE,
                "Lebensmittel", "Freizeit", "Urlaub", "Persönlicher Bedarf", "Geschenke", "Gesundheit");
        addGroup(categories, SAVINGS,
                "Investitionen", "Selbstständigkeit");
        addGroup(categories, null, "Gehalt/Lohn (netto)", "Nebenjob", "Einnahmen Selbstständigkeit",
                "Kapitalerträge", "Staatliche Leistungen", "Sonstige Einnahmen");

        categoryRepository.saveAll(categories);
    }

    // group null means "ohne Gruppe"
    private static void addGroup(List<Category> categories, CategoryGroup group, String... names) {
        for (String name : names) {
            categories.add(new Category(name, group, nextSortOrder(categories)));
        }
    }

    private static int nextSortOrder(List<Category> categories) {
        return (categories.size() + 1) * 10;
    }
}
