package io.github.mirco104.budget.recurring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Occurrences of one recurring transaction, rule R12 from docs/spezifikation.md, section 2. No Spring dependencies.
 * Occurrence k lies at startDate + k × interval months. Each date is derived from the start date, not from the
 * previous occurrence, so a start on the 31st returns to the 31st after a shorter month.
 */
public class RecurrenceSchedule {

    private static final int MONEY_SCALE = 2;

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final RecurringInterval interval;
    private final Set<LocalDate> exceptions;

    /** endDate is inclusive and may be null (open-ended); exceptions are suspended occurrences (R13). */
    public RecurrenceSchedule(
            LocalDate startDate, LocalDate endDate, RecurringInterval interval, Set<LocalDate> exceptions) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.interval = interval;
        this.exceptions = Set.copyOf(exceptions);
    }

    /** Occurrences that count, from and to inclusive, in ascending order. */
    public List<LocalDate> occurrencesBetween(LocalDate from, LocalDate to) {
        List<LocalDate> occurrences = new ArrayList<>();
        LocalDate last = endDate == null || to.isBefore(endDate) ? to : endDate;
        for (long k = firstIndexFrom(from); ; k++) {
            LocalDate occurrence = scheduled(k);
            if (occurrence.isAfter(last)) {
                return occurrences;
            }
            if (!occurrence.isBefore(from) && !exceptions.contains(occurrence)) {
                occurrences.add(occurrence);
            }
        }
    }

    /** True if the date is on the schedule, not after the end date and not suspended by an exception. */
    public boolean isOccurrence(LocalDate date) {
        return indexOf(date).isPresent()
                && (endDate == null || !date.isAfter(endDate))
                && !exceptions.contains(date);
    }

    /** The scheduled date before the given one, regardless of exceptions; empty for the first occurrence. */
    public Optional<LocalDate> previousOccurrence(LocalDate date) {
        return indexOf(date)
                .filter(k -> k > 0)
                .map(k -> scheduled(k - 1));
    }

    /** |amount| / interval in months, rounded to cents (spec section 5, R14). */
    public static BigDecimal monthlyEquivalent(BigDecimal amount, RecurringInterval interval) {
        return amount.abs().divide(BigDecimal.valueOf(interval.months()), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private LocalDate scheduled(long k) {
        return startDate.plusMonths(k * interval.months());
    }

    // Index of the first scheduled date in or after the month of "from"; never before the start
    private long firstIndexFrom(LocalDate from) {
        long months = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(from));
        return months <= 0 ? 0 : months / interval.months();
    }

    private Optional<Long> indexOf(LocalDate date) {
        long months = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(date));
        if (months < 0 || months % interval.months() != 0) {
            return Optional.empty();
        }
        long k = months / interval.months();
        return scheduled(k).equals(date) ? Optional.of(k) : Optional.empty();
    }
}
