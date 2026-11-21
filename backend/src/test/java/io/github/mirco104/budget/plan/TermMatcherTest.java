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

        // The same amount with another suggested category is no match
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-02", "-900", "Person B", FREIZEIT)), List.of(MIETE)))
                .isEmpty();
    }

    @Test
    void theSameAmountAloneMatchesARowWithoutSuggestion() {
        // Ticket #142: a fixed cost with a fixed amount is found without a shared word and without a suggestion
        Planned handy = planned(14, "2026-11-10", "-24.99", "Handy", NEBENKOSTEN);
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-12", "-24.99", "Telefonica Germany", null)),
                List.of(handy))).containsExactly(Map.entry(0, handy));

        // Not for another amount, and not outside the days
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-12", "-25.99", "Telefonica Germany", null)),
                List.of(handy))).isEmpty();
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-16", "-24.99", "Telefonica Germany", null)),
                List.of(handy))).isEmpty();
    }

    @Test
    void aMatchByTheSameAmountAloneComesAfterTheOtherMatches() {
        // The earlier row only has the same amount; the later one shares a word, so it gets the term
        Planned handy = planned(14, "2026-11-10", "-24.99", "Handy Vertrag", NEBENKOSTEN);
        List<Booking> bookings = List.of(
                booking(0, "2026-11-08", "-24.99", "Person C", null),
                booking(1, "2026-11-11", "-24.99", "Handy Vertrag 11/2026", null));

        assertThat(TermMatcher.match(bookings, List.of(handy))).containsExactly(Map.entry(1, handy));
    }

    @Test
    void incomeOfAMonthlyItemMayComeUpToTenDaysEarly() {
        // Ticket #142: the salary of November often comes at the end of October
        Planned gehalt = new Planned(60, new UUID(0, 60), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"),
                new BigDecimal("2500"), "Gehalt", 9L, 5, 10);
        assertThat(TermMatcher.match(List.of(booking(0, "2026-10-23", "2480", "Lohn Gehalt", null)), List.of(gehalt)))
                .containsExactly(Map.entry(0, gehalt));
        // 11 days before the due date is too early
        assertThat(TermMatcher.match(List.of(booking(0, "2026-10-22", "2480", "Lohn Gehalt", null)), List.of(gehalt)))
                .isEmpty();
        // Afterwards still 5 days
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-08", "2480", "Lohn Gehalt", null)), List.of(gehalt)))
                .isEmpty();
    }

    @Test
    void anExpenseMayNotComeEarlierThanTheTolerance() {
        Planned miete = new Planned(61, new UUID(0, 61), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"),
                new BigDecimal("-900"), "Miete Wohnung", WOHNEN, 5, TermSchedule.earlyDays(RhythmUnit.MONTH, 1, false));
        assertThat(TermMatcher.match(List.of(booking(0, "2026-10-26", "-900", "Miete", null)), List.of(miete)))
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

    @Test
    void aSavingTermMatchesTheSameAmountOnlyForASavingRow() {
        // Decision 67: a saving item has no category, so the same amount counts for a row suggested as saving
        Planned saving = new Planned(50, new UUID(0, 50), LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-03"),
                new BigDecimal("-200"), "Notgroschen", null, 5);
        Booking savingRow = new Booking(0, LocalDate.parse("2026-11-04"), new BigDecimal("-200"), "Person A", null, true);
        assertThat(TermMatcher.match(List.of(savingRow), List.of(saving))).containsExactly(Map.entry(0, saving));

        // Not for a row that is no saving, and not for another amount without a shared word
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-04", "-200", "Person A", null)), List.of(saving)))
                .isEmpty();
        Booking other = new Booking(0, LocalDate.parse("2026-11-04"), new BigDecimal("-210"), "Person A", null, true);
        assertThat(TermMatcher.match(List.of(other), List.of(saving))).isEmpty();
        // The shared word still matches
        assertThat(TermMatcher.match(List.of(booking(0, "2026-11-04", "-210", "NOTGROSCHEN", null)), List.of(saving)))
                .containsExactly(Map.entry(0, saving));
        // A saving row never matches the same amount of a term with a category
        Planned rent = planned(51, "2026-11-03", "-200", "Miete", WOHNEN);
        assertThat(TermMatcher.match(List.of(savingRow), List.of(rent))).isEmpty();
    }

    private static Booking booking(int index, String date, String amount, String text, Long suggestedCategoryId) {
        return new Booking(index, LocalDate.parse(date), new BigDecimal(amount), text, suggestedCategoryId);
    }
}
