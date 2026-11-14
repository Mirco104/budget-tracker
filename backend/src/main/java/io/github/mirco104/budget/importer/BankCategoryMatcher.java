package io.github.mirco104.budget.importer;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * R33: matches the category of the bank ("Parent › Child", R30) with the active own categories of the user. A part is
 * EXISTING when its normalized name is equal, GUESSED when only the endings or a small typo differ, otherwise NEW. No
 * Spring dependencies.
 */
public final class BankCategoryMatcher {

    /** The separator of the parent and the child in the bank category (R30). */
    public static final String SEPARATOR = " › ";

    private static final List<String> ENDINGS = List.of("en", "n", "e", "s");
    private static final Collator COLLATOR = Collator.getInstance(Locale.GERMAN);

    public enum Status { EXISTING, GUESSED, NEW }

    /** An active own category; parentId is null for a top-level category. */
    public record Own(long id, String name, Long parentId) {
    }

    /** The match of one part; categoryId is null for NEW. */
    public record Part(Status status, Long categoryId) {
    }

    /** child and childMatch are null without a child; a NEW parent makes the child NEW too. */
    public record Result(String parent, String child, Part parentMatch, Part childMatch) {

        /** The category the bank category points to without asking: the child if there is one, else the parent. */
        public Long categoryId() {
            return child == null ? parentMatch.categoryId() : childMatch.categoryId();
        }
    }

    private BankCategoryMatcher() {
    }

    public static Result match(String bankCategory, List<Own> own) {
        int at = bankCategory.indexOf(SEPARATOR);
        String parent = (at < 0 ? bankCategory : bankCategory.substring(0, at)).trim();
        String child = at < 0 ? null : blankToNull(bankCategory.substring(at + SEPARATOR.length()).trim());

        Part parentMatch = best(parent, own.stream().filter(o -> o.parentId() == null).toList());
        Part childMatch = null;
        if (child != null) {
            childMatch = parentMatch.categoryId() == null
                    ? new Part(Status.NEW, null)
                    : best(child, own.stream().filter(o -> parentMatch.categoryId().equals(o.parentId())).toList());
        }
        return new Result(parent, child, parentMatch, childMatch);
    }

    /** Lower case, umlauts as two letters, anything but letters and digits as one space. */
    public static String normalize(String name) {
        String lower = name.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        return lower.replaceAll("[^a-z0-9]+", " ").trim();
    }

    // EXISTING beats GUESSED; among guesses the smallest distance, then the alphabetically first name
    private static Part best(String name, List<Own> candidates) {
        String normalized = normalize(name);
        Optional<Own> equal = candidates.stream()
                .filter(o -> normalize(o.name()).equals(normalized))
                .min(Comparator.comparing(Own::name, COLLATOR));
        if (equal.isPresent()) {
            return new Part(Status.EXISTING, equal.get().id());
        }
        String stem = stem(normalized);
        record Guess(Own own, int distance) {
        }
        return candidates.stream()
                .map(o -> {
                    String other = normalize(o.name());
                    if (stem(other).equals(stem)) {
                        return new Guess(o, 0);
                    }
                    int distance = levenshtein(normalized, other);
                    return distance <= allowedTypos(normalized) ? new Guess(o, distance) : null;
                })
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparingInt(Guess::distance).thenComparing(g -> g.own().name(), COLLATOR))
                .map(g -> new Part(Status.GUESSED, g.own().id()))
                .orElse(new Part(Status.NEW, null));
    }

    private static int allowedTypos(String normalized) {
        int length = normalized.length();
        return length >= 10 ? 2 : length >= 5 ? 1 : 0;
    }

    // Each word without one of the endings, the longest first
    private static String stem(String normalized) {
        return Stream.of(normalized.split(" "))
                .map(word -> ENDINGS.stream()
                        .filter(ending -> word.length() > ending.length() + 2 && word.endsWith(ending))
                        .findFirst()
                        .map(ending -> word.substring(0, word.length() - ending.length()))
                        .orElse(word))
                .collect(Collectors.joining(" "));
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
