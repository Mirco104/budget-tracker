package io.github.mirco104.budget.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Rule R12 from docs/spezifikation.md, section 2, and the test cases from section 7.
 */
class RecurrenceScheduleTest {

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    private static RecurrenceSchedule schedule(String start, String end, RecurringInterval interval,
            String... exceptions) {
        return new RecurrenceSchedule(
                date(start),
                end == null ? null : date(end),
                interval,
                Arrays.stream(exceptions).map(LocalDate::parse).collect(Collectors.toSet()));
    }

    // --- occurrencesBetween: intervals ---

    @Test
    void monthlyOccursEveryMonthOnTheDayOfTheStartDate() {
        RecurrenceSchedule monthly = schedule("2026-01-05", null, RecurringInterval.MONTHLY);

        assertThat(monthly.occurrencesBetween(date("2026-01-01"), date("2026-04-30")))
                .containsExactly(date("2026-01-05"), date("2026-02-05"), date("2026-03-05"), date("2026-04-05"));
    }

    @Test
    void quarterlyOccursEveryThreeMonths() {
        RecurrenceSchedule quarterly = schedule("2026-02-10", null, RecurringInterval.QUARTERLY);

        assertThat(quarterly.occurrencesBetween(date("2026-01-01"), date("2026-12-31")))
                .containsExactly(date("2026-02-10"), date("2026-05-10"), date("2026-08-10"), date("2026-11-10"));
    }

    @Test
    void halfYearlyOccursEverySixMonths() {
        RecurrenceSchedule halfYearly = schedule("2026-03-01", null, RecurringInterval.HALF_YEARLY);

        assertThat(halfYearly.occurrencesBetween(date("2026-01-01"), date("2027-12-31")))
                .containsExactly(date("2026-03-01"), date("2026-09-01"), date("2027-03-01"), date("2027-09-01"));
    }

    @Test
    void yearlyOccursOncePerYear() {
        RecurrenceSchedule yearly = schedule("2026-10-15", null, RecurringInterval.YEARLY);

        assertThat(yearly.occurrencesBetween(date("2026-10-01"), date("2026-10-31")))
                .containsExactly(date("2026-10-15"));
        assertThat(yearly.occurrencesBetween(date("2026-11-01"), date("2027-09-30"))).isEmpty();
        assertThat(yearly.occurrencesBetween(date("2027-10-01"), date("2027-10-31")))
                .containsExactly(date("2027-10-15"));
    }

    // --- occurrencesBetween: day of month ---

    @Test
    void day31FallsOnTheLastDayOfShorterMonthsAndComesBackAfterwards() {
        RecurrenceSchedule monthly = schedule("2026-01-31", null, RecurringInterval.MONTHLY);

        assertThat(monthly.occurrencesBetween(date("2026-01-01"), date("2026-05-31")))
                .containsExactly(date("2026-01-31"), date("2026-02-28"), date("2026-03-31"), date("2026-04-30"),
                        date("2026-05-31"));
    }

    @Test
    void day31FallsOnFebruary29InLeapYears() {
        RecurrenceSchedule monthly = schedule("2028-01-31", null, RecurringInterval.MONTHLY);

        assertThat(monthly.occurrencesBetween(date("2028-02-01"), date("2028-02-29")))
                .containsExactly(date("2028-02-29"));
    }

    @Test
    void february29FallsOnFebruary28InNonLeapYears() {
        RecurrenceSchedule yearly = schedule("2028-02-29", null, RecurringInterval.YEARLY);

        assertThat(yearly.occurrencesBetween(date("2028-01-01"), date("2032-12-31")))
                .containsExactly(date("2028-02-29"), date("2029-02-28"), date("2030-02-28"), date("2031-02-28"),
                        date("2032-02-29"));
    }

    // --- occurrencesBetween: start, end, exceptions ---

    @Test
    void noOccurrenceBeforeTheStartDate() {
        RecurrenceSchedule monthly = schedule("2026-10-20", null, RecurringInterval.MONTHLY);

        assertThat(monthly.occurrencesBetween(date("2026-01-01"), date("2026-09-30"))).isEmpty();
        assertThat(monthly.occurrencesBetween(date("2026-10-01"), date("2026-10-19"))).isEmpty();
        assertThat(monthly.occurrencesBetween(date("2026-10-01"), date("2026-10-31")))
                .containsExactly(date("2026-10-20"));
    }

