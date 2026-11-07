package io.github.mirco104.budget.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.recurring.OccurrenceMatcher.Booking;
import io.github.mirco104.budget.recurring.OccurrenceMatcher.Planned;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rule R28: which planned occurrence of a series a new booking replaces (decision 42). */
class OccurrenceMatcherTest {

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
        Map<Integer, Planned> matches = OccurrenceMatcher.match(
                List.of(booking(0, "2026-11-18", "-94", "STADTWERKE NORD", null)), List.of(MIETE, STROM));

        assertThat(matches).containsExactly(Map.entry(0, STROM));
    }

    @Test
    void noMatchOutsideTheLimits() {
        List<Planned> planned = List.of(STROM);

        // 6 days away
        assertThat(OccurrenceMatcher.match(List.of(booking(0, "2026-11-21", "-80", "Stadtwerke", null)), planned)).isEmpty();
        // 97 is more than 20 % above 80
        assertThat(OccurrenceMatcher.match(List.of(booking(0, "2026-11-15", "-97", "Stadtwerke", null)), planned)).isEmpty();
        // The other sign
        assertThat(OccurrenceMatcher.match(List.of(booking(0, "2026-11-15", "80", "Stadtwerke", null)), planned)).isEmpty();
        // No shared word with 4 letters or more, and not the same amount
        assertThat(OccurrenceMatcher.match(List.of(booking(0, "2026-11-15", "-81", "SWN GmbH", null)), planned)).isEmpty();
    }

    @Test
    void theSameAmountMatchesWithTheSuggestedCategoryOfTheSeries() {
        Map<Integer, Planned> matches = OccurrenceMatcher.match(
                List.of(booking(0, "2026-11-02", "-900", "Person B", WOHNEN)), List.of(MIETE));
        assertThat(matches).containsExactly(Map.entry(0, MIETE));

        // The same amount without the category is no match
        assertThat(OccurrenceMatcher.match(List.of(booking(0, "2026-11-02", "-900", "Person B", FREIZEIT)), List.of(MIETE)))
                .isEmpty();
        assertThat(OccurrenceMatcher.match(List.of(booking(0, "2026-11-02", "-900", "Person B", null)), List.of(MIETE)))
                .isEmpty();
    }

    @Test
    void twoSubscriptionsWithTheSamePriceAreNotMixedUp() {
        Map<Integer, Planned> matches = OccurrenceMatcher.match(
                List.of(booking(0, "2026-11-05", "-9.99", "MUSIKDIENST AB", FREIZEIT)), List.of(STREAMING, MUSIK));

        assertThat(matches).containsExactly(Map.entry(0, MUSIK));
    }

    @Test
    void theClosestDateThenTheClosestAmountThenTheLowerSeriesWins() {
        Planned early = planned(20, "2026-11-01", "-50", "Verein Beitrag", FREIZEIT);
        Planned late = planned(21, "2026-11-03", "-50", "Verein Beitrag", FREIZEIT);
        assertThat(OccurrenceMatcher.match(List.of(booking(0, "2026-11-03", "-50", "Verein", null)), List.of(early, late)))
                .containsExactly(Map.entry(0, late));

        Planned near = planned(22, "2026-11-03", "-52", "Verein Beitrag", FREIZEIT);
        Planned exact = planned(23, "2026-11-03", "-50", "Verein Beitrag", FREIZEIT);
        assertThat(OccurrenceMatcher.match(List.of(booking(0, "2026-11-03", "-50", "Verein", null)), List.of(near, exact)))
                .containsExactly(Map.entry(0, exact));

        Planned b = planned(31, "2026-11-03", "-50", "Verein Beitrag", FREIZEIT);
        Planned a = planned(30, "2026-11-03", "-50", "Verein Beitrag", FREIZEIT);
        assertThat(OccurrenceMatcher.match(List.of(booking(0, "2026-11-03", "-50", "Verein", null)), List.of(b, a)))
                .containsExactly(Map.entry(0, a));
    }

    @Test
    void eachOccurrenceAndEachBookingOnlyOnceInTheOrderOfTheDates() {
        Planned november = planned(10, "2026-11-01", "-900", "Miete Wohnung", WOHNEN);
        List<Booking> bookings = List.of(
                booking(0, "2026-11-04", "-900", "Miete Wohnung", null),
                booking(1, "2026-11-02", "-900", "Miete Wohnung", null));

        Map<Integer, Planned> matches = OccurrenceMatcher.match(bookings, List.of(november));

        // Booking 1 comes first by date
        assertThat(matches).containsExactly(Map.entry(1, november));
    }

    private static Planned planned(long recurringId, String date, String amount, String description, long categoryId) {
        return new Planned(recurringId, LocalDate.parse(date), new BigDecimal(amount), description, categoryId);
    }

    private static Booking booking(int index, String date, String amount, String text, Long suggestedCategoryId) {
        return new Booking(index, LocalDate.parse(date), new BigDecimal(amount), text, suggestedCategoryId);
    }
}
