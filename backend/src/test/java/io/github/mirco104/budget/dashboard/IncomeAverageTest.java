package io.github.mirco104.budget.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.dashboard.IncomeAverage.Result;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rule R23: average income of the 12 months before M, only months before today's month and from the first entry. */
class IncomeAverageTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @Test
    void averagesTheTwelveMonthsBeforeTheMonth() {
        Map<YearMonth, BigDecimal> income = new HashMap<>();
        for (int i = 1; i <= 12; i++) {
            income.put(OCTOBER.minusMonths(i), new BigDecimal("2800"));
        }
        // A bonus in the oldest month and income in the month itself and before the window
        income.put(YearMonth.of(2025, 10), new BigDecimal("3400"));
        income.put(OCTOBER, new BigDecimal("9999"));
        income.put(YearMonth.of(2025, 9), new BigDecimal("9999"));

        Result result = IncomeAverage.of(income, YearMonth.of(2020, 1), OCTOBER, NOVEMBER);

        assertThat(result.months()).isEqualTo(12);
        assertThat(result.from()).isEqualTo(YearMonth.of(2025, 10));
        assertThat(result.to()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(result.average()).isEqualByComparingTo("2850.00");
    }

    @Test
    void countsOnlyMonthsFromTheFirstEntry() {
        Map<YearMonth, BigDecimal> income = Map.of(
                YearMonth.of(2026, 8), new BigDecimal("1000"),
                YearMonth.of(2026, 9), new BigDecimal("2000"));

        Result result = IncomeAverage.of(income, YearMonth.of(2026, 8), OCTOBER, NOVEMBER);

        assertThat(result.months()).isEqualTo(2);
        assertThat(result.from()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(result.average()).isEqualByComparingTo("1500.00");
    }

    @Test
    void monthsWithoutIncomeCountAsZero() {
        Map<YearMonth, BigDecimal> income = Map.of(YearMonth.of(2026, 9), new BigDecimal("3000"));

        Result result = IncomeAverage.of(income, YearMonth.of(2026, 7), OCTOBER, NOVEMBER);

        assertThat(result.months()).isEqualTo(3);
        assertThat(result.average()).isEqualByComparingTo("1000.00");
    }

    @Test
    void ignoresTodaysMonthAndLaterMonths() {
        Map<YearMonth, BigDecimal> income = Map.of(
                YearMonth.of(2026, 9), new BigDecimal("3000"),
                YearMonth.of(2026, 10), new BigDecimal("6000"),
                YearMonth.of(2026, 11), new BigDecimal("6000"));

        // Today is in October: for December only the months up to September count
        Result result = IncomeAverage.of(income, YearMonth.of(2026, 9), YearMonth.of(2026, 12), OCTOBER);

        assertThat(result.months()).isEqualTo(1);
        assertThat(result.to()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(result.average()).isEqualByComparingTo("3000.00");
    }

    @Test
    void isEmptyWithoutACountedMonth() {
        Result noEntry = IncomeAverage.of(Map.of(), null, OCTOBER, NOVEMBER);
        Result firstEntryThisMonth = IncomeAverage.of(Map.of(), OCTOBER, OCTOBER, NOVEMBER);
        Result onlyTodaysMonth = IncomeAverage.of(Map.of(), YearMonth.of(2026, 1), OCTOBER, YearMonth.of(2026, 1));

        for (Result result : new Result[] {noEntry, firstEntryThisMonth, onlyTodaysMonth}) {
            assertThat(result.months()).isZero();
            assertThat(result.average()).isNull();
            assertThat(result.from()).isNull();
            assertThat(result.to()).isNull();
        }
    }

    @Test
    void roundsHalfUpToCents() {
        Map<YearMonth, BigDecimal> income = Map.of(
                YearMonth.of(2026, 7), new BigDecimal("100.00"),
                YearMonth.of(2026, 8), new BigDecimal("100.00"),
                YearMonth.of(2026, 9), new BigDecimal("100.01"));

        Result result = IncomeAverage.of(income, YearMonth.of(2026, 7), OCTOBER, NOVEMBER);

        // 300.01 / 3 = 100.0033…
        assertThat(result.average()).isEqualByComparingTo("100.00");
        assertThat(result.average().scale()).isEqualTo(2);
    }

    // Fixture F1 (spec section 12): salary 3000.00 a month from 2026-01, today 2026-10-20
    @Test
    void fixtureF1() {
        Map<YearMonth, BigDecimal> income = new HashMap<>();
        for (int month = 1; month <= 10; month++) {
            income.put(YearMonth.of(2026, month), new BigDecimal("3000.00"));
        }

        Result result = IncomeAverage.of(income, YearMonth.of(2026, 1), OCTOBER, OCTOBER);

        assertThat(result.months()).isEqualTo(9);
        assertThat(result.average()).isEqualByComparingTo("3000.00");
    }
}
