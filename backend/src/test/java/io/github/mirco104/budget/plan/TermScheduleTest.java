package io.github.mirco104.budget.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Rule P5: terms of a planned item with a week or month rhythm, next working day and date tolerance. */
class TermScheduleTest {

    private static LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    @Test
    void monthlyTermsReturnToTheAnchorDayAfterAShortMonth() {
        TermSchedule schedule = new TermSchedule(RhythmUnit.MONTH, 1, d("2026-01-31"), YearMonth.of(2026, 1), null);

        assertThat(schedule.termsBetween(d("2026-01-01"), d("2026-04-30")))
                .containsExactly(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30"));
    }

    @Test
    void yearlyTermLiesOnlyInItsDueMonth() {
        TermSchedule schedule = new TermSchedule(RhythmUnit.MONTH, 12, d("2026-03-15"), YearMonth.of(2026, 1), null);

        assertThat(schedule.termsBetween(d("2026-01-01"), d("2027-12-31")))
                .containsExactly(d("2026-03-15"), d("2027-03-15"));
    }

    @Test
    void weeklyAndBiweeklyTerms() {
        TermSchedule weekly = new TermSchedule(RhythmUnit.WEEK, 1, d("2026-11-02"), YearMonth.of(2026, 11), null);
        TermSchedule biweekly = new TermSchedule(RhythmUnit.WEEK, 2, d("2026-11-02"), YearMonth.of(2026, 11), null);

        assertThat(weekly.termsBetween(d("2026-11-01"), d("2026-11-30")))
                .containsExactly(d("2026-11-02"), d("2026-11-09"), d("2026-11-16"), d("2026-11-23"), d("2026-11-30"));
        assertThat(biweekly.termsBetween(d("2026-11-10"), d("2026-12-31")))
                .containsExactly(d("2026-11-16"), d("2026-11-30"), d("2026-12-14"), d("2026-12-28"));
    }

    @Test
    void termsOnlyInMonthsTheItemIsValid() {
        // Anchor before validFrom: the version applies from March to May only
        TermSchedule schedule = new TermSchedule(
                RhythmUnit.MONTH, 1, d("2026-01-01"), YearMonth.of(2026, 3), YearMonth.of(2026, 5));

        assertThat(schedule.termsBetween(d("2026-01-01"), d("2026-12-31")))
                .containsExactly(d("2026-03-01"), d("2026-04-01"), d("2026-05-01"));
    }

    @Test
    void noTermsBeforeTheAnchor() {
        TermSchedule schedule = new TermSchedule(RhythmUnit.MONTH, 1, d("2026-06-10"), YearMonth.of(2026, 1), null);

        assertThat(schedule.termsBetween(d("2026-01-01"), d("2026-06-30"))).containsExactly(d("2026-06-10"));
        assertThat(schedule.isTerm(d("2026-05-10"))).isFalse();
        assertThat(schedule.isTerm(d("2026-07-10"))).isTrue();
        assertThat(schedule.isTerm(d("2026-07-11"))).isFalse();
    }

    @Test
    void nextWorkingDaySkipsWeekendsAndNationwideHolidays() {
        assertThat(TermSchedule.nextWorkingDay(d("2026-11-14"))).isEqualTo(d("2026-11-16")); // Saturday
        assertThat(TermSchedule.nextWorkingDay(d("2026-11-16"))).isEqualTo(d("2026-11-16"));
        assertThat(TermSchedule.nextWorkingDay(d("2026-12-25"))).isEqualTo(d("2026-12-28")); // Christmas, weekend
        assertThat(TermSchedule.nextWorkingDay(d("2027-01-01"))).isEqualTo(d("2027-01-04"));
        // Easter 2026 is 5 April: Good Friday 3 April, Easter Monday 6 April, Ascension 14 May, Whit Monday 25 May
        assertThat(TermSchedule.nextWorkingDay(d("2026-04-03"))).isEqualTo(d("2026-04-07"));
        assertThat(TermSchedule.nextWorkingDay(d("2026-05-14"))).isEqualTo(d("2026-05-15"));
        assertThat(TermSchedule.nextWorkingDay(d("2026-05-25"))).isEqualTo(d("2026-05-26"));
        assertThat(TermSchedule.nextWorkingDay(d("2026-10-03"))).isEqualTo(d("2026-10-05"));
        assertThat(TermSchedule.nextWorkingDay(d("2026-05-01"))).isEqualTo(d("2026-05-04"));
    }

    @Test
    void toleranceIsAtMostFiveDaysAndHalfTheSpacing() {
        assertThat(TermSchedule.toleranceDays(RhythmUnit.WEEK, 1)).isEqualTo(3);
        assertThat(TermSchedule.toleranceDays(RhythmUnit.WEEK, 2)).isEqualTo(5);
        assertThat(TermSchedule.toleranceDays(RhythmUnit.MONTH, 1)).isEqualTo(5);
        assertThat(TermSchedule.toleranceDays(RhythmUnit.MONTH, 12)).isEqualTo(5);
    }

    @Test
    void previousTermIsTheLatestBeforeTheDate() {
        TermSchedule halfYearly = new TermSchedule(RhythmUnit.MONTH, 6, d("2025-04-01"), YearMonth.of(2025, 4), null);
        TermSchedule yearly = new TermSchedule(RhythmUnit.MONTH, 12, d("2025-03-15"), YearMonth.of(2025, 1), null);

        assertThat(halfYearly.previousTerm(d("2026-11-01"))).isEqualTo(d("2026-10-01"));
        assertThat(halfYearly.previousTerm(d("2026-10-01"))).isEqualTo(d("2026-04-01"));
        assertThat(yearly.previousTerm(d("2026-11-01"))).isEqualTo(d("2026-03-15"));
        assertThat(yearly.previousTerm(d("2025-03-15"))).isNull();
    }
}
