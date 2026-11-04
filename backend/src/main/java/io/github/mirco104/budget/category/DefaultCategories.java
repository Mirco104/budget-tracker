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

    /** Name and icon (US28) of each default category, in their order. */
    record DefaultCategory(String name, String icon) {
    }

    static final List<DefaultCategory> CATEGORIES = List.of(
            new DefaultCategory("Wohnen", "home"),
            new DefaultCategory("Nebenkosten", "bolt"),
            new DefaultCategory("Versicherungen", "shield"),
            new DefaultCategory("Mobilität", "car"),
            new DefaultCategory("Kredite", "card"),
            new DefaultCategory("Spenden", "heart"),
            new DefaultCategory("Lebensmittel", "cart"),
            new DefaultCategory("Freizeit", "music"),
            new DefaultCategory("Urlaub", "suitcase"),
            new DefaultCategory("Persönlicher Bedarf", "bag"),
            new DefaultCategory("Geschenke", "gift"),
            new DefaultCategory("Gesundheit", "pulse"),
            new DefaultCategory("Investitionen", "trend"),
            new DefaultCategory("Selbstständigkeit", "briefcase"),
            new DefaultCategory("Gehalt/Lohn (netto)", "wallet"),
            new DefaultCategory("Nebenjob", "laptop"),
            new DefaultCategory("Einnahmen Selbstständigkeit", "receipt"),
            new DefaultCategory("Kapitalerträge", "coins"),
            new DefaultCategory("Staatliche Leistungen", "landmark"),
            new DefaultCategory("Sonstige Einnahmen", "plus"));

    private final CategoryRepository categoryRepository;

    public DefaultCategories(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void seedFor(User owner) {
        List<Category> categories = new ArrayList<>();
        for (DefaultCategory category : CATEGORIES) {
            categories.add(new Category(owner, category.name(), (categories.size() + 1) * 10, category.icon()));
        }
        categoryRepository.saveAll(categories);
    }
}
