package io.github.mirco104.budget.category;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.category.TargetResolver.TargetEntry;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Rule R3 from docs/spezifikation.md: the target for month M is the latest entry with validFrom ≤ M.
 */
class TargetResolverTest {

    private static final long WOHNEN = 1;
    private static final long LEBENSMITTEL = 2;

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
    private static final YearMonth JANUARY = YearMonth.of(2027, 1);

    @Test
    void targetFromNovemberDoesNotApplyToOctober() {
        List<TargetEntry> entries = List.of(amount(WOHNEN, NOVEMBER, "800"));

        assertThat(TargetResolver.inEffect(entries, OCTOBER)).isEmpty();
    }

    @Test
    void targetFromNovemberAppliesToNovemberAndLaterMonths() {
        List<TargetEntry> entries = List.of(amount(WOHNEN, NOVEMBER, "800"));

        assertThat(TargetResolver.inEffect(entries, NOVEMBER).get(WOHNEN).targetAmount())
                .isEqualByComparingTo("800");
        assertThat(TargetResolver.inEffect(entries, DECEMBER).get(WOHNEN).targetAmount())
                .isEqualByComparingTo("800");
    }

    @Test
    void newerEntryOverridesOlderOneFromItsMonthOn() {
        List<TargetEntry> entries = List.of(amount(WOHNEN, NOVEMBER, "800"), amount(WOHNEN, JANUARY, "850"));

        assertThat(TargetResolver.inEffect(entries, DECEMBER).get(WOHNEN).targetAmount())
                .isEqualByComparingTo("800");
        assertThat(TargetResolver.inEffect(entries, JANUARY).get(WOHNEN).targetAmount())
                .isEqualByComparingTo("850");
        assertThat(TargetResolver.inEffect(entries, JANUARY.plusMonths(5)).get(WOHNEN).validFrom())
                .isEqualTo(JANUARY);
    }

    @Test
    void orderOfEntriesDoesNotMatter() {
        List<TargetEntry> entries = List.of(amount(WOHNEN, JANUARY, "850"), amount(WOHNEN, NOVEMBER, "800"));

        assertThat(TargetResolver.inEffect(entries, JANUARY).get(WOHNEN).targetAmount())
                .isEqualByComparingTo("850");
    }

    @Test
    void categoriesAreResolvedIndependently() {
        List<TargetEntry> entries = List.of(
                amount(WOHNEN, NOVEMBER, "800"),
                percent(LEBENSMITTEL, OCTOBER, "0.15"),
                percent(LEBENSMITTEL, DECEMBER, "0.20"));

        Map<Long, TargetEntry> november = TargetResolver.inEffect(entries, NOVEMBER);

        assertThat(november).containsOnlyKeys(WOHNEN, LEBENSMITTEL);
        assertThat(november.get(LEBENSMITTEL).targetPercent()).isEqualByComparingTo("0.15");
    }

    @Test
    void emptyNewerEntryClearsTheTarget() {
        List<TargetEntry> entries = List.of(
                amount(WOHNEN, NOVEMBER, "800"),
                new TargetEntry(WOHNEN, JANUARY, null, null));

        TargetEntry january = TargetResolver.inEffect(entries, JANUARY).get(WOHNEN);
        assertThat(january.targetAmount()).isNull();
        assertThat(january.targetPercent()).isNull();
    }

    private static TargetEntry amount(long categoryId, YearMonth validFrom, String amount) {
        return new TargetEntry(categoryId, validFrom, new BigDecimal(amount), null);
    }

    private static TargetEntry percent(long categoryId, YearMonth validFrom, String percent) {
        return new TargetEntry(categoryId, validFrom, null, new BigDecimal(percent));
    }
}
