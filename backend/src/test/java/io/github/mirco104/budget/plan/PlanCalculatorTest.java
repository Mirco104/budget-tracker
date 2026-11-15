package io.github.mirco104.budget.plan;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.plan.PlanCalculator.ItemData;
import io.github.mirco104.budget.plan.PlanCalculator.Result;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rules P2–P4 and P9: monthly share, plan per category, groups, not planned and saving rate. */
class PlanCalculatorTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final long WOHNEN = 1;
    private static final long MOBILITAET = 2;
    private static final long LEBENSMITTEL = 3;
    private static final long GEHALT = 4;

    private static ItemData item(
            long id, Long category, String amount, RhythmUnit unit, Integer count, boolean saving, String from,
            String to) {
        return new ItemData(id, category, new BigDecimal(amount), saving, unit, count,
                YearMonth.parse(from), to == null ? null : YearMonth.parse(to));
    }

    private static ItemData monthly(long id, Long category, String amount) {
        return item(id, category, amount, RhythmUnit.MONTH, 1, false, "2026-01", null);
    }

    @Test
    void monthlyShareOfEveryRhythm() {
        assertThat(PlanCalculator.monthlyShare(new BigDecimal("-10"), RhythmUnit.WEEK, 1)).isEqualByComparingTo("43.33");
        assertThat(PlanCalculator.monthlyShare(new BigDecimal("-10"), RhythmUnit.WEEK, 2)).isEqualByComparingTo("21.67");
        assertThat(PlanCalculator.monthlyShare(new BigDecimal("-1200"), RhythmUnit.MONTH, 12))
                .isEqualByComparingTo("100.00");
        assertThat(PlanCalculator.monthlyShare(new BigDecimal("-55.08"), RhythmUnit.MONTH, 3))
                .isEqualByComparingTo("18.36");
        assertThat(PlanCalculator.monthlyShare(new BigDecimal("-250"), null, null)).isEqualByComparingTo("250.00");
        assertThat(PlanCalculator.monthlyShare(new BigDecimal("2500"), RhythmUnit.MONTH, 1))
                .isEqualByComparingTo("2500.00");
    }

    @Test
    void groupsAndNotPlanned() {
        List<ItemData> items = List.of(
                monthly(1, GEHALT, "2500"),
                monthly(2, WOHNEN, "-900"),
                item(3, MOBILITAET, "-1200", RhythmUnit.MONTH, 12, false, "2026-01", null),
                item(4, LEBENSMITTEL, "-400", null, null, false, "2026-01", null),
                item(5, MOBILITAET, "-10", RhythmUnit.WEEK, 1, false, "2026-01", null),
                item(6, null, "-300", RhythmUnit.MONTH, 1, true, "2026-01", null));

        Result result = PlanCalculator.of(items, NOVEMBER);

        assertThat(result.income()).isEqualByComparingTo("2500.00");
        assertThat(result.fixed()).isEqualByComparingTo("1043.33"); // 900 + 100 + 43.33
        assertThat(result.budgets()).isEqualByComparingTo("400.00");
        assertThat(result.saving()).isEqualByComparingTo("300.00");
        assertThat(result.unplanned()).isEqualByComparingTo("756.67");
        assertThat(result.savingRate()).isEqualByComparingTo("12.0");
        assertThat(result.planByCategory()).containsOnlyKeys(WOHNEN, MOBILITAET, LEBENSMITTEL);
        assertThat(result.planByCategory().get(MOBILITAET)).isEqualByComparingTo("143.33");
        assertThat(result.planByCategory().get(WOHNEN)).isEqualByComparingTo("900.00");
    }

    @Test
    void roundsOnlyAtTheEnd() {
        // Three times 100 / 3: rounded shares would give 99.99
        List<ItemData> items = List.of(
                item(1, WOHNEN, "-100", RhythmUnit.MONTH, 3, false, "2026-01", null),
                item(2, WOHNEN, "-100", RhythmUnit.MONTH, 3, false, "2026-01", null),
                item(3, WOHNEN, "-100", RhythmUnit.MONTH, 3, false, "2026-01", null));

        Result result = PlanCalculator.of(items, NOVEMBER);

        assertThat(result.fixed()).isEqualByComparingTo("100.00");
        assertThat(result.planByCategory().get(WOHNEN)).isEqualByComparingTo("100.00");
    }

    @Test
    void onlyItemsValidInTheMonthCount() {
        List<ItemData> items = List.of(
                item(1, WOHNEN, "-800", RhythmUnit.MONTH, 1, false, "2026-01", "2026-10"),
                item(2, WOHNEN, "-900", RhythmUnit.MONTH, 1, false, "2026-11", null),
                item(3, WOHNEN, "-50", RhythmUnit.MONTH, 1, false, "2026-12", null));

        assertThat(PlanCalculator.of(items, NOVEMBER).planByCategory().get(WOHNEN)).isEqualByComparingTo("900.00");
        assertThat(PlanCalculator.of(items, YearMonth.of(2026, 10)).planByCategory().get(WOHNEN))
                .isEqualByComparingTo("800.00");
        assertThat(PlanCalculator.validIn(items.get(0), NOVEMBER)).isFalse();
    }

    @Test
    void overplannedIsNegativeAndNoIncomeHasNoSavingRate() {
        Result result = PlanCalculator.of(List.of(monthly(1, WOHNEN, "-900")), NOVEMBER);

        assertThat(result.unplanned()).isEqualByComparingTo("-900.00");
        assertThat(result.savingRate()).isNull();
        assertThat(result.planTotal()).isEqualByComparingTo("900.00");
    }
}