    @Test
    void endDateIsInclusive() {
        RecurrenceSchedule monthly = schedule("2026-01-05", "2026-03-05", RecurringInterval.MONTHLY);

        assertThat(monthly.occurrencesBetween(date("2026-01-01"), date("2026-12-31")))
                .containsExactly(date("2026-01-05"), date("2026-02-05"), date("2026-03-05"));
    }

    @Test
    void noOccurrenceAfterTheEndDate() {
        RecurrenceSchedule monthly = schedule("2026-01-05", "2026-03-04", RecurringInterval.MONTHLY);

        assertThat(monthly.occurrencesBetween(date("2026-01-01"), date("2026-12-31")))
                .containsExactly(date("2026-01-05"), date("2026-02-05"));
    }

    @Test
    void exceptionsAreSkipped() {
        RecurrenceSchedule monthly = schedule("2026-01-05", null, RecurringInterval.MONTHLY, "2026-10-05");

        assertThat(monthly.occurrencesBetween(date("2026-09-01"), date("2026-11-30")))
                .containsExactly(date("2026-09-05"), date("2026-11-05"));
    }

    @Test
    void rangeBoundsAreInclusive() {
        RecurrenceSchedule monthly = schedule("2026-01-01", null, RecurringInterval.MONTHLY);

        assertThat(monthly.occurrencesBetween(date("2026-03-01"), date("2026-04-01")))
                .containsExactly(date("2026-03-01"), date("2026-04-01"));
    }

    // --- isOccurrence ---

    @Test
    void isOccurrenceForScheduledDates() {
        RecurrenceSchedule quarterly = schedule("2026-01-31", "2026-12-31", RecurringInterval.QUARTERLY,
                "2026-07-31");

        assertThat(quarterly.isOccurrence(date("2026-01-31"))).isTrue();
        assertThat(quarterly.isOccurrence(date("2026-04-30"))).isTrue();
        assertThat(quarterly.isOccurrence(date("2026-10-31"))).isTrue();
    }

    @Test
    void isNoOccurrenceOffTheScheduleOrExcludedOrOutsideTheSeries() {
        RecurrenceSchedule quarterly = schedule("2026-01-31", "2026-12-31", RecurringInterval.QUARTERLY,
                "2026-07-31");

        assertThat(quarterly.isOccurrence(date("2026-04-29"))).as("wrong day").isFalse();
        assertThat(quarterly.isOccurrence(date("2026-02-28"))).as("wrong month").isFalse();
        assertThat(quarterly.isOccurrence(date("2026-07-31"))).as("exception").isFalse();
        assertThat(quarterly.isOccurrence(date("2025-10-31"))).as("before start").isFalse();
        assertThat(quarterly.isOccurrence(date("2027-01-31"))).as("after end").isFalse();
    }

    // --- previousOccurrence ---

    @Test
    void previousOccurrenceIsTheScheduledDateBefore() {
        RecurrenceSchedule monthly = schedule("2026-01-31", null, RecurringInterval.MONTHLY);

        assertThat(monthly.previousOccurrence(date("2026-03-31"))).contains(date("2026-02-28"));
        assertThat(monthly.previousOccurrence(date("2026-02-28"))).contains(date("2026-01-31"));
    }

    @Test
    void firstOccurrenceHasNoPrevious() {
        RecurrenceSchedule monthly = schedule("2026-01-31", null, RecurringInterval.MONTHLY);

        assertThat(monthly.previousOccurrence(date("2026-01-31"))).isEmpty();
    }

    // --- monthlyEquivalent (spec section 5, R14) ---

    @Test
    void monthlyEquivalentIsAbsoluteAmountPerMonthRoundedToCents() {
        assertThat(RecurrenceSchedule.monthlyEquivalent(new BigDecimal("-240.00"), RecurringInterval.YEARLY))
                .isEqualByComparingTo("20.00");
        assertThat(RecurrenceSchedule.monthlyEquivalent(new BigDecimal("100.00"), RecurringInterval.QUARTERLY))
                .isEqualTo(new BigDecimal("33.33"));
        assertThat(RecurrenceSchedule.monthlyEquivalent(new BigDecimal("-0.05"), RecurringInterval.HALF_YEARLY))
                .isEqualTo(new BigDecimal("0.01"));
    }
}
