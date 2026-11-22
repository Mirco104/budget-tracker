package io.github.mirco104.budget.plan;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mirco104.budget.plan.BudgetSuggester.Booking;
import io.github.mirco104.budget.plan.BudgetSuggester.Suggestion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Rule R38: budget suggestions of the setup wizard from the bookings of the 6 full months before the current one. */
class BudgetSuggesterTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final long LEBENSMITTEL = 1;
    private static final long FREIZEIT = 2;

    private static Booking own(String date, String amount, long categoryId, String name) {
        return new Booking(LocalDate.parse(date), new BigDecimal(amount), categoryId, name, null);
    }

    private static Booking bank(String date, String amount, String bankCategory) {
        return new Booking(LocalDate.parse(date), new BigDecimal(amount), null, null, bankCategory);
    }

    @Test
    void averageOverTheSixFullMonthsBeforeTheCurrentOne() {
        List<Suggestion> result = BudgetSuggester.suggest(List.of(
                own("2026-05-03", "-100.00", LEBENSMITTEL, "Lebensmittel"),
                own("2026-07-10", "-200.00", LEBENSMITTEL, "Lebensmittel"),
                own("2026-10-31", "-301.00", LEBENSMITTEL, "Lebensmittel")), NOVEMBER, Set.of());

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("Lebensmittel");
            assertThat(s.categoryId()).isEqualTo(LEBENSMITTEL);
            assertThat(s.bankCategory()).isNull();
            assertThat(s.months()).isEqualTo(6);
            assertThat(s.average()).isEqualByComparingTo("100.17");
            assertThat(s.proposal()).isEqualByComparingTo("100");
        });
    }

    @Test
    void bookingsOutsideTheWindowDoNotCount() {
        List<Suggestion> result = BudgetSuggester.suggest(List.of(
                own("2026-04-30", "-999.00", LEBENSMITTEL, "Lebensmittel"),
                own("2026-11-02", "-999.00", LEBENSMITTEL, "Lebensmittel"),
                own("2026-10-01", "-60.00", LEBENSMITTEL, "Lebensmittel")), NOVEMBER, Set.of());

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.months()).isEqualTo(1);
            assertThat(s.average()).isEqualByComparingTo("60.00");
        });
    }

    @Test
    void monthsStartWithTheFirstBookingInTheWindow() {
        List<Suggestion> result = BudgetSuggester.suggest(List.of(
                own("2026-09-15", "-90.00", LEBENSMITTEL, "Lebensmittel"),
                own("2026-10-15", "-60.00", FREIZEIT, "Freizeit")), NOVEMBER, Set.of());

        assertThat(result).extracting(Suggestion::name, Suggestion::months, Suggestion::average)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Lebensmittel", 2, new BigDecimal("45.00")),
                        org.assertj.core.groups.Tuple.tuple("Freizeit", 2, new BigDecimal("30.00")));
    }

    @Test
    void withoutOwnCategoryTheTopPartOfTheBankCategoryGroups() {
        List<Suggestion> result = BudgetSuggester.suggest(List.of(
                bank("2026-10-01", "-40.00", "Shopping › Kleidung"),
                bank("2026-10-05", "-20.00", "Shopping"),
                bank("2026-10-06", "-5.00", "  "),
                new Booking(LocalDate.parse("2026-10-07"), new BigDecimal("-7.00"), null, null, null)), NOVEMBER,
                Set.of());

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("Shopping");
            assertThat(s.categoryId()).isNull();
            assertThat(s.bankCategory()).isEqualTo("Shopping");
            assertThat(s.average()).isEqualByComparingTo("60.00");
        });
    }

    @Test
    void refundsLowerTheNetAndAPositiveNetGivesNoSuggestion() {
        List<Suggestion> result = BudgetSuggester.suggest(List.of(
                own("2026-10-01", "-80.00", LEBENSMITTEL, "Lebensmittel"),
                own("2026-10-02", "20.00", LEBENSMITTEL, "Lebensmittel"),
                own("2026-10-03", "50.00", FREIZEIT, "Freizeit"),
                own("2026-10-04", "-10.00", FREIZEIT, "Freizeit")), NOVEMBER, Set.of());

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("Lebensmittel");
            assertThat(s.average()).isEqualByComparingTo("60.00");
        });
    }

    @Test
    void categoriesWithABudgetDropOutAndTheRestIsSortedByAverage() {
        List<Suggestion> result = BudgetSuggester.suggest(List.of(
                own("2026-10-01", "-80.00", LEBENSMITTEL, "Lebensmittel"),
                own("2026-10-01", "-30.00", FREIZEIT, "Freizeit"),
                bank("2026-10-01", "-50.00", "Tanken")), NOVEMBER, Set.of(LEBENSMITTEL));

        assertThat(result).extracting(Suggestion::name).containsExactly("Tanken", "Freizeit");
    }

    @Test
    void proposalIsTheAverageRoundedToWholeEurosHalfUp() {
        List<Suggestion> result = BudgetSuggester.suggest(List.of(
                own("2026-10-01", "-24.50", LEBENSMITTEL, "Lebensmittel")), NOVEMBER, Set.of());

        assertThat(result.getFirst().proposal()).isEqualByComparingTo("25");
    }

    @Test
    void noBookingsNoSuggestions() {
        assertThat(BudgetSuggester.suggest(List.of(), NOVEMBER, Set.of())).isEmpty();
    }
}
