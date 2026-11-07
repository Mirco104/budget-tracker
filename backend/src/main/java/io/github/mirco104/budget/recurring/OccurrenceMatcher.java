package io.github.mirco104.budget.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which planned occurrence a new booking replaces, rule R28 from docs/spezifikation.md, section 2. No Spring
 * dependencies.
 */
public final class OccurrenceMatcher {

    /** Days between the booking and the occurrence (R28). */
    static final int MAX_DAYS = 5;
    /** Largest difference of the amounts as a share of the amount of the series (R28). */
    static final BigDecimal MAX_SHARE = new BigDecimal("0.20");
    /** A shared word needs at least this many letters (R28). */
    static final int MIN_WORD = 4;

    /** A new booking: index in the caller's list, the text of R29 and its suggested category (null without one). */
    public record Booking(int index, LocalDate date, BigDecimal amount, String text, Long suggestedCategoryId) {
    }

    /** An occurrence that still counts (no exception), with the values of its series. */
    public record Planned(long recurringId, LocalDate date, BigDecimal amount, String description, long categoryId) {
    }

    private OccurrenceMatcher() {
    }

    /** R28: per booking index the occurrence it replaces; each booking and each occurrence at most once. */
    public static Map<Integer, Planned> match(List<Booking> bookings, List<Planned> planned) {
        Map<Integer, Planned> result = new LinkedHashMap<>();
        Set<Planned> used = new HashSet<>();
        List<Booking> byDate = bookings.stream()
                .sorted(Comparator.comparing(Booking::date).thenComparingInt(Booking::index))
                .toList();
        for (Booking booking : byDate) {
            Set<String> words = longWords(booking.text());
            planned.stream()
                    .filter(occurrence -> !used.contains(occurrence) && matches(booking, words, occurrence))
                    // A shared word first, so two subscriptions with the same price are not mixed up
                    .min(Comparator.<Planned, Boolean>comparing(occurrence -> !sharesWord(words, occurrence))
                            .thenComparingLong(occurrence -> days(booking.date(), occurrence.date()))
                            .thenComparing(occurrence -> booking.amount().subtract(occurrence.amount()).abs())
                            .thenComparingLong(Planned::recurringId))
                    .ifPresent(occurrence -> {
                        result.put(booking.index(), occurrence);
                        used.add(occurrence);
                    });
        }
        return result;
    }

    private static boolean matches(Booking booking, Set<String> words, Planned occurrence) {
        if (booking.amount().signum() != occurrence.amount().signum()
                || days(booking.date(), occurrence.date()) > MAX_DAYS) {
            return false;
        }
        BigDecimal difference = booking.amount().subtract(occurrence.amount()).abs();
        if (difference.compareTo(occurrence.amount().abs().multiply(MAX_SHARE)) > 0) {
            return false;
        }
        boolean sharedWord = sharesWord(words, occurrence);
        boolean sameAmountAndCategory = difference.signum() == 0
                && Objects.equals(booking.suggestedCategoryId(), occurrence.categoryId());
        return sharedWord || sameAmountAndCategory;
    }

    private static boolean sharesWord(Set<String> words, Planned occurrence) {
        return longWords(occurrence.description()).stream().anyMatch(words::contains);
    }

    // Normalized like R29: lower case, everything but letters separates words; only words of 4 letters or more
    private static Set<String> longWords(String text) {
        if (text == null) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.GERMAN).split("[^\\p{L}]+"))
                .filter(word -> word.length() >= MIN_WORD)
                .collect(Collectors.toSet());
    }

    private static long days(LocalDate a, LocalDate b) {
        return Math.abs(ChronoUnit.DAYS.between(a, b));
    }
}
