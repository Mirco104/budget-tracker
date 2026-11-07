package io.github.mirco104.budget.category;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Decision 38: categories are listed alphabetically everywhere. German collation, so umlauts sort like their base
 * letter and case does not matter; ties (names differing only in case or accents) by id. Sorted in Java, because
 * H2 and PostgreSQL order text differently.
 */
public final class CategoryOrder {

    private static final Collator COLLATOR = collator();

    public static final Comparator<Category> ALPHABETICAL =
            Comparator.comparing(Category::getName, COLLATOR::compare).thenComparing(Category::getId);

    private CategoryOrder() {
    }

    private static Collator collator() {
        Collator collator = Collator.getInstance(Locale.GERMAN);
        // Primary: "a", "A" and "ä" count as the same letter
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }
}
