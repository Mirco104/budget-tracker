package io.github.mirco104.budget.reserve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.reserve.ReserveResponse.CategoryAmount;
import io.github.mirco104.budget.reserve.ReserveCalculator.CategoryData;
import io.github.mirco104.budget.reserve.ReserveCalculator.PastMonth;
import io.github.mirco104.budget.reserve.ReserveCalculator.SeriesData;
import io.github.mirco104.budget.reserve.ReserveCalculator.SingleData;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastEntry;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastMonth;
import io.github.mirco104.budget.reserve.ReserveResponse.HistoryMonth;
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

    private static PastMonth expenses(String month, String amount) {
        return new PastMonth(month(month), BigDecimal.ZERO, money(amount), List.of(), List.of());
    }

    private static PastMonth past(String month, String income, String expenses) {
        return new PastMonth(month(month), money(income), money(expenses), List.of(), List.of());
    }

    private ReserveResponse onlySeries(YearMonth month, SeriesData... series) {
        return calculator.calculate(List.of(series), List.of(), List.of(), null, month, 12);
    }

    private ReserveResponse onlyAverage(YearMonth firstEntry, PastMonth... months) {
        return averageOver(12, firstEntry, months);
    }

    private ReserveResponse averageOver(int period, YearMonth firstEntry, PastMonth... months) {
        return calculator.calculate(List.of(), List.of(), List.of(months), firstEntry, OCTOBER, period);
    }

    // 100.00 expenses and 1000.00 income in each of the 24 months before October 2026, 400.00 in September
    private static PastMonth[] twoYears() {
        return IntStream.rangeClosed(1, 24)
                .mapToObj(i -> past(OCTOBER.minusMonths(i).toString(), "1000.00", i == 1 ? "400.00" : "100.00"))
                .toArray(PastMonth[]::new);
    }

    private static List<String> totals(ReserveResponse response) {
        return response.forecast().stream().map(month -> month.total().toPlainString()).toList();
    }

    // --- average expenses of the 12 completed months before M ---

    @Test
    void averagesTheTwelveMonthsBeforeTheMonth() {
        PastMonth[] months = IntStream.range(0, 12)
                .mapToObj(i -> new PastMonth(OCTOBER.minusMonths(12 - i), BigDecimal.ZERO,
                        money(i == 11 ? "220.00" : "100.00"), List.of(), List.of()))
                .toArray(PastMonth[]::new);

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
        assertThat(onlyAverage(null).averageIncome()).isNull();
        assertThat(onlyAverage(null).averageSurplus()).isNull();
        assertThat(onlyAverage(null).history()).isEmpty();
    }

    // --- chosen period ---

    @Test
    void averagesAndLooksBackOverThreeSixAndTwentyFourMonths() {
        ReserveResponse three = averageOver(3, month("2024-01"), twoYears());
        assertThat(three.averageMonths()).isEqualTo(3);
        assertThat(three.history()).extracting(HistoryMonth::month).containsExactly("2026-07", "2026-08", "2026-09");
        // (2 × 100 + 400) / 3
        assertThat(three.averageExpenses()).isEqualByComparingTo("200.00");

        ReserveResponse six = averageOver(6, month("2024-01"), twoYears());
        assertThat(six.averageMonths()).isEqualTo(6);
        assertThat(six.history()).first().extracting(HistoryMonth::month).isEqualTo("2026-04");
        assertThat(six.averageExpenses()).isEqualByComparingTo("150.00");

        ReserveResponse twentyFour = averageOver(24, month("2024-01"), twoYears());
        assertThat(twentyFour.averageMonths()).isEqualTo(24);
        assertThat(twentyFour.history()).first().extracting(HistoryMonth::month).isEqualTo("2024-10");
        // (23 × 100 + 400) / 24
        assertThat(twentyFour.averageExpenses()).isEqualByComparingTo("112.50");
    }

    @Test
    void everyPeriodStartsAtTheFirstEntryAtTheEarliest() {
        // First entry in August: two months count, whatever the period
        ReserveResponse three = averageOver(3, month("2026-08"), twoYears());
        assertThat(three.averageMonths()).isEqualTo(2);
        assertThat(three.history()).extracting(HistoryMonth::month).containsExactly("2026-08", "2026-09");
        assertThat(three.averageExpenses()).isEqualByComparingTo("250.00");

        ReserveResponse twentyFour = averageOver(24, month("2025-10"), twoYears());
        assertThat(twentyFour.averageMonths()).isEqualTo(12);
        assertThat(twentyFour.history()).first().extracting(HistoryMonth::month).isEqualTo("2025-10");
        // (11 × 100 + 400) / 12
        assertThat(twentyFour.averageExpenses()).isEqualByComparingTo("125.00");
    }

    @Test
    void anyOtherPeriodWorksTheSameWay() {
        ReserveResponse one = averageOver(1, month("2024-01"), twoYears());
        assertThat(one.history()).extracting(HistoryMonth::month).containsExactly("2026-09");
        assertThat(one.averageExpenses()).isEqualByComparingTo("400.00");

        ReserveResponse seven = averageOver(7, month("2024-01"), twoYears());
        assertThat(seven.averageMonths()).isEqualTo(7);
        assertThat(seven.history()).first().extracting(HistoryMonth::month).isEqualTo("2026-03");
        // (6 × 100 + 400) / 7
        assertThat(seven.averageExpenses()).isEqualByComparingTo("142.86");
    }

    @Test
    void totalsAndSharesFollowTheChosenPeriod() {
        ReserveResponse six = averageOver(6, month("2024-01"), twoYears());

        assertThat(six.totalIncome()).isEqualByComparingTo("6000.00");
        assertThat(six.totalExpenses()).isEqualByComparingTo("900.00");
        assertThat(six.totalSurplus()).isEqualByComparingTo("5100.00");
        assertThat(six.expensesShare()).isEqualByComparingTo("0.1500");
        assertThat(six.surplusShare()).isEqualByComparingTo("0.8500");
    }

    @Test
    void forecastAndPotLookTwelveMonthsAheadWhateverThePeriod() {
        SeriesData insurance = series(1, "Haftpflicht", "-240.00", RecurringInterval.YEARLY, "2026-12-15", null);

        ReserveResponse response = calculator.calculate(
                List.of(insurance), List.of(), List.of(twoYears()), month("2024-01"), OCTOBER, 3);

        assertThat(response.forecast()).hasSize(12);
        assertThat(response.monthlyReserve()).isEqualByComparingTo("20.00");
        assertThat(response.reserveBalance()).isEqualByComparingTo("200.00");
    }

    // --- look back over the months of the average ---

    @Test
    void lookBackListsIncomeExpensesAndSurplusOfEveryMonthOfTheAverage() {
        PastMonth september = new PastMonth(month("2026-09"), money("3000.00"), money("3200.00"),
                List.of(new CategoryData("Gehalt", money("3000.00"))),
                List.of(new CategoryData("Wohnen", money("900.00")), new CategoryData("Urlaub", money("2300.00"))));
        PastMonth[] months = IntStream.range(0, 11)
                .mapToObj(i -> new PastMonth(OCTOBER.minusMonths(12 - i), money("3000.00"), money("1000.00"),
                        List.of(), List.of()))
                .toArray(PastMonth[]::new);
        PastMonth[] all = Arrays.copyOf(months, 12);
        all[11] = september;

        ReserveResponse response = onlyAverage(month("2025-01"), all);

        assertThat(response.history()).extracting(HistoryMonth::month).containsExactly(
                "2025-10", "2025-11", "2025-12", "2026-01", "2026-02", "2026-03",
                "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09");
        HistoryMonth last = response.history().get(11);
        assertThat(last.income()).isEqualByComparingTo("3000.00");
        assertThat(last.expenses()).isEqualByComparingTo("3200.00");
        assertThat(last.surplus()).isEqualByComparingTo("-200.00");
        assertThat(last.incomeCategories())
                .containsExactly(new CategoryAmount("Gehalt", money("3000.00"), money("1.0000")));
        assertThat(last.expenseCategories()).extracting(CategoryAmount::name).containsExactly("Wohnen", "Urlaub");
        assertThat(response.history().get(0).surplus()).isEqualByComparingTo("2000.00");
        // (11 × 2000 − 200) / 12
        assertThat(response.averageIncome()).isEqualByComparingTo("3000.00");
        assertThat(response.averageExpenses()).isEqualByComparingTo("1183.33");
        assertThat(response.averageSurplus()).isEqualByComparingTo("1816.67");
    }

    @Test
    void lookBackStartsAtTheFirstEntryAndShowsMonthsWithoutEntriesAsZero() {
        ReserveResponse response = onlyAverage(month("2026-07"),
                past("2026-06", "999.00", "999.00"),
                past("2026-07", "500.00", "300.00"),
                past("2026-09", "0.00", "150.00"));

        assertThat(response.history()).extracting(HistoryMonth::month)
                .containsExactly("2026-07", "2026-08", "2026-09");
        HistoryMonth august = response.history().get(1);
        assertThat(august.income()).isEqualByComparingTo("0.00");
        assertThat(august.expenses()).isEqualByComparingTo("0.00");
        assertThat(august.surplus()).isEqualByComparingTo("0.00");
        assertThat(august.incomeCategories()).isEmpty();
        assertThat(august.expenseCategories()).isEmpty();
        assertThat(response.averageIncome()).isEqualByComparingTo("166.67");
        assertThat(response.averageSurplus()).isEqualByComparingTo("16.67");
    }

    @Test
    void lookBackGivesEveryValueAsShareOfTheIncomeOfItsMonth() {
        PastMonth september = new PastMonth(month("2026-09"), money("3000.00"), money("1000.00"),
                List.of(new CategoryData("Gehalt", money("3000.00"))),
                List.of(new CategoryData("Wohnen", money("900.00")), new CategoryData("Freizeit", money("100.00"))));

        HistoryMonth month = onlyAverage(month("2026-09"), september).history().get(0);

        assertThat(month.incomeShare()).isEqualByComparingTo("1");
        assertThat(month.expensesShare()).isEqualByComparingTo("0.3333");
        assertThat(month.surplusShare()).isEqualByComparingTo("0.6667");
        assertThat(month.expensesShare().scale()).isEqualTo(4);
        assertThat(month.incomeCategories()).extracting(CategoryAmount::share)
                .containsExactly(money("1.0000"));
        assertThat(month.expenseCategories()).extracting(CategoryAmount::share)
                .containsExactly(money("0.3000"), money("0.0333"));
    }

    @Test
    void lookBackHasNoSharesInAMonthWithoutIncome() {
        PastMonth september = new PastMonth(month("2026-09"), BigDecimal.ZERO, money("200.00"), List.of(),
                List.of(new CategoryData("Wohnen", money("200.00"))));

        HistoryMonth month = onlyAverage(month("2026-09"), september).history().get(0);

        assertThat(month.incomeShare()).isNull();
        assertThat(month.expensesShare()).isNull();
        assertThat(month.surplusShare()).isNull();
        assertThat(month.expenseCategories()).extracting(CategoryAmount::share).containsOnlyNulls();
    }

    @Test
    void totalSumsTheMonthsOfTheLookBackWithSharesOfTheTotalIncome() {
        // August without income still counts in the total
        ReserveResponse response = onlyAverage(month("2026-07"),
                past("2026-07", "2000.00", "500.00"),
                past("2026-08", "0.00", "300.00"),
                past("2026-09", "1000.00", "700.00"));

        assertThat(response.totalIncome()).isEqualByComparingTo("3000.00");
        assertThat(response.totalExpenses()).isEqualByComparingTo("1500.00");
        assertThat(response.totalSurplus()).isEqualByComparingTo("1500.00");
        assertThat(response.totalExpenses().scale()).isEqualTo(2);
        assertThat(response.incomeShare()).isEqualByComparingTo("1");
        assertThat(response.expensesShare()).isEqualByComparingTo("0.5000");
        assertThat(response.surplusShare()).isEqualByComparingTo("0.5000");
        assertThat(response.history().get(1).expensesShare()).isNull();
    }

    @Test
    void hasNoTotalWithoutACompletedMonthAndNoSharesWithoutIncome() {
        ReserveResponse empty = onlyAverage(null);
        assertThat(empty.totalIncome()).isNull();
        assertThat(empty.totalExpenses()).isNull();
        assertThat(empty.totalSurplus()).isNull();
        assertThat(empty.incomeShare()).isNull();
        assertThat(empty.expensesShare()).isNull();
        assertThat(empty.surplusShare()).isNull();

        ReserveResponse withoutIncome = onlyAverage(month("2026-09"), expenses("2026-09", "100.00"));
        assertThat(withoutIncome.totalExpenses()).isEqualByComparingTo("100.00");
        assertThat(withoutIncome.incomeShare()).isNull();
        assertThat(withoutIncome.expensesShare()).isNull();
        assertThat(withoutIncome.surplusShare()).isNull();
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
                List.of(), null, OCTOBER, 12);

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
        List<PastMonth> past = IntStream.rangeClosed(1, 9)
                .mapToObj(m -> new PastMonth(YearMonth.of(2026, m), BigDecimal.ZERO,
                        money(m == 9 ? "1914.00" : "915.00"), List.of(), List.of()))
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
                singles, past, month("2026-01"), OCTOBER, 12);

        assertThat(response.month()).isEqualTo("2026-10");
        assertThat(response.averageMonths()).isEqualTo(9);
        assertThat(response.averageExpenses()).isEqualByComparingTo("1026.00");
        assertThat(response.averageIncome()).isEqualByComparingTo("0.00");
        assertThat(response.averageSurplus()).isEqualByComparingTo("-1026.00");
        assertThat(response.history()).hasSize(9);
        assertThat(response.history().get(8).surplus()).isEqualByComparingTo("-1914.00");
        assertThat(response.history()).extracting(HistoryMonth::expensesShare).containsOnlyNulls();
        assertThat(response.totalIncome()).isEqualByComparingTo("0.00");
        assertThat(response.totalExpenses()).isEqualByComparingTo("9234.00");
        assertThat(response.totalSurplus()).isEqualByComparingTo("-9234.00");
        assertThat(response.expensesShare()).isNull();
        assertThat(response.surplusShare()).isNull();
        assertThat(response.reserves()).extracting(ReserveLine::description).containsExactly("Haftpflicht");
        assertThat(response.monthlyReserve()).isEqualByComparingTo("20.00");
        assertThat(response.reserveBalance()).isEqualByComparingTo("240.00");
        assertThat(totals(response)).containsExactly(
                "2636.49", "915.00", "915.00", "915.00", "915.00", "915.00",
                "915.00", "915.00", "915.00", "915.00", "915.00", "915.00");
        assertThat(response.forecast().get(0).entries()).hasSize(10);
    }
}
