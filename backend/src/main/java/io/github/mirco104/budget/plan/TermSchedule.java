package io.github.mirco104.budget.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Terms of a planned item, rule P5 from docs/spezifikation.md, section 19. No Spring dependencies.
 * Term k lies at anchor + k × count weeks or months. Month dates are derived from the anchor, not from the previous
 * term, so an anchor on the 31st returns to the 31st after a shorter month (R12). Terms lie only in months in which
 * the item is valid.
 */
public class TermSchedule {

    private static final int MAX_TOLERANCE_DAYS = 5;

    private final RhythmUnit unit;
    private final int count;
    private final LocalDate anchor;
    private final YearMonth validFrom;
    private final YearMonth validTo;

    /** validTo is inclusive and may be null (open-ended). */
    public TermSchedule(RhythmUnit unit, int count, LocalDate anchor, YearMonth validFrom, YearMonth validTo) {
        this.unit = unit;
        this.count = count;
        this.anchor = anchor;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    /** Planned term dates (before the working-day shift) from and to inclusive, ascending. */
    public List<LocalDate> termsBetween(LocalDate from, LocalDate to) {
        List<LocalDate> terms = new ArrayList<>();
        LocalDate first = later(from, validFrom.atDay(1));
        LocalDate last = validTo == null || to.isBefore(validTo.atEndOfMonth()) ? to : validTo.atEndOfMonth();
        if (last.isBefore(first)) {
            return terms;
        }
        for (long k = firstIndexFrom(first); ; k++) {
            LocalDate term = scheduled(k);
            if (term.isAfter(last)) {
                return terms;
            }
            if (!term.isBefore(first)) {
                terms.add(term);
            }
        }
    }

    /** True if the date is a planned term date in a month in which the item is valid. */
    public boolean isTerm(LocalDate date) {
        return termsBetween(date, date).contains(date);
    }

    /** The term date before the given one, regardless of validity; empty for the first term. */
    public LocalDate previousTerm(LocalDate date) {
        // firstIndexFrom may lie one or two terms before the date; step up first, then back
        long k = firstIndexFrom(date) + 2;
        while (k > 0 && !scheduled(k).isBefore(date)) {
            k--;
        }
        LocalDate candidate = scheduled(k);
        return candidate.isBefore(date) ? candidate : null;
    }

    /** P5: days a booking may lie before or after the term, at most 5 and at most half the spacing. */
    public static int toleranceDays(RhythmUnit unit, int count) {
        int spacingDays = unit == RhythmUnit.WEEK ? 7 * count : 30 * count;
        return Math.min(MAX_TOLERANCE_DAYS, spacingDays / 2);
    }

    /** P5: the date itself on a working day, otherwise the next working day (weekend and nationwide holidays). */
    public static LocalDate nextWorkingDay(LocalDate date) {
        LocalDate day = date;
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY
                || isNationwideHoliday(day)) {
            day = day.plusDays(1);
        }
        return day;
    }

    /** Nationwide German holidays (decision in section 19.6); state holidays are not considered. */
    public static boolean isNationwideHoliday(LocalDate day) {
        int month = day.getMonthValue();
        int dom = day.getDayOfMonth();
        if ((month == 1 && dom == 1) || (month == 5 && dom == 1) || (month == 10 && dom == 3)
                || (month == 12 && (dom == 25 || dom == 26))) {
            return true;
        }
        LocalDate easter = easterSunday(day.getYear());
        long offset = ChronoUnit.DAYS.between(easter, day);
        // Good Friday, Easter Monday, Ascension Day, Whit Monday
        return offset == -2 || offset == 1 || offset == 39 || offset == 50;
    }

    // Anonymous Gregorian algorithm
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    private LocalDate scheduled(long k) {
        return unit == RhythmUnit.WEEK ? anchor.plusWeeks(k * count) : anchor.plusMonths(k * count);
    }

    // Index of a scheduled date at or shortly before "from"; never below 0
    private long firstIndexFrom(LocalDate from) {
        long steps = unit == RhythmUnit.WEEK
                ? ChronoUnit.WEEKS.between(anchor, from)
                : ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(from));
        return steps <= 0 ? 0 : Math.max(0, steps / count - 1);
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
