package io.github.mirco104.budget.reserve;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.plan.RhythmUnit;
import io.github.mirco104.budget.plan.TermSchedule;
import io.github.mirco104.budget.reserve.ReserveCalculator.Pot;
import io.github.mirco104.budget.reserve.ReserveCalculator.PotItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Rule P8: the reserve pot of a rare item, what it pays and what is missing. */
class ReservePotTest {

    // Kfz-Versicherung: 1,200 € yearly, due on 15 March, planned since January 2025
    private static PotItem kfz(String from) {
        YearMonth validFrom = YearMonth.parse(from);
        return new PotItem(1, "Kfz-Versicherung", 2L, new BigDecimal("1200.00"), 12,
                new TermSchedule(RhythmUnit.MONTH, 12, LocalDate.parse("2025-03-15"), validFrom, null), validFrom);
    }

    @Test
    void everyMonthTheShareIsPutAsideAndTheBalanceGrows() {
        Pot january = ReserveCalculator.pot(kfz("2025-01"), YearMonth.of(2026, 1), BigDecimal.ZERO, null);

        assertThat(january.monthly()).isEqualByComparingTo("100.00");
        // Last due March 2025: April 2025 to January 2026 = 10 months
        assertThat(january.balance()).isEqualByComparingTo("1000.00");
        assertThat(january.due()).isFalse();
        assertThat(january.nextDue()).isEqualTo(LocalDate.parse("2026-03-15"));
        assertThat(january.fromPot()).isEqualByComparingTo("0.00");
        assertThat(january.shortfall()).isEqualByComparingTo("0.00");
    }

    @Test
    void inTheDueMonthThePotPaysThePayment() {
        Pot march = ReserveCalculator.pot(kfz("2025-01"), YearMonth.of(2026, 3), new BigDecimal("1200.00"), null);

        assertThat(march.due()).isTrue();
        assertThat(march.balance()).isEqualByComparingTo("1200.00");
        assertThat(march.fromPot()).isEqualByComparingTo("1200.00");
        assertThat(march.shortfall()).isEqualByComparingTo("0.00");
    }

    @Test
    void whatThePotLacksCountsIntoTheBudget() {
        // Planned only since July 2025: July to March = 9 months, 900 € in the pot
        Pot march = ReserveCalculator.pot(kfz("2025-07"), YearMonth.of(2026, 3), new BigDecimal("1250.00"), null);

        assertThat(march.balance()).isEqualByComparingTo("900.00");
        assertThat(march.fromPot()).isEqualByComparingTo("900.00");
        assertThat(march.shortfall()).isEqualByComparingTo("350.00");
    }

    @Test
    void aLinkedAccountGivesTheBalance() {
        Pot march = ReserveCalculator.pot(kfz("2025-01"), YearMonth.of(2026, 3), new BigDecimal("1200.00"),
                new BigDecimal("1000.00"));

        assertThat(march.balance()).isEqualByComparingTo("1000.00");
        assertThat(march.fromPot()).isEqualByComparingTo("1000.00");
        assertThat(march.shortfall()).isEqualByComparingTo("200.00");
    }

    @Test
    void theFirstMonthStartsWithOneShare() {
        Pot first = ReserveCalculator.pot(kfz("2026-04"), YearMonth.of(2026, 4), BigDecimal.ZERO, null);

        assertThat(first.balance()).isEqualByComparingTo("100.00");
    }
}
