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
 * section 19. The date may differ by the tolerance of the term, at most 5 days and half the spacing; income of a
 * monthly item may come up to 10 days early (decision 78). No Spring dependencies.
 */
public final class TermMatcher {
    /** Largest difference of the amounts as a share of the planned amount (R28). */
    static final BigDecimal MAX_SHARE = new BigDecimal("0.20");
    /** A shared word needs at least this many letters (R28). */
    static final int MIN_WORD = 4;

    /**
     * A new booking: index in the caller's list, the text of R29 and its suggested category (null without one); saving
     * when it is suggested as the kind Sparen (decision 67).
     */
    public record Booking(int index, LocalDate date, BigDecimal amount, String text, Long suggestedCategoryId,
            boolean saving) {

        public Booking(int index, LocalDate date, BigDecimal amount, String text, Long suggestedCategoryId) {
            this(index, date, amount, text, suggestedCategoryId, false);
        }
    }

    /**
     * An open term with the values of its item version: date is the planned date (the key of the term), dueDate the
     * date after the working-day shift, toleranceDays from {@link TermSchedule#toleranceDays}, earlyDays (how far
     * before the due date) from {@link TermSchedule#earlyDays}.
     */
    public record Planned(
            long itemId, UUID itemKey, LocalDate date, LocalDate dueDate, BigDecimal amount, String description,
            Long categoryId, int toleranceDays, int earlyDays) {

        public Planned(long itemId, UUID itemKey, LocalDate date, LocalDate dueDate, BigDecimal amount,
                String description, Long categoryId, int toleranceDays) {
            this(itemId, itemKey, date, dueDate, amount, description, categoryId, toleranceDays, toleranceDays);
        }
    }

    private TermMatcher() {
    }

    /**
     * P5: per booking index the term it fulfils; each booking and each term at most once. Matches by a shared word or
     * the same amount and category first, then those by the same amount alone (decision 78).
     */
    public static Map<Integer, Planned> match(List<Booking> bookings, List<Planned> planned) {
        Map<Integer, Planned> result = new LinkedHashMap<>();
        Set<Planned> used = new HashSet<>();
        List<Booking> byDate = bookings.stream()
                .sorted(Comparator.comparing(Booking::date).thenComparingInt(Booking::index))
                .toList();
        assign(byDate, planned, result, used, false);
        assign(byDate.stream().filter(booking -> !result.containsKey(booking.index())).toList(), planned, result, used,
                true);
        return result;
    }

    private static void assign(List<Booking> byDate, List<Planned> planned, Map<Integer, Planned> result,
            Set<Planned> used, boolean amountAlone) {
        for (Booking booking : byDate) {
            Set<String> words = longWords(booking.text());
            planned.stream()
                    .filter(occurrence -> !used.contains(occurrence) && matches(booking, words, occurrence, amountAlone))
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
    }

    private static boolean matches(Booking booking, Set<String> words, Planned occurrence, boolean amountAlone) {
        if (booking.amount().signum() != occurrence.amount().signum() || !inDays(booking, occurrence)) {
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
        // Decision 67: a saving item has no category; the same amount counts for a booking suggested as saving
        boolean sameAmountSaving = difference.signum() == 0 && occurrence.categoryId() == null && booking.saving();
        if (!amountAlone) {
            return sharedWord || sameAmountAndCategory || sameAmountSaving;
        }
        // Decision 78: the same amount alone, for a row without suggestion and an item with a category
        return difference.signum() == 0 && occurrence.categoryId() != null && booking.suggestedCategoryId() == null
                && !booking.saving();
    }

    // Up to toleranceDays after the due date, up to earlyDays before it
    private static boolean inDays(Booking booking, Planned occurrence) {
        long after = ChronoUnit.DAYS.between(occurrence.dueDate(), booking.date());
        return after >= 0 ? after <= occurrence.toleranceDays() : -after <= occurrence.earlyDays();
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
