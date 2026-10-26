package io.github.mirco104.budget.reserve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.reserve.ReserveCalculator.MonthExpenses;
import io.github.mirco104.budget.reserve.ReserveCalculator.SeriesData;
import io.github.mirco104.budget.reserve.ReserveCalculator.SingleData;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastEntry;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastMonth;
import io.github.mirco104.budget.reserve.ReserveResponse.ReserveLine;
import io.github.mirco104.budget.transaction.TransactionResponse.Kind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    private static YearMonth month(String value) {
        return YearMonth.parse(value);
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

    private static SingleData single(long id, String description, String date, String amount) {
        return new SingleData(id, description, LocalDate.parse(date), money(amount));
    }

    private static MonthExpenses expenses(String month, String amount) {
        return new MonthExpenses(month(month), money(amount));
    }

    private ReserveResponse onlySeries(YearMonth month, SeriesData... series) {
        return calculator.calculate(List.of(series), List.of(), List.of(), null, month);
    }

    private ReserveResponse onlyAverage(YearMonth firstEntry, MonthExpenses... months) {
        return calculator.calculate(List.of(), List.of(), List.of(months), firstEntry, OCTOBER);
    }

    private static List<String> totals(ReserveResponse response) {
        return response.forecast().stream().map(month -> month.total().toPlainString()).toList();
    }

    // --- average expenses of the 12 completed months before M ---

    @Test
    void averagesTheTwelveMonthsBeforeTheMonth() {
        MonthExpenses[] months = IntStream.range(0, 12)
                .mapToObj(i -> new MonthExpenses(OCTOBER.minusMonths(12 - i), money(i == 11 ? "220.00" : "100.00")))
                .toArray(MonthExpenses[]::new);

        ReserveResponse response = onlyAverage(month("2025-01"), months);

        assertThat(response.averageMonths()).isEqualTo(12);
        assertThat(response.averageExpenses()).isEqualByComparingTo("110.00");
    }

    @Test
    void leavesOutTheMonthItselfAndMonthsBeforeTheWindow() {
        ReserveResponse response = onlyAverage(month("2025-01"),
                expenses("2025-09", "5000.00"),
                expenses("2025-10", "120.00"),
                expenses("2026-10", "5000.00"));

        assertThat(response.averageMonths()).isEqualTo(12);
        assertThat(response.averageExpenses()).isEqualByComparingTo("10.00");
    }

    @Test
    void averagesFewerMonthsFromTheFirstEntryOn() {
        // First entry in July: July to September count, a month without expenses counts as 0
        ReserveResponse response = onlyAverage(month("2026-07"),
                expenses("2026-07", "300.00"),
                expenses("2026-09", "150.00"));

        assertThat(response.averageMonths()).isEqualTo(3);
        assertThat(response.averageExpenses()).isEqualByComparingTo("150.00");
    }

    @Test
    void roundsTheAverageToCents() {
        ReserveResponse response = onlyAverage(month("2026-07"), expenses("2026-07", "100.00"));

        assertThat(response.averageExpenses()).isEqualByComparingTo("33.33");
    }

    @Test
    void hasNoAverageWithoutACompletedMonthWithEntries() {
        assertThat(onlyAverage(null).averageMonths()).isZero();
        assertThat(onlyAverage(null).averageExpenses()).isNull();
        assertThat(onlyAverage(OCTOBER, expenses("2026-10", "50.00")).averageExpenses()).isNull();
        assertThat(onlyAverage(month("2027-01")).averageMonths()).isZero();
    }

    // --- reserve pot ---

    @Test
    void potIsFullInTheMonthTheExpenseIsDue() {
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "Haftpflicht", "-240.00", RecurringInterval.YEARLY, "2026-10-15", null));

        assertThat(response.reserves()).extracting(ReserveLine::recurringId, ReserveLine::nextDate,
                        line -> line.monthly().toPlainString(), line -> line.saved().toPlainString())
                .containsExactly(tuple(1L, LocalDate.parse("2026-10-15"), "20.00", "240.00"));
        assertThat(response.monthlyReserve()).isEqualByComparingTo("20.00");
        assertThat(response.reserveBalance()).isEqualByComparingTo("240.00");
    }

    @Test
    void potStartsWithOneMonthlyRateAfterTheDueMonthAndGrowsEachMonth() {
        SeriesData haftpflicht = series(1, "Haftpflicht", "-240.00", RecurringInterval.YEARLY, "2026-10-15", null);

        assertThat(onlySeries(month("2026-11"), haftpflicht).reserveBalance()).isEqualByComparingTo("20.00");
        assertThat(onlySeries(month("2027-03"), haftpflicht).reserveBalance()).isEqualByComparingTo("100.00");
        assertThat(onlySeries(month("2027-03"), haftpflicht).reserves().get(0).nextDate())
                .isEqualTo(LocalDate.parse("2027-10-15"));
    }

    @Test
    void quarterlyExpenseSavesInThirds() {
        // Occurrences Feb, May, Aug, Nov: in October the next one is one month away
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "Kfz", "-90.00", RecurringInterval.QUARTERLY, "2026-02-01", null));

        assertThat(response.reserves().get(0).monthly()).isEqualByComparingTo("30.00");
        assertThat(response.reserves().get(0).saved()).isEqualByComparingTo("60.00");
    }

    @Test
    void suspendedNextOccurrenceMovesTheDueDate() {
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "Haftpflicht", "-240.00", RecurringInterval.YEARLY, "2025-10-15", null, "2026-10-15"));

        // Next counting occurrence is 2027-10-15, outside M..M+11
        assertThat(response.reserves()).isEmpty();

        ReserveResponse quarterly = onlySeries(OCTOBER,
                series(2, "Kfz", "-90.00", RecurringInterval.QUARTERLY, "2026-02-01", null, "2026-11-01"));
        assertThat(quarterly.reserves().get(0).nextDate()).isEqualTo(LocalDate.parse("2027-02-01"));
        // Four months away, more than one interval: nothing needs to be saved yet
        assertThat(quarterly.reserves().get(0).saved()).isEqualByComparingTo("0.00");
    }

    @Test
    void potLeavesOutMonthlyEndedAndIncomeSeries() {
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "Miete", "-900.00", RecurringInterval.MONTHLY, "2026-01-01", null),
                series(2, "Beendet", "-240.00", RecurringInterval.YEARLY, "2025-10-15", "2026-06-30"),
                series(3, "Bonus", "1200.00", RecurringInterval.YEARLY, "2026-12-01", null));

        assertThat(response.reserves()).isEmpty();
        assertThat(response.monthlyReserve()).isEqualByComparingTo("0.00");
        assertThat(response.reserveBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void potRoundsOnlyTheSums() {
        // 100/12 = 8.333… twice: rounded per series 16.66, rounded at the end 16.67
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "A", "-100.00", RecurringInterval.YEARLY, "2027-01-01", null),
                series(2, "B", "-100.00", RecurringInterval.YEARLY, "2027-02-01", null));

        assertThat(response.reserves()).extracting(ReserveLine::monthly)
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("8.33"));
        assertThat(response.monthlyReserve()).isEqualByComparingTo("16.67");
    }

    // --- forecast ---

    @Test
    void forecastCoversTheMonthAndTheFollowingElevenMonths() {
        ReserveResponse response = onlySeries(OCTOBER);

        assertThat(response.forecast()).extracting(ForecastMonth::month).containsExactly(
                "2026-10", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03",
                "2027-04", "2027-05", "2027-06", "2027-07", "2027-08", "2027-09");
        assertThat(response.forecast()).allSatisfy(month -> assertThat(month.total()).isEqualByComparingTo("0.00"));
    }

    @Test
    void forecastIncludesSingleExpensesButNoIncome() {
        ReserveResponse response = calculator.calculate(
                List.of(series(1, "Streaming", "-15.00", RecurringInterval.MONTHLY, "2026-01-28", null),
                        series(2, "Gehalt", "3000.00", RecurringInterval.MONTHLY, "2026-01-01", null)),
                List.of(single(7, "Geschenke", "2026-12-20", "-50.00"),
                        single(8, "Erstattung", "2026-12-21", "30.00"),
                        single(9, "Zu spät", "2027-10-01", "-80.00")),
                List.of(), null, OCTOBER);

        assertThat(totals(response).subList(0, 3)).containsExactly("15.00", "15.00", "65.00");
        assertThat(response.forecast().get(2).entries()).containsExactly(
                new ForecastEntry(Kind.SINGLE, 7L, null, "Geschenke", LocalDate.parse("2026-12-20"), money("50.00")),
                new ForecastEntry(Kind.RECURRING, null, 1L, "Streaming", LocalDate.parse("2026-12-28"), money("15.00")));
    }

    @Test
    void forecastLeavesOutSuspendedOccurrencesAndStopsAtTheEndDate() {
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "Fitness", "-15.00", RecurringInterval.MONTHLY, "2026-01-05", "2026-12-05", "2026-11-05"));

        assertThat(totals(response).subList(0, 4)).containsExactly("15.00", "0.00", "15.00", "0.00");
    }

    // --- fixture F1 (spec section 12) ---

    @Test
    void fixtureF1() {
        List<MonthExpenses> past = IntStream.rangeClosed(1, 9)
                .mapToObj(m -> new MonthExpenses(YearMonth.of(2026, m), money(m == 9 ? "1914.00" : "915.00")))
                .toList();
        List<SingleData> singles = List.of(
                single(1, "Gehalt", "2026-10-01", "3000.00"),
                single(2, "Nebenjob", "2026-10-10", "200.00"),
                single(3, "Strom", "2026-10-02", "-200.00"),
                single(4, "Wasser", "2026-10-03", "-140.00"),
                single(5, "Einkauf 1", "2026-10-04", "-120.30"),
                single(6, "Einkauf 2", "2026-10-11", "-200.20"),
                single(7, "Einkauf 3", "2026-10-18", "-130.00"),
                single(8, "Erstattung", "2026-10-19", "30.00"),
                single(9, "Tanken", "2026-10-06", "-45.99"),
                single(10, "Sparplan", "2026-10-15", "-640.00"),
                single(11, "Geschenk", "2026-10-20", "-20.00"));

        ReserveResponse response = calculator.calculate(List.of(
                        series(1, "Miete", "-900.00", RecurringInterval.MONTHLY, "2026-01-01", null),
                        series(2, "Haftpflicht", "-240.00", RecurringInterval.YEARLY, "2026-10-15", null),
                        series(3, "Fitness", "-15.00", RecurringInterval.MONTHLY, "2026-01-05", null, "2026-10-05")),
                singles, past, month("2026-01"), OCTOBER);

        assertThat(response.month()).isEqualTo("2026-10");
        assertThat(response.averageMonths()).isEqualTo(9);
        assertThat(response.averageExpenses()).isEqualByComparingTo("1026.00");
        assertThat(response.reserves()).extracting(ReserveLine::description).containsExactly("Haftpflicht");
        assertThat(response.monthlyReserve()).isEqualByComparingTo("20.00");
        assertThat(response.reserveBalance()).isEqualByComparingTo("240.00");
        assertThat(totals(response)).containsExactly(
                "2636.49", "915.00", "915.00", "915.00", "915.00", "915.00",
                "915.00", "915.00", "915.00", "915.00", "915.00", "915.00");
        assertThat(response.forecast().get(0).entries()).hasSize(10);
    }
}
