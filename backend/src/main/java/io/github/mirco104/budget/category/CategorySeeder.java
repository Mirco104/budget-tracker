package io.github.mirco104.budget.category;

import static io.github.mirco104.budget.category.CategoryGroup.FIXED;
import static io.github.mirco104.budget.category.CategoryGroup.FLEXIBLE;
import static io.github.mirco104.budget.category.CategoryGroup.REMAINDER;
import static io.github.mirco104.budget.category.CategoryGroup.SAVINGS;
import static io.github.mirco104.budget.category.CategoryType.EXPENSE;
import static io.github.mirco104.budget.category.CategoryType.INCOME;

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

    private static final String REMAINDER_NAME = "Sonstiges (berechneter Rest)";

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
        addExpenses(categories, FIXED,
                "Wohnen", "Nebenkosten", "Versicherungen", "Mobilität", "Kredite", "Spenden");
        addExpenses(categories, FLEXIBLE,
                "Lebensmittel", "Freizeit", "Urlaub", "Persönlicher Bedarf", "Geschenke", "Gesundheit");
        addExpenses(categories, SAVINGS,
                "Investitionen", "Selbstständigkeit");
        categories.add(new Category(REMAINDER_NAME, EXPENSE, REMAINDER, nextSortOrder(categories), true));
        for (String name : List.of("Gehalt/Lohn (netto)", "Nebenjob", "Selbstständigkeit",
                "Kapitalerträge", "Staatliche Leistungen", "Sonstiges")) {
            categories.add(new Category(name, INCOME, null, nextSortOrder(categories), false));
        }

        categoryRepository.saveAll(categories);
    }

    private static void addExpenses(List<Category> categories, CategoryGroup group, String... names) {
        for (String name : names) {
            categories.add(new Category(name, EXPENSE, group, nextSortOrder(categories), false));
        }
    }

    private static int nextSortOrder(List<Category> categories) {
        return (categories.size() + 1) * 10;
    }
}
