package io.github.mirco104.budget.category;

import java.util.List;

/**
 * The fixed list of icon keys for categories (US28, section 4). Only the key is stored; the frontend draws the
 * icon. The list can grow without a schema change.
 */
public final class CategoryIcons {

    public static final String DEFAULT = "tag";

    public static final List<String> KEYS = List.of(
            "home", "bolt", "shield", "car", "card", "heart", "cart", "music", "suitcase", "bag", "gift",
            "pulse", "trend", "briefcase", "wallet", "laptop", "receipt", "coins", "landmark", "plus", DEFAULT);

    private CategoryIcons() {
    }

    public static boolean isKnown(String key) {
        return KEYS.contains(key);
    }
}
