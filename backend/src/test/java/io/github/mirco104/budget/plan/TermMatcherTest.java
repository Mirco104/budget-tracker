package io.github.mirco104.budget.plan;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.plan.TermMatcher.Booking;
import io.github.mirco104.budget.plan.TermMatcher.Planned;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Rule P5 with the match of R28: which term of a planned item a booking fulfils. */
class TermMatcherTest {

    private static final long WOHNEN = 1;
    private static final long NEBENKOSTEN = 2;
    private static final long FREIZEIT = 8;

    private static final Planned MIETE = planned(10, "2026-11-01", "-900", "Miete Wohnung", WOHNEN);
    private static final Planned STROM = planned(11, "2026-11-15", "-80", "Strom Stadtwerke", NEBENKOSTEN);
    private static final Planned STREAMING = planned(12, "2026-11-05", "-9.99", "Streaming Abo", FREIZEIT);
    private static final Planned MUSIK = planned(13, "2026-11-05", "-9.99", "Musikdienst", FREIZEIT);

    @Test
    void matchesASharedWordWithinFiveDaysAndTwentyPercent() {
        // 94 is within 20 % of 80; the word "stadtwerke" is shared
        Map<Integer, Planned> matches = TermMatcher.match(
                List.of(booking(0, "2026-11-18", "-94", "STADTWERKE NORD", null)), List.of(MIETE, STROM));

        assertThat(matches).containsExactly(Map.entry(0, STROM));
    }

    @Test
    void noMatchOutsideTheLimits() {
        List<Planned> planned = List.of(STROM);

        // 6 days away
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-21", "-80", "Stadtwerke", null)), planned)).isEmpty();
        // 97 is more than 20 % above 80
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-15", "-97", "Stadtwerke", null)), planned)).isEmpty();
        // The other sign
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-15", "80", "Stadtwerke", null)), planned)).isEmpty();
        // No shared word with 4 letters or more, and not the same amount
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-15", "-81", "SWN GmbH", null)), planned)).isEmpty();
    }

    @Test
    void theSameAmountMatchesWithTheSuggestedCategoryOfTheSeries() {
        Map<Integer, Planned> matches = TermMatcher.match(
                List.of(booking(0, "2026-11-02", "-900", "Person B", WOHNEN)), List.of(MIETE));
        assertThat(matches).containsExactly(Map.entry(0, MIETE));

        // The same amount without the category is no match
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-02", "-900", "Person B", FREIZEIT)), List.of(MIETE)))
                .isEmpty();
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-02", "-900", "Person B", null)), List.of(MIETE)))
                .isEmpty();
    }

    @Test
    void twoSubscriptionsWithTheSamePriceAreNotMixedUp() {
        Map<Integer, Planned> matches = TermMatcher.match(
                List.of(booking(0, "2026-11-05", "-9.99", "MUSIKDIENST AB", FREIZEIT)), List.of(STREAMING, MUSIK));

        assertThat(matches).containsExactly(Map.entry(0, MUSIK));
    }

    @Test
    void theClosestDateThenTheClosestAmountThenTheLowerSeriesWins() {
        Planned early = planned(20, "2026-11-01", "-50", "Verein Beitrag", FREIZEIT);
        Planned late = planned(21, "2026-11-03", "-50", "Verein Beitrag", FREIZEIT);
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-03", "-50", "Verein", null)), List.of(early, late)))
                .containsExactly(Map.entry(0, late));

        Planned near = planned(22, "2026-11-03", "-52", "Verein Beitrag", FREIZEIT);
        Planned exact = planned(23, "2026-11-03", "-50", "Verein Beitrag", FREIZEIT);
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-03", "-50", "Verein", null)), List.of(near, exact)))
                .containsExactly(Map.entry(0, exact));

        Planned b = planned(31, "2026-11-03", "-50", "Verein Beitrag", FREIZEIT);
        Planned a = planned(30, "2026-11-03", "-50", "Verein Beitrag", FREIZEIT);
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-03", "-50", "Verein", null)), List.of(b, a)))
                .containsExactly(Map.entry(0, a));
    }

    @Test
    void eachOccurrenceAndEachBookingOnlyOnceInTheOrderOfTheDates() {
        Planned november = planned(10, "2026-11-01", "-900", "Miete Wohnung", WOHNEN);
        List<Booking> bookings = List.of(
                booking(0, "2026-11-04", "-900", "Miete Wohnung", null),
                booking(1, "2026-11-02", "-900", "Miete Wohnung", null));

        Map<Integer, Planned> matches = TermMatcher.match(bookings, List.of(november));

        // Booking 1 comes first by date
        assertThat(matches).containsExactly(Map.entry(1, november));
    }

    private static Planned planned(long itemId, String date, String amount, String description, long categoryId) {
        return planned(itemId, date, amount, description, categoryId, 5);
    }

    private static Planned planned(
            long itemId, String date, String amount, String description, long categoryId, int tolerance) {
        return new Planned(itemId, new UUID(0, itemId), LocalDate.parse(date), LocalDate.parse(date),
                new BigDecimal(amount), description, categoryId, tolerance);
    }

    @Test
    void aWeeklyTermAllowsThreeDaysAndUsesTheDueDate() {
        Planned weekly = planned(40, "2026-11-09", "-10", "Bäckerei Brot", FREIZEIT, 3);
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-12", "-10", "Bäckerei", null)), List.of(weekly)))
                .containsExactly(Map.entry(0, weekly));
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-13", "-10", "Bäckerei", null)), List.of(weekly)))
                .isEmpty();

        // A term on Saturday is due on Monday; the distance counts from Monday
        Planned shifted = new Planned(41, new UUID(0, 41), LocalDate.parse("2026-11-14"),
                LocalDate.parse("2026-11-16"), new BigDecimal("-900"), "Miete Wohnung", WOHNEN, 5);
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-21", "-900", "Miete", null)), List.of(shifted)))
                .containsExactly(Map.entry(0, shifted));
    }

    private static Booking booking(int index, String date, String amount, String text, Long suggestedCategoryId) {
        return new Booking(index, LocalDate.parse(date), new BigDecimal(amount), text, suggestedCategoryId);
    }
}
