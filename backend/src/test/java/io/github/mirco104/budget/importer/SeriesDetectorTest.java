package io.github.mirco104.budget.importer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.importer.SeriesDetector.Row;
import io.github.mirco104.budget.importer.SeriesDetector.Suggestion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rule R31: monthly series among the new rows of an import, also when a payment moves a few days. */
class SeriesDetectorTest {

    @Test
    void findsAMonthlySalaryThatMovesAroundTheFirst() {
        // Due on the 1st; moved to the last working day before or the Monday after a weekend
        List<Row> rows = List.of(
                row(0, "2026-08-01", "2500", "firma beispiel", "Firma Beispiel GmbH · Lohn"),
                row(1, "2026-08-31", "2500", "firma beispiel", "Firma Beispiel GmbH · Lohn"),
                row(2, "2026-10-01", "2500", "firma beispiel", "Firma Beispiel GmbH · Lohn"),
                row(3, "2026-11-02", "2550", "firma beispiel", "Firma Beispiel GmbH · Lohn Nov"));

        List<Suggestion> suggestions = SeriesDetector.detect(rows);

        assertThat(suggestions).hasSize(1);
        Suggestion salary = suggestions.getFirst();
        assertThat(salary.indices()).containsExactly(0, 1, 2, 3);
        assertThat(salary.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        // Issue #119: the median amount, the smallest and the largest amount, the description of the latest row
        assertThat(salary.amount()).isEqualByComparingTo("2500");
        assertThat(salary.lowest()).isEqualByComparingTo("2500");
        assertThat(salary.highest()).isEqualByComparingTo("2550");
        assertThat(salary.description()).isEqualTo("Firma Beispiel GmbH · Lohn Nov");
    }

    // Issue #119: a salary that varies a little; a higher latest row does not raise the planned amount
    @Test
    void theAmountIsTheMedianOfTheRows() {
        List<Row> rows = List.of(
                row(0, "2026-08-01", "2410", "firma beispiel", "Firma Beispiel GmbH · Lohn"),
                row(1, "2026-09-01", "2480", "firma beispiel", "Firma Beispiel GmbH · Lohn"),
                row(2, "2026-10-01", "2450", "firma beispiel", "Firma Beispiel GmbH · Lohn"),
                row(3, "2026-11-02", "2530", "firma beispiel", "Firma Beispiel GmbH · Lohn"));

        Suggestion salary = SeriesDetector.detect(rows).getFirst();

        // An even number of rows: the mean of the two middle amounts, 2450 and 2480
        assertThat(salary.amount()).isEqualByComparingTo("2465.00");
        assertThat(salary.lowest()).isEqualByComparingTo("2410");
        assertThat(salary.highest()).isEqualByComparingTo("2530");
    }

    // Issue #119: for an expense lowest and highest go by the size of the amount
    @Test
    void anExpenseHasItsMedianAndItsRangeBySize() {
        List<Row> rows = List.of(
                row(0, "2026-08-15", "-60.15", "stadtwerke", "Stadtwerke"),
                row(1, "2026-09-15", "-55.00", "stadtwerke", "Stadtwerke"),
                row(2, "2026-10-15", "-58.33", "stadtwerke", "Stadtwerke"));

        Suggestion power = SeriesDetector.detect(rows).getFirst();

        assertThat(power.amount()).isEqualByComparingTo("-58.33");
        assertThat(power.lowest()).isEqualByComparingTo("-55.00");
        assertThat(power.highest()).isEqualByComparingTo("-60.15");
    }

    @Test
    void theMeanOfTheTwoMiddleAmountsIsRoundedHalfUp() {
        List<Row> rows = List.of(
                row(0, "2026-08-15", "-10.00", "verein", "Verein"),
                row(1, "2026-09-15", "-10.01", "verein", "Verein"),
                row(2, "2026-10-15", "-10.00", "verein", "Verein"),
                row(3, "2026-11-15", "-10.01", "verein", "Verein"));

        // -10.005 rounds away from zero
        assertThat(SeriesDetector.detect(rows).getFirst().amount()).isEqualByComparingTo("-10.01");
    }

    // Code review 08.11.2026: a series created with the import replaces the nearest free occurrence per row
    @Test
    void eachRowReplacesTheNearestFreeOccurrence() {
        List<LocalDate> dates = SeriesDetector.occurrenceDates(LocalDate.of(2026, 8, 1), List.of(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 10, 1)));

        // In the order of the rows
        assertThat(dates).containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1));
    }

    @Test
    void aRowWithoutAFreeOccurrenceWithinFiveDaysHasNone() {
        List<LocalDate> dates = SeriesDetector.occurrenceDates(LocalDate.of(2026, 8, 15), List.of(
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 5)));

        // The second row of August finds the occurrence taken; September 1st and August 5th are too far away
        assertThat(dates).containsExactly(LocalDate.of(2026, 8, 15), null, null, null);
    }

    @Test
    void needsThreeMonthsInARow() {
        assertThat(SeriesDetector.detect(List.of(
                row(0, "2026-01-05", "-50", "verein sport", "Verein Sport"),
                row(1, "2026-02-05", "-50", "verein sport", "Verein Sport")))).isEmpty();

        // January, February, then a gap: the run from April to June counts
        List<Suggestion> suggestions = SeriesDetector.detect(List.of(
                row(0, "2026-01-05", "-50", "verein sport", "Verein Sport"),
                row(1, "2026-02-05", "-50", "verein sport", "Verein Sport"),
                row(2, "2026-04-06", "-50", "verein sport", "Verein Sport"),
                row(3, "2026-05-05", "-50", "verein sport", "Verein Sport"),
                row(4, "2026-06-04", "-50", "verein sport", "Verein Sport")));
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().indices()).containsExactly(2, 3, 4);
        assertThat(suggestions.getFirst().startDate()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    void leavesOutAmountsMoreThanTwentyPercentAway() {
        List<Suggestion> suggestions = SeriesDetector.detect(List.of(
                row(0, "2026-01-01", "-900", "person vermieter", "Miete"),
                row(1, "2026-02-01", "-900", "person vermieter", "Miete"),
                row(2, "2026-02-15", "-2000", "person vermieter", "Kaution"),
                row(3, "2026-03-01", "-900", "person vermieter", "Miete")));

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().indices()).containsExactly(0, 1, 3);
    }

    @Test
    void severalBookingsInAMonthAreNoMonthlySeries() {
        List<Row> rows = new ArrayList<>();
        int index = 0;
        for (String month : List.of("2026-01", "2026-02", "2026-03")) {
            for (String day : List.of("03", "10", "17", "24")) {
                rows.add(row(index++, month + "-" + day, "-4.20", "bäckerei ecke", "Bäckerei Ecke"));
            }
        }

        assertThat(SeriesDetector.detect(rows)).isEmpty();
    }

    @Test
    void keysAndSignsAreSeparate() {
        List<Suggestion> suggestions = SeriesDetector.detect(List.of(
                row(0, "2026-01-10", "-20", "kino nord", "Kino"),
                row(1, "2026-02-10", "20", "kino nord", "Kino Erstattung"),
                row(2, "2026-03-10", "-20", "kino nord", "Kino"),
                row(3, "2026-01-10", "-20", "theater", "Theater"),
                row(4, "2026-02-10", "-20", "theater", "Theater"),
                row(5, "2026-03-10", "-20", "theater", "Theater"),
                row(6, "2026-04-10", "-20", "", "ohne Text")));

        assertThat(suggestions).extracting(Suggestion::description).containsExactly("Theater");
    }

    @Test
    void theEndOfTheMonthStaysTheEndOfTheMonth() {
        List<Suggestion> suggestions = SeriesDetector.detect(List.of(
                row(0, "2026-01-31", "-30", "handy vertrag", "Handy"),
                row(1, "2026-02-28", "-30", "handy vertrag", "Handy"),
                row(2, "2026-03-31", "-30", "handy vertrag", "Handy")));

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().startDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(suggestions.getFirst().indices()).containsExactly(0, 1, 2);
    }

    private static Row row(int index, String date, String amount, String key, String description) {
        return new Row(index, LocalDate.parse(date), new BigDecimal(amount), key, description);
    }
}
