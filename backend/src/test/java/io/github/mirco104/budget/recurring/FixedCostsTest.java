package io.github.mirco104.budget.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.recurring.FixedCosts.Item;
import io.github.mirco104.budget.recurring.FixedCosts.Result;
import io.github.mirco104.budget.recurring.FixedCosts.SeriesData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rule R25: fixed costs per category in a month, the monthly share of the recurring expenses (decision 36). */
class FixedCostsTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final long WOHNEN = 1;
    private static final long NEBENKOSTEN = 2;
    private static final long VERSICHERUNGEN = 3;

    @Test
    void sumsTheMonthlySharesPerCategory() {
        List<SeriesData> series = List.of(
                series(10, NEBENKOSTEN, "Strom & Internet", "-165", 1, "2026-01-01", null),
                series(11, NEBENKOSTEN, "Rundfunkbeitrag", "-55.08", 3, "2026-01-15", null),
                series(12, VERSICHERUNGEN, "Kfz-Versicherung", "-480", 12, "2026-03-15", null),
                series(13, VERSICHERUNGEN, "Haftpflicht", "-60", 6, "2026-02-01", null));

        Map<Long, Result> result = FixedCosts.of(series, OCTOBER);

        assertThat(result.get(NEBENKOSTEN).total()).isEqualByComparingTo("183.36");
        assertThat(result.get(NEBENKOSTEN).items()).containsExactly(
                new Item(10, "Strom & Internet", new BigDecimal("165.00"), 1, new BigDecimal("165.00")),
                new Item(11, "Rundfunkbeitrag", new BigDecimal("55.08"), 3, new BigDecimal("18.36")));
        // 480 / 12 + 60 / 6
        assertThat(result.get(VERSICHERUNGEN).total()).isEqualByComparingTo("50.00");
        assertThat(result.get(VERSICHERUNGEN).total().scale()).isEqualTo(2);
    }

    @Test
    void roundsOnlyTheTotal() {
        // 100 / 3 = 33.333… twice: rounded items would give 66.66, the total is 66.67
        List<SeriesData> series = List.of(
                series(10, WOHNEN, "A", "-100", 3, "2026-01-01", null),
                series(11, WOHNEN, "B", "-100", 3, "2026-02-01", null));

        Result result = FixedCosts.of(series, OCTOBER).get(WOHNEN);

        assertThat(result.total()).isEqualByComparingTo("66.67");
        assertThat(result.items()).extracting(Item::monthly)
                .containsExactly(new BigDecimal("33.33"), new BigDecimal("33.33"));
    }

    @Test
    void countsOnlySeriesRunningOnTheLastDayOfTheMonth() {
        List<SeriesData> series = List.of(
                // Starts on the last day: counts
                series(10, WOHNEN, "Miete neu", "-950", 1, "2026-10-31", null),
                // Starts next month: does not count
                series(11, WOHNEN, "Garage", "-50", 1, "2026-11-01", null),
                // Ends before the last day: does not count
                series(12, WOHNEN, "Stellplatz", "-30", 1, "2025-01-01", "2026-10-30"),
                // Ends on the last day (inclusive): counts
                series(13, WOHNEN, "Keller", "-20", 1, "2025-01-01", "2026-10-31"));

        Result result = FixedCosts.of(series, OCTOBER).get(WOHNEN);

        assertThat(result.total()).isEqualByComparingTo("970.00");
        assertThat(result.items()).extracting(Item::recurringId).containsExactly(10L, 13L);
    }

    @Test
    void aSplitSeriesCountsOnlyWithItsNewPart() {
        // R13 "Dieser und alle folgenden" from 2026-10-15: the old part ends the day before
        List<SeriesData> series = List.of(
                series(10, WOHNEN, "Miete", "-900", 1, "2026-01-01", "2026-10-14"),
                series(11, WOHNEN, "Miete", "-950", 1, "2026-10-15", null));

        Result result = FixedCosts.of(series, OCTOBER).get(WOHNEN);

        assertThat(result.total()).isEqualByComparingTo("950.00");
        assertThat(result.items()).extracting(Item::recurringId).containsExactly(11L);
    }

    @Test
    void ignoresIncomeSeriesAndCategoriesWithoutSeries() {
        List<SeriesData> series = List.of(
                series(10, 20, "Gehalt", "3000", 1, "2026-01-01", null),
                series(11, WOHNEN, "Miete", "-900", 1, "2026-01-01", null));

        Map<Long, Result> result = FixedCosts.of(series, OCTOBER);

        assertThat(result).containsOnlyKeys(WOHNEN);
        assertThat(FixedCosts.of(List.of(), OCTOBER)).isEmpty();
    }

    // Fixture F1 (spec section 12): Wohnen 900.00, Versicherungen 240.00 / 12, Freizeit 15.00, salary does not count
    @Test
    void fixtureF1() {
        long freizeit = 4;
        List<SeriesData> series = List.of(
                series(1, 20, "Gehalt", "3000", 1, "2026-01-01", null),
                series(2, WOHNEN, "Miete", "-900", 1, "2026-01-01", null),
                series(3, VERSICHERUNGEN, "Versicherung", "-240", 12, "2026-10-15", null),
                series(4, freizeit, "Streaming", "-15", 1, "2026-01-05", null));

        Map<Long, Result> result = FixedCosts.of(series, OCTOBER);

        assertThat(result).containsOnlyKeys(WOHNEN, VERSICHERUNGEN, freizeit);
        assertThat(result.get(WOHNEN).total()).isEqualByComparingTo("900.00");
        assertThat(result.get(VERSICHERUNGEN).total()).isEqualByComparingTo("20.00");
        assertThat(result.get(freizeit).total()).isEqualByComparingTo("15.00");
    }

    private static SeriesData series(
            long id, long categoryId, String description, String amount, int intervalMonths, String start, String end) {
        return new SeriesData(id, categoryId, description, new BigDecimal(amount), intervalMonths,
                LocalDate.parse(start), end == null ? null : LocalDate.parse(end));
    }
}
