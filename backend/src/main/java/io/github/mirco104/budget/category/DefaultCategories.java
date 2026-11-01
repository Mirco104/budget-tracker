package io.github.mirco104.budget.category;

import io.github.mirco104.budget.user.User;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The default categories from the spec (section 4) as one flat list, created for every new user (US15).
 * Targets stay empty; the user sets them in the app.
 */
@Component
public class DefaultCategories {

    static final List<String> NAMES = List.of(
            "Wohnen", "Nebenkosten", "Versicherungen", "Mobilität", "Kredite", "Spenden",
            "Lebensmittel", "Freizeit", "Urlaub", "Persönlicher Bedarf", "Geschenke", "Gesundheit",
            "Investitionen", "Selbstständigkeit",
            "Gehalt/Lohn (netto)", "Nebenjob", "Einnahmen Selbstständigkeit",
            "Kapitalerträge", "Staatliche Leistungen", "Sonstige Einnahmen");

    private final CategoryRepository categoryRepository;

    public DefaultCategories(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void seedFor(User owner) {
        List<Category> categories = new ArrayList<>();
        for (String name : NAMES) {
            categories.add(new Category(owner, name, (categories.size() + 1) * 10));
        }
        categoryRepository.saveAll(categories);
    }
}
