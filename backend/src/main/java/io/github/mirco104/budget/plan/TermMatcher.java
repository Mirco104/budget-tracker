package io.github.mirco104.budget.plan;

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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Which term of a planned item a booking fulfils, rule P5 (with the match of R28) from docs/spezifikation.md,
 * section 19. The date may differ by the tolerance of the term, at most 5 days and half the spacing. No Spring
 * dependencies.
 */
public final class TermMatcher {
    /** Largest difference of the amounts as a share of the planned amount (R28). */
    static final BigDecimal MAX_SHARE = new BigDecimal("0.20");
    /** A shared word needs at least this many letters (R28). */
    static final int MIN_WORD = 4;

    /** A new booking: index in the caller's list, the text of R29 and its suggested category (null without one). */
    public record Booking(int index, LocalDate date, BigDecimal amount, String text, Long suggestedCategoryId) {
    }

    /**
     * An open term with the values of its item version: date is the planned date (the key of the term), dueDate the
     * date after the working-day shift, toleranceDays from {@link TermSchedule#toleranceDays}.
     */
    public record Planned(
            long itemId, UUID itemKey, LocalDate date, LocalDate dueDate, BigDecimal amount, String description,
            Long categoryId, int toleranceDays) {
    }

    private TermMatcher() {
    }

    /** P5: per booking index the term it fulfils; each booking and each term at most once. */
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
                            .thenComparingLong(occurrence -> days(booking.date(), occurrence.dueDate()))
                            .thenComparing(occurrence -> booking.amount().subtract(occurrence.amount()).abs())
                            .thenComparingLong(Planned::itemId))
                    .ifPresent(occurrence -> {
                        result.put(booking.index(), occurrence);
                        used.add(occurrence);
                    });
        }
        return result;
    }

    private static boolean matches(Booking booking, Set<String> words, Planned occurrence) {
        if (booking.amount().signum() != occurrence.amount().signum()
                || days(booking.date(), occurrence.dueDate()) > occurrence.toleranceDays()) {
            return false;
        }
        BigDecimal difference = booking.amount().subtract(occurrence.amount()).abs();
        if (difference.compareTo(occurrence.amount().abs().multiply(MAX_SHARE)) > 0) {
            return false;
        }
        boolean sharedWord = sharesWord(words, occurrence);
        boolean sameAmountAndCategory = difference.signum() == 0
                && occurrence.categoryId() != null
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
