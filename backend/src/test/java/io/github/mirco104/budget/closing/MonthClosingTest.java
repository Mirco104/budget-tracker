package io.github.mirco104.budget.closing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** P10 and decision 65: what is left of a month and whether the bell asks about it. */
class MonthClosingTest {

    @Test
    void leftoverIsIncomeMinusExpensesMinusSaving() {
        assertThat(MonthClosing.leftover(new BigDecimal("3000"), new BigDecimal("2100.5"), new BigDecimal("300")))
                .isEqualByComparingTo("599.50");
        assertThat(MonthClosing.leftover(new BigDecimal("1000"), new BigDecimal("1200"), BigDecimal.ZERO))
                .isEqualByComparingTo("-200.00");
    }

    @Test
    void onlyTheMonthBeforeTheCurrentOneIsAskedAbout() {
        assertThat(MonthClosing.monthToAsk(YearMonth.of(2026, 11))).isEqualTo(YearMonth.of(2026, 10));
        assertThat(MonthClosing.monthToAsk(YearMonth.of(2027, 1))).isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void theBellAsksOnlyForAPositiveLeftoverOfAnOpenMonthWithBookings() {
        YearMonth october = YearMonth.of(2026, 10);
        assertThat(MonthClosing.asks(new BigDecimal("0.01"), false, october, YearMonth.of(2026, 1))).isTrue();
        assertThat(MonthClosing.asks(BigDecimal.ZERO, false, october, YearMonth.of(2026, 1))).isFalse();
        assertThat(MonthClosing.asks(new BigDecimal("-5"), false, october, YearMonth.of(2026, 1))).isFalse();
        assertThat(MonthClosing.asks(new BigDecimal("100"), true, october, YearMonth.of(2026, 1))).isFalse();
        // Before the first booking there is nothing to close
        assertThat(MonthClosing.asks(new BigDecimal("100"), false, october, YearMonth.of(2026, 11))).isFalse();
        assertThat(MonthClosing.asks(new BigDecimal("100"), false, october, null)).isFalse();
    }
}
