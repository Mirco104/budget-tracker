package io.github.mirco104.budget.reserve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.reserve.HistoryResponse.CategoryAmount;
import io.github.mirco104.budget.reserve.HistoryResponse.HistoryMonth;
import io.github.mirco104.budget.reserve.ReserveCalculator.CategoryData;
import io.github.mirco104.budget.reserve.ReserveCalculator.PastMonth;
import io.github.mirco104.budget.reserve.ReserveCalculator.SeriesData;
import io.github.mirco104.budget.reserve.ReserveCalculator.SingleData;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastEntry;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastMonth;
import io.github.mirco104.budget.reserve.ReserveResponse.PotMonth;
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
 * Rule R14 from docs/spezifikation.md, section 2: look back (history) and outlook (reserve pot and forecast), the
 * test cases from section 7 and fixture F1 from section 12.
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
        return calculator.outlook(List.of(series), List.of(), month);
    }

    // 100.00 expenses and 1000.00 income in each of the 24 months before October 2026, 400.00 in September
    private static PastMonth[] twoYears() {
        return IntStream.rangeClosed(1, 24)
                .mapToObj(i -> past(OCTOBER.minusMonths(i).toString(), "1000.00", i == 1 ? "400.00" : "100.00"))
                .toArray(PastMonth[]::new);
    }

    private HistoryResponse history(String from, String to, YearMonth firstEntry, PastMonth... months) {
        return calculator.history(List.of(months), firstEntry, month(from), month(to));
    }

    private static List<String> totals(ReserveResponse response) {
        return response.forecast().stream().map(month -> month.total().toPlainString()).toList();
    }

    private static List<String> balances(ReserveResponse response) {
        return response.potMonths().stream().map(month -> month.balance().toPlainString()).toList();
    }

    private static List<String> withdrawals(ReserveResponse response) {
        return response.potMonths().stream().map(month -> month.withdrawal().toPlainString()).toList();
    }

    // --- look back from month V to month B (R14) ---

    @Test
    void averagesEveryMonthFromToIncludingBoth() {
        HistoryResponse response = history("2025-10", "2026-09", month("2024-01"), twoYears());

        assertThat(response.from()).isEqualTo("2025-10");
        assertThat(response.to()).isEqualTo("2026-09");
        assertThat(response.months()).isEqualTo(12);
        assertThat(response.history()).extracting(HistoryMonth::month).containsExactly(
                "2025-10", "2025-11", "2025-12", "2026-01", "2026-02", "2026-03",
                "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09");
        // (11 x 100 + 400) / 12
        assertThat(response.averageExpenses()).isEqualByComparingTo("125.00");
        assertThat(response.averageIncome()).isEqualByComparingTo("1000.00");
        assertThat(response.averageSurplus()).isEqualByComparingTo("875.00");
    }

    @Test
    void anyPeriodWorksTheSameWay() {
        HistoryResponse one = history("2026-09", "2026-09", month("2024-01"), twoYears());
        assertThat(one.months()).isEqualTo(1);
        assertThat(one.averageExpenses()).isEqualByComparingTo("400.00");

        HistoryResponse seven = history("2026-03", "2026-09", month("2024-01"), twoYears());
        assertThat(seven.months()).isEqualTo(7);
        // (6 x 100 + 400) / 7
        assertThat(seven.averageExpenses()).isEqualByComparingTo("142.86");

        HistoryResponse twentyFour = history("2024-10", "2026-09", month("2024-01"), twoYears());
        assertThat(twentyFour.months()).isEqualTo(24);
        // (23 x 100 + 400) / 24
        assertThat(twentyFour.averageExpenses()).isEqualByComparingTo("112.50");
    }

    // A calendar year as a look back, e.g. the year 2025
    @Test
    void looksBackOverACalendarYearThatIsNotTheLastTwelveMonths() {
        HistoryResponse year = history("2025-01", "2025-12", month("2024-01"), twoYears());

        assertThat(year.history()).extracting(HistoryMonth::month).startsWith("2025-01").endsWith("2025-12");
        assertThat(year.total().expenses()).isEqualByComparingTo("1200.00");
        assertThat(year.total().income()).isEqualByComparingTo("12000.00");
    }

    @Test
    void leavesOutMonthsOutsideThePeriod() {
        HistoryResponse response = history("2026-08", "2026-08", month("2025-01"),
                expenses("2026-07", "5000.00"), expenses("2026-08", "120.00"), expenses("2026-09", "5000.00"));

        assertThat(response.months()).isEqualTo(1);
        assertThat(response.averageExpenses()).isEqualByComparingTo("120.00");
    }

    @Test
    void startsAtTheFirstEntryAtTheEarliest() {
        // First entry in August: two months count, the requested period stays in the answer
        HistoryResponse response = history("2026-01", "2026-09", month("2026-08"), twoYears());

        assertThat(response.from()).isEqualTo("2026-01");
        assertThat(response.months()).isEqualTo(2);
        assertThat(response.history()).extracting(HistoryMonth::month).containsExactly("2026-08", "2026-09");
        assertThat(response.averageExpenses()).isEqualByComparingTo("250.00");
    }

    @Test
    void roundsTheAverageToCents() {
        HistoryResponse response = history("2026-07", "2026-09", month("2026-07"), expenses("2026-07", "100.00"));

        assertThat(response.averageExpenses()).isEqualByComparingTo("33.33");
    }

    @Test
    void hasNoAverageAndNoTotalWithoutACountedMonth() {
        for (HistoryResponse empty : List.of(
                history("2025-10", "2026-09", null),
                // first entry after the period
                history("2025-10", "2026-09", month("2026-10"), expenses("2026-10", "50.00")))) {
            assertThat(empty.months()).isZero();
            assertThat(empty.averageIncome()).isNull();
            assertThat(empty.averageExpenses()).isNull();
            assertThat(empty.averageSurplus()).isNull();
            assertThat(empty.total()).isNull();
            assertThat(empty.history()).isEmpty();
        }
    }

    @Test
    void listsIncomeExpensesAndSurplusOfEveryMonthWithTheCategories() {
        PastMonth september = new PastMonth(month("2026-09"), money("3000.00"), money("3200.00"),
                List.of(new CategoryData(20, "Gehalt", "wallet", money("3000.00"))),
                List.of(new CategoryData(1, "Wohnen", "home", money("900.00")),
                        new CategoryData(9, "Urlaub", "suitcase", money("2300.00"))));

        HistoryResponse response = history("2026-08", "2026-09", month("2025-01"),
                past("2026-08", "3000.00", "1000.00"), september);

        HistoryMonth last = response.history().get(1);
        assertThat(last.income()).isEqualByComparingTo("3000.00");
        assertThat(last.expenses()).isEqualByComparingTo("3200.00");
        assertThat(last.surplus()).isEqualByComparingTo("-200.00");
        assertThat(last.incomeCategories())
                .containsExactly(new CategoryAmount(20, "Gehalt", "wallet", money("3000.00")));
        assertThat(last.expenseCategories()).extracting(CategoryAmount::name).containsExactly("Wohnen", "Urlaub");
        assertThat(last.expenseCategories()).extracting(CategoryAmount::icon).containsExactly("home", "suitcase");
        assertThat(response.history().get(0).surplus()).isEqualByComparingTo("2000.00");
        // (2000 - 200) / 2
        assertThat(response.averageSurplus()).isEqualByComparingTo("900.00");
    }

    @Test
    void showsMonthsWithoutEntriesAsZero() {
        HistoryResponse response = history("2026-07", "2026-09", month("2026-07"),
                past("2026-07", "500.00", "300.00"),
                past("2026-09", "0.00", "150.00"));

        HistoryMonth august = response.history().get(1);
        assertThat(august.month()).isEqualTo("2026-08");
        assertThat(august.income()).isEqualByComparingTo("0.00");
        assertThat(august.expenses()).isEqualByComparingTo("0.00");
        assertThat(august.surplus()).isEqualByComparingTo("0.00");
        assertThat(august.incomeCategories()).isEmpty();
        assertThat(august.expenseCategories()).isEmpty();
        assertThat(response.averageIncome()).isEqualByComparingTo("166.67");
        assertThat(response.averageSurplus()).isEqualByComparingTo("16.67");
    }

    @Test
    void totalSumsTheCountedMonthsWithScaleTwo() {
        // August without income still counts in the total
        HistoryResponse response = history("2026-07", "2026-09", month("2026-07"),
                past("2026-07", "2000.00", "500.00"),
                past("2026-08", "0.00", "300.00"),
                past("2026-09", "1000.00", "700.00"));

        assertThat(response.total().income()).isEqualByComparingTo("3000.00");
        assertThat(response.total().expenses()).isEqualByComparingTo("1500.00");
        assertThat(response.total().surplus()).isEqualByComparingTo("1500.00");
        assertThat(response.total().expenses().scale()).isEqualTo(2);
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

    // --- course of the pot (R14, decision 37) ---

    @Test
    void potCourseRisesEachMonthAndStartsAgainAfterTheWithdrawal() {
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "Haftpflicht", "-240.00", RecurringInterval.YEARLY, "2026-10-15", null));

        assertThat(response.potMonths()).extracting(PotMonth::month).containsExactly(
                "2026-10", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03",
                "2027-04", "2027-05", "2027-06", "2027-07", "2027-08", "2027-09");
        assertThat(balances(response)).containsExactly(
                "240.00", "20.00", "40.00", "60.00", "80.00", "100.00",
                "120.00", "140.00", "160.00", "180.00", "200.00", "220.00");
        assertThat(withdrawals(response)).containsExactly(
                "240.00", "0.00", "0.00", "0.00", "0.00", "0.00",
                "0.00", "0.00", "0.00", "0.00", "0.00", "0.00");
    }

    @Test
    void potCourseSumsAllSeriesAndStartsWithTheBalanceOfNow() {
        // Quarterly Feb, May, Aug, Nov; yearly in March
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "Kfz", "-90.00", RecurringInterval.QUARTERLY, "2026-02-01", null),
                series(2, "Haftpflicht", "-240.00", RecurringInterval.YEARLY, "2027-03-01", null));

        assertThat(balances(response).subList(0, 7))
                .containsExactly("200.00", "250.00", "210.00", "260.00", "310.00", "270.00", "80.00");
        assertThat(withdrawals(response).subList(0, 7))
                .containsExactly("0.00", "90.00", "0.00", "0.00", "90.00", "240.00", "0.00");
        assertThat(response.potMonths().get(0).balance()).isEqualByComparingTo(response.reserveBalance());
    }

    @Test
    void potCourseFollowsSuspendedOccurrencesAndEndDates() {
        ReserveResponse ended = onlySeries(OCTOBER,
                series(1, "Beitrag", "-240.00", RecurringInterval.YEARLY, "2025-12-01", "2026-12-31"));
        assertThat(balances(ended).subList(0, 5)).containsExactly("200.00", "220.00", "240.00", "0.00", "0.00");
        assertThat(withdrawals(ended).subList(0, 4)).containsExactly("0.00", "0.00", "240.00", "0.00");

        // November suspended: the next occurrence is in February, more than one interval away until December
        ReserveResponse suspended = onlySeries(OCTOBER,
                series(2, "Kfz", "-90.00", RecurringInterval.QUARTERLY, "2026-02-01", null, "2026-11-01"));
        assertThat(balances(suspended).subList(0, 6))
                .containsExactly("0.00", "0.00", "30.00", "60.00", "90.00", "30.00");
        assertThat(withdrawals(suspended).subList(0, 5)).containsExactly("0.00", "0.00", "0.00", "0.00", "90.00");
    }

    @Test
    void potCourseRoundsOnlyTheSums() {
        // 100/12 = 8.333… twice: rounded per series 16.66, rounded at the end 16.67
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "A", "-100.00", RecurringInterval.YEARLY, "2026-09-01", null),
                series(2, "B", "-100.00", RecurringInterval.YEARLY, "2026-09-15", null));

        assertThat(response.potMonths().get(0).balance()).isEqualByComparingTo("16.67");
    }

    @Test
    void potCourseIsZeroWithoutRareExpenses() {
        ReserveResponse response = onlySeries(OCTOBER,
                series(1, "Miete", "-900.00", RecurringInterval.MONTHLY, "2026-01-01", null),
                series(2, "Bonus", "1200.00", RecurringInterval.YEARLY, "2026-12-01", null));

        assertThat(response.potMonths()).hasSize(12);
        assertThat(balances(response)).containsOnly("0.00");
        assertThat(withdrawals(response)).containsOnly("0.00");
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
        ReserveResponse response = calculator.outlook(
                List.of(series(1, "Streaming", "-15.00", RecurringInterval.MONTHLY, "2026-01-28", null),
                        series(2, "Gehalt", "3000.00", RecurringInterval.MONTHLY, "2026-01-01", null)),
                List.of(single(7, "Geschenke", "2026-12-20", "-50.00"),
                        single(8, "Erstattung", "2026-12-21", "30.00"),
                        single(9, "Zu spät", "2027-10-01", "-80.00")),
                OCTOBER);

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
    void fixtureF1LookBack() {
        List<PastMonth> past = IntStream.rangeClosed(1, 9)
                .mapToObj(m -> new PastMonth(YearMonth.of(2026, m), money("3000.00"),
                        money(m == 9 ? "1914.00" : "915.00"),
                        List.of(new CategoryData(15, "Gehalt/Lohn (netto)", "wallet", money("3000.00"))),
                        m == 9
                                ? List.of(new CategoryData(1, "Wohnen", "home", money("900.00")),
                                        new CategoryData(7, "Lebensmittel", "cart", money("999.00")),
                                        new CategoryData(8, "Freizeit", "music", money("15.00")))
                                : List.of(new CategoryData(1, "Wohnen", "home", money("900.00")),
                                        new CategoryData(8, "Freizeit", "music", money("15.00")))))
                .toList();

        HistoryResponse response = calculator.history(past, month("2026-01"), month("2025-10"), month("2026-09"));

        assertThat(response.from()).isEqualTo("2025-10");
        assertThat(response.to()).isEqualTo("2026-09");
        assertThat(response.months()).isEqualTo(9);
        assertThat(response.averageIncome()).isEqualByComparingTo("3000.00");
        assertThat(response.averageExpenses()).isEqualByComparingTo("1026.00");
        assertThat(response.averageSurplus()).isEqualByComparingTo("1974.00");
        assertThat(response.history()).hasSize(9);
        assertThat(response.history().get(0).surplus()).isEqualByComparingTo("2085.00");
        assertThat(response.history().get(8).expenses()).isEqualByComparingTo("1914.00");
        assertThat(response.history().get(8).surplus()).isEqualByComparingTo("1086.00");
        assertThat(response.total().income()).isEqualByComparingTo("27000.00");
        assertThat(response.total().expenses()).isEqualByComparingTo("9234.00");
        assertThat(response.total().surplus()).isEqualByComparingTo("17766.00");

        HistoryResponse september = calculator.history(past, month("2026-01"), month("2026-09"), month("2026-09"));
        assertThat(september.months()).isEqualTo(1);
        assertThat(september.averageExpenses()).isEqualByComparingTo("1914.00");
    }

    @Test
    void fixtureF1Outlook() {
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
        List<SeriesData> series = List.of(
                series(1, "Miete", "-900.00", RecurringInterval.MONTHLY, "2026-01-01", null),
                series(2, "Haftpflicht", "-240.00", RecurringInterval.YEARLY, "2026-10-15", null),
                series(3, "Fitness", "-15.00", RecurringInterval.MONTHLY, "2026-01-05", null, "2026-10-05"));

        ReserveResponse response = calculator.outlook(series, singles, OCTOBER);

        assertThat(response.month()).isEqualTo("2026-10");
        assertThat(response.reserves()).extracting(ReserveLine::description).containsExactly("Haftpflicht");
        assertThat(response.monthlyReserve()).isEqualByComparingTo("20.00");
        assertThat(response.reserveBalance()).isEqualByComparingTo("240.00");
        assertThat(totals(response)).containsExactly(
                "2636.49", "915.00", "915.00", "915.00", "915.00", "915.00",
                "915.00", "915.00", "915.00", "915.00", "915.00", "915.00");
        assertThat(response.forecast().get(0).entries()).hasSize(10);
        assertThat(balances(response)).containsExactly(
                "240.00", "20.00", "40.00", "60.00", "80.00", "100.00",
                "120.00", "140.00", "160.00", "180.00", "200.00", "220.00");
        assertThat(withdrawals(response).get(0)).isEqualTo("240.00");
        assertThat(withdrawals(response).subList(1, 12)).containsOnly("0.00");

        // From November 2026 the next occurrence is 2027-10-15 (k = 11)
        assertThat(calculator.outlook(series, List.of(), month("2026-11")).reserveBalance())
                .isEqualByComparingTo("20.00");
    }
}
