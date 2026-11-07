package io.github.mirco104.budget.suggestion;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Category suggestion, rule R29 from docs/spezifikation.md, section 2. No Spring dependencies. The suggestion comes
 * from the user's own history; it is certain only when the latest entries with the same key agree (decision 41).
 */
public final class CategorySuggester {

    /** How many of the latest matching entries count (R29). */
    static final int LATEST = 10;
    /** The first word must have at least this many letters for rule (3). */
    static final int MIN_FIRST_WORD = 4;

    /**
     * One entry of the history: a transaction with a category (no transfer, R27) or a series. text is the
     * counterparty when it is known, else the description; date is the date or the start date; income is a positive
     * amount; bankCategory is the category or kind the bank gave it (import, R30), else null.
     */
    public record Entry(String text, long categoryId, LocalDate date, boolean income, String bankCategory) {
    }

    /** A category that may be suggested: not archived. */
    public record CategoryData(long id, String name) {
    }

    public record Suggestion(long categoryId, boolean certain) {
    }

    private CategorySuggester() {
    }

    /** The normalized words of a text: lower case, everything but letters is a space, words of 2 letters or more. */
    static List<String> words(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.GERMAN).split("[^\\p{L}]+"))
                .filter(word -> word.length() >= 2)
                .toList();
    }

    /** R29: the first two normalized words, "" without any word. */
    public static String key(String text) {
        List<String> words = words(text);
        return String.join(" ", words.subList(0, Math.min(2, words.size())));
    }

    private static String firstWord(String text) {
        List<String> words = words(text);
        return words.isEmpty() ? "" : words.getFirst();
    }

    /**
     * R29 for a booking with the text, the sign (income true for a positive amount) and the bank category (null outside
     * the import). Only categories in the list (not archived) are suggested; history entries of other categories are
     * left out before anything is counted.
     */
    public static Optional<Suggestion> suggest(
            String text, boolean income, String bankCategory, List<Entry> history, List<CategoryData> categories) {
        Set<Long> allowed = categories.stream().map(CategoryData::id).collect(Collectors.toSet());
        List<Entry> usable = history.stream()
                .filter(entry -> allowed.contains(entry.categoryId()))
                .sorted(Comparator.comparing(Entry::date).reversed())
                .toList();
        List<Entry> sameSign = usable.stream().filter(entry -> entry.income() == income).toList();

        // (1) and (2): the latest entries with the same key
        String key = key(text);
        if (!key.isEmpty()) {
            List<Entry> matching = latest(sameSign, entry -> key(entry.text()).equals(key));
            if (!matching.isEmpty()) {
                boolean agree = matching.stream().map(Entry::categoryId).distinct().count() == 1;
                return Optional.of(new Suggestion(mostFrequent(matching), agree));
            }
            // (3): the same first word with at least 4 letters
            String first = firstWord(text);
            if (first.length() >= MIN_FIRST_WORD) {
                List<Entry> sameFirst = latest(sameSign, entry -> firstWord(entry.text()).equals(first));
                if (!sameFirst.isEmpty()) {
                    return Optional.of(new Suggestion(mostFrequent(sameFirst), false));
                }
            }
        }

        // (4): the bank category, from the history and then from the names of the categories
        if (bankCategory != null && !bankCategory.isBlank()) {
            List<Entry> sameBankCategory = latest(sameSign,
                    entry -> entry.bankCategory() != null && entry.bankCategory().equalsIgnoreCase(bankCategory.trim()));
            if (!sameBankCategory.isEmpty()) {
                return Optional.of(new Suggestion(mostFrequent(sameBankCategory), false));
            }
            return byName(bankCategory, categories).map(id -> new Suggestion(id, false));
        }
        return Optional.empty();
    }

    // The first LATEST entries (newest first) that match
    private static List<Entry> latest(List<Entry> entries, Predicate<Entry> matches) {
        return entries.stream().filter(matches).limit(LATEST).toList();
    }

    // The most frequent category; on a tie the one of the newest entry (entries are newest first)
    private static long mostFrequent(List<Entry> entries) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : entries) {
            counts.merge(entry.categoryId(), 1, Integer::sum);
        }
        int max = counts.values().stream().max(Integer::compare).orElseThrow();
        return counts.entrySet().stream()
                .filter(count -> count.getValue() == max)
                .findFirst()
                .orElseThrow()
                .getKey();
    }

    // Trade Republic kinds by their parts, else a category whose first word is the first word of the bank category
    private static Optional<Long> byName(String bankCategory, List<CategoryData> categories) {
        String kind = bankCategory.toUpperCase(Locale.ROOT);
        if (kind.contains("BUY") || kind.contains("SELL") || kind.contains("SAVINGS")) {
            return named("Investitionen", categories);
        }
        if (kind.contains("INTEREST") || kind.contains("DIVIDEND")) {
            return named("Kapitalerträge", categories);
        }
        String first = firstWord(bankCategory);
        if (first.isEmpty()) {
            return Optional.empty();
        }
        return categories.stream()
                .filter(category -> firstWord(category.name()).equals(first))
                .map(CategoryData::id)
                .findFirst();
    }

    private static Optional<Long> named(String name, List<CategoryData> categories) {
        return categories.stream()
                .filter(category -> category.name().equalsIgnoreCase(name))
                .map(CategoryData::id)
                .findFirst();
    }
}
