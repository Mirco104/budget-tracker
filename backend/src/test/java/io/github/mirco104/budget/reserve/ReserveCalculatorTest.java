package io.github.mirco104.budget.reserve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.reserve.ReserveCalculator.SeriesData;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastMonth;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastOccurrence;
import io.github.mirco104.budget.reserve.ReserveResponse.SeriesLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Rule R14 from docs/spezifikation.md, section 2, the test cases from section 7 and fixture F1 from section 12.
 */
class ReserveCalculatorTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private final ReserveCalculator calculator = new ReserveCalculator();

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static SeriesData series(long id, String description, String amount, RecurringInterval interval,
            String start, String end, String... exceptions) {
        return new SeriesData(
                id,
                description,
                "Kategorie " + id,
                money(amount),
                interval,
                LocalDate.parse(start),
                end == null ? null : LocalDate.parse(end),
                Arrays.stream(exceptions).map(LocalDate::parse).collect(Collectors.toSet()));
    }

    private static List<String> totals(ReserveResponse response) {
        return response.forecast().stream().map(month -> month.total().toPlainString()).toList();
    }

    // --- average monthly costs and reserve ---

    @Test
    void averageMonthlyCostsSpreadEveryIntervalOverItsMonths() {
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "Miete", "-900.00", RecurringInterval.MONTHLY, "2026-01-01", null),
                series(2, "Kfz", "-90.00", RecurringInterval.QUARTERLY, "2026-02-01", null),
                series(3, "Wartung", "-120.00", RecurringInterval.HALF_YEARLY, "2026-03-01", null),
                series(4, "Versicherung", "-240.00", RecurringInterval.YEARLY, "2026-10-15", null)), OCTOBER);

        assertThat(response.averageMonthlyCosts()).isEqualByComparingTo("970.00");
    }

    @Test
    void reserveCountsOnlySeriesWithAnIntervalLongerThanOneMonth() {
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "Miete", "-900.00", RecurringInterval.MONTHLY, "2026-01-01", null),
                series(2, "Kfz", "-90.00", RecurringInterval.QUARTERLY, "2026-02-01", null),
                series(4, "Versicherung", "-240.00", RecurringInterval.YEARLY, "2026-10-15", null)), OCTOBER);

        assertThat(response.reserve()).isEqualByComparingTo("50.00");
    }

    @Test
    void roundsOnlyTheSumNotEachSeries() {
        // 100/12 = 8.333… twice: rounded per series 16.66, rounded at the end 16.67
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "A", "-100.00", RecurringInterval.YEARLY, "2026-01-01", null),
                series(2, "B", "-100.00", RecurringInterval.YEARLY, "2026-02-01", null)), OCTOBER);

        assertThat(response.averageMonthlyCosts()).isEqualByComparingTo("16.67");
        assertThat(response.reserve()).isEqualByComparingTo("16.67");
        assertThat(response.series()).extracting(SeriesLine::monthlyEquivalent)
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("8.33"));
    }

    @Test
    void incomeSeriesDoNotCount() {
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "Gehalt", "3000.00", RecurringInterval.MONTHLY, "2026-01-01", null),
                series(2, "Bonus", "1200.00", RecurringInterval.YEARLY, "2026-10-20", null)), OCTOBER);

        assertThat(response.averageMonthlyCosts()).isEqualByComparingTo("0.00");
        assertThat(response.reserve()).isEqualByComparingTo("0.00");
        assertThat(response.series()).isEmpty();
        assertThat(response.forecast()).allSatisfy(month -> assertThat(month.occurrences()).isEmpty());
    }

    @Test
    void seriesInactiveInTheMonthDoNotCountTowardsTheAverage() {
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "Ab November", "-50.00", RecurringInterval.MONTHLY, "2026-11-01", null),
                series(2, "Bis September", "-60.00", RecurringInterval.MONTHLY, "2026-01-01", "2026-09-30"),
                series(3, "Bis Oktober", "-70.00", RecurringInterval.MONTHLY, "2026-01-31", "2026-10-01")), OCTOBER);

        assertThat(response.averageMonthlyCosts()).isEqualByComparingTo("70.00");
        assertThat(response.series()).extracting(SeriesLine::id).containsExactly(3L);
    }

    @Test
    void seriesStartingOnTheLastDayOfTheMonthIsActive() {
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "Ab 31.10.", "-30.00", RecurringInterval.MONTHLY, "2026-10-31", null)), OCTOBER);

        assertThat(response.averageMonthlyCosts()).isEqualByComparingTo("30.00");
    }

    // --- forecast ---

    @Test
    void forecastCoversTheMonthAndTheFollowingElevenMonths() {
        ReserveResponse response = calculator.calculate(List.of(), OCTOBER);

        assertThat(response.forecast()).extracting(ForecastMonth::month).containsExactly(
                "2026-10", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03",
                "2027-04", "2027-05", "2027-06", "2027-07", "2027-08", "2027-09");
        assertThat(response.forecast()).allSatisfy(month -> assertThat(month.total()).isEqualByComparingTo("0.00"));
    }

    @Test
    void forecastIncludesSeriesStartingLaterAndStopsAtTheEndDate() {
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "Ab Dezember", "-50.00", RecurringInterval.MONTHLY, "2026-12-10", "2027-02-10")), OCTOBER);

        assertThat(totals(response)).containsExactly(
                "0.00", "0.00", "50.00", "50.00", "50.00", "0.00",
                "0.00", "0.00", "0.00", "0.00", "0.00", "0.00");
        // Not active in October, so it does not count towards the average yet
        assertThat(response.averageMonthlyCosts()).isEqualByComparingTo("0.00");
    }

    @Test
    void forecastLeavesOutSuspendedOccurrences() {
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "Fitness", "-15.00", RecurringInterval.MONTHLY, "2026-01-05", null, "2026-11-05")), OCTOBER);

        assertThat(totals(response).subList(0, 3)).containsExactly("15.00", "0.00", "15.00");
    }

    @Test
    void forecastListsOccurrencesWithPositiveAmountsSortedByDate() {
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "Spät", "-20.00", RecurringInterval.MONTHLY, "2026-01-25", null),
                series(2, "Früh", "-10.00", RecurringInterval.MONTHLY, "2026-01-03", null)), OCTOBER);

        assertThat(response.forecast().get(0).occurrences()).containsExactly(
                new ForecastOccurrence(2, "Früh", LocalDate.parse("2026-10-03"), money("10.00")),
                new ForecastOccurrence(1, "Spät", LocalDate.parse("2026-10-25"), money("20.00")));
    }

    // --- fixture F1 (spec section 12) ---

    @Test
    void fixtureF1() {
        ReserveResponse response = calculator.calculate(List.of(
                series(1, "Miete", "-900.00", RecurringInterval.MONTHLY, "2026-01-01", null),
                series(2, "Haftpflicht", "-240.00", RecurringInterval.YEARLY, "2026-10-15", null),
                series(3, "Fitness", "-15.00", RecurringInterval.MONTHLY, "2026-01-05", null, "2026-10-05")),
                OCTOBER);

        assertThat(response.month()).isEqualTo("2026-10");
        assertThat(response.series()).extracting(SeriesLine::id, line -> line.monthlyEquivalent().toPlainString())
                .containsExactly(
                        tuple(1L, "900.00"),
                        tuple(2L, "20.00"),
                        tuple(3L, "15.00"));
        assertThat(response.averageMonthlyCosts()).isEqualByComparingTo("935.00");
        assertThat(response.reserve()).isEqualByComparingTo("20.00");
        assertThat(totals(response)).containsExactly(
                "1140.00", "915.00", "915.00", "915.00", "915.00", "915.00",
                "915.00", "915.00", "915.00", "915.00", "915.00", "915.00");
        assertThat(response.forecast().get(0).occurrences()).extracting(ForecastOccurrence::description)
                .containsExactly("Miete", "Haftpflicht");
    }
}
