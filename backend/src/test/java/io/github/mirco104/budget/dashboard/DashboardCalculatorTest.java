package io.github.mirco104.budget.dashboard;

import static io.github.mirco104.budget.category.CategoryGroup.FIXED;
import static io.github.mirco104.budget.category.CategoryGroup.FLEXIBLE;
import static io.github.mirco104.budget.category.CategoryGroup.SAVINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.mirco104.budget.category.CategoryGroup;
import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.TransactionData;
import io.github.mirco104.budget.dashboard.DashboardResponse.CategoryLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.GroupLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.IncomeLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rules R1–R11 from docs/spezifikation.md, section 2. Test cases follow section 7, the reference fixture follows
 * section 12 (F1, with the recurring occurrences booked as single transactions).
 */
class DashboardCalculatorTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate IN_OCTOBER = LocalDate.of(2026, 10, 15);

    private static final long WOHNEN = 1;
    private static final long NEBENKOSTEN = 2;
    private static final long VERSICHERUNGEN = 3;
    private static final long MOBILITAET = 4;
    private static final long KREDITE = 5;
    private static final long LEBENSMITTEL = 6;
    private static final long FREIZEIT = 7;
    private static final long GESCHENKE = 8;
    private static final long INVESTITIONEN = 9;
    private static final long GEHALT = 11;
    private static final long NEBENJOB = 12;

    private final DashboardCalculator calculator = new DashboardCalculator();

    // --- Case 1: euro target only (R3, R5) ---

    @Test
    void euroTargetIsUsedAndDifferenceIsTargetMinusActual() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "800", null), ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "-750")));

        CategoryLine wohnen = category(result, WOHNEN);
        assertThat(wohnen.target()).isEqualByComparingTo("800.00");
        assertThat(wohnen.actual()).isEqualByComparingTo("750.00");
        assertThat(wohnen.difference()).isEqualByComparingTo("50.00");
    }

    // --- Case 2: percent target only, income > 0 (R3) ---

    @Test
    void percentTargetIsShareOfIncome() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, FLEXIBLE, null, "0.15"), ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2400")));

        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("360.00");
    }

    @Test
    void percentTargetIsRoundedHalfUpToCents() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, FLEXIBLE, null, "0.3333"), ungrouped(GEHALT)),
                List.of(booking(GEHALT, "100.15")));

        // 100.15 * 0.3333 = 33.380 -> 33.38
        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("33.38");
    }

    // --- Case 3: euro and percent set -> euro wins (R3) ---

    @Test
    void euroTargetWinsOverPercent() {
        DashboardResponse result = calculate(
                List.of(category(INVESTITIONEN, SAVINGS, "250", "0.10"), ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2400")));

        assertThat(category(result, INVESTITIONEN).target()).isEqualByComparingTo("250.00");
    }

    // --- Case 4: unplanned income, negative when over-planned; not part of the target total (R4, R6) ---

    @Test
    void unplannedIsIncomeMinusTargets() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "800", null), category(LEBENSMITTEL, FLEXIBLE, null, "0.15"),
                        ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2400")));

        // 2400 - 800 - 360
        assertThat(result.unplanned()).isEqualByComparingTo("1240.00");
        assertThat(result.unplannedLabel()).isEqualTo("Nicht verplant");
        assertThat(result.expenses().target()).isEqualByComparingTo("1160.00");
    }

    @Test
    void unplannedIsNegativeWhenOverPlanned() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "1500", null), category(LEBENSMITTEL, FLEXIBLE, null, "0.40"),
                        ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2000")));

        // 2000 - 1500 - 800
        assertThat(result.unplanned()).isEqualByComparingTo("-300.00");
        assertThat(result.unplannedLabel()).isEqualTo("Überplant");
        assertThat(result.expenses().target()).isEqualByComparingTo("2300.00");
    }

    @Test
    void differenceTotalIsTargetMinusActualAndSurplusIsUnplannedPlusDifference() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "800", null), category(LEBENSMITTEL, FLEXIBLE, "300", null),
                        ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "-800"), booking(LEBENSMITTEL, "-350")));

        assertThat(result.expenses().target()).isEqualByComparingTo("1100.00");
        assertThat(result.expenses().actual()).isEqualByComparingTo("1150.00");
        assertThat(result.expenses().difference()).isEqualByComparingTo("-50.00");
        assertThat(result.unplanned()).isEqualByComparingTo("900.00");
        assertThat(result.surplus()).isEqualByComparingTo("850.00");
    }

    // --- Case 5: income = 0 -> no division by zero, empty shares (R8, R9) ---

    @Test
    void zeroIncomeAndZeroExpensesGiveEmptySharesAndNoIncomeLines() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, FLEXIBLE, null, "0.15"), ungrouped(GEHALT),
                        ungrouped(NEBENJOB)),
                List.of());

        assertThat(result.incomeTotal()).isEqualByComparingTo("0.00");
        assertThat(result.incomeTotalShare()).isNull();
        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("0.00");
        assertThat(category(result, LEBENSMITTEL).targetShare()).isNull();
        assertThat(result.unplanned()).isEqualByComparingTo("0.00");
        assertThat(result.unplannedLabel()).isEqualTo("Nicht verplant");
        assertThat(result.surplus()).isEqualByComparingTo("0.00");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Überschuss");
        assertThat(result.surplusShare()).isNull();
        assertThat(result.expenses().actualShare()).isNull();
        assertThat(result.income()).isEmpty();
    }

    @Test
    void zeroIncomeKeepsEuroTargetsAndGivesNegativeUnplanned() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "900", null), category(LEBENSMITTEL, FLEXIBLE, null, "0.15"),
                        ungrouped(GEHALT)),
                List.of(booking(WOHNEN, "-100")));

        assertThat(category(result, WOHNEN).target()).isEqualByComparingTo("900.00");
        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("0.00");
        assertThat(result.unplanned()).isEqualByComparingTo("-900.00");
        assertThat(result.surplus()).isEqualByComparingTo("-100.00");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Verlust");
        assertThat(result.surplusShare()).isNull();
        assertThat(category(result, WOHNEN).actualShare()).isNull();
        assertThat(group(result, FIXED).actualShare()).isNull();
        assertThat(result.income()).isEmpty();
    }

    // --- Case 7: negative surplus -> "Monatlicher Verlust" (R7, R8) ---

    @Test
    void negativeSurplusIsLabelledAsLoss() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, null, null), ungrouped(GEHALT)),
                List.of(booking(GEHALT, "1000"), booking(WOHNEN, "-1250")));

        assertThat(result.surplus()).isEqualByComparingTo("-250.00");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Verlust");
        assertThat(result.surplusShare()).isEqualByComparingTo("-0.2500");
    }

    // --- Shares of income (R8) ---

    @Test
    void surplusShareIsRoundedHalfUp() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, FLEXIBLE, null, null), ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(LEBENSMITTEL, "-62.83")));

        // 1937.17 / 2000 = 0.968585 -> 0.9686
        assertThat(result.surplus()).isEqualByComparingTo("1937.17");
        assertThat(result.surplusShare()).isEqualByComparingTo("0.9686");
    }

    @Test
    void incomeTotalAndSurplusAreHundredPercentWithoutExpenses() {
        DashboardResponse result = calculate(
                List.of(ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2000")));

        assertThat(result.incomeTotalShare()).isEqualByComparingTo("1.0000");
        assertThat(result.surplusShare()).isEqualByComparingTo("1.0000");
    }

    @Test
    void everyAmountComesWithItsShareOfIncome() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "800", null), category(FREIZEIT, FLEXIBLE, null, null),
                        ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "-750"), booking(FREIZEIT, "-50")));

        CategoryLine wohnen = category(result, WOHNEN);
        assertThat(wohnen.targetShare()).isEqualByComparingTo("0.4000");
        assertThat(wohnen.actualShare()).isEqualByComparingTo("0.3750");
        assertThat(wohnen.differenceShare()).isEqualByComparingTo("0.0250");

        CategoryLine freizeit = category(result, FREIZEIT);
        assertThat(freizeit.targetShare()).isNull();
        assertThat(freizeit.actualShare()).isEqualByComparingTo("0.0250");
        assertThat(freizeit.differenceShare()).isNull();

        assertThat(group(result, FLEXIBLE).targetShare()).isEqualByComparingTo("0");
        assertThat(group(result, FLEXIBLE).differenceShare()).isEqualByComparingTo("-0.0250");
        assertThat(result.expenses().targetShare()).isEqualByComparingTo("0.4000");
        assertThat(result.expenses().actualShare()).isEqualByComparingTo("0.4000");
        assertThat(result.expenses().differenceShare()).isEqualByComparingTo("0");
        assertThat(income(result, GEHALT).share()).isEqualByComparingTo("1.0000");
    }

    // --- Case 9: the sign decides (R2, R9, R10, R11) ---

    @Test
    void refundLowersActualOfItsCategoryAndIsNoIncome() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, FLEXIBLE, null, null), ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(LEBENSMITTEL, "-100"), booking(LEBENSMITTEL, "30")));

        assertThat(category(result, LEBENSMITTEL).actual()).isEqualByComparingTo("70.00");
        assertThat(result.incomeTotal()).isEqualByComparingTo("2000.00");
        assertThat(result.income()).extracting(IncomeLine::id).containsExactly(GEHALT);
    }

    @Test
    void groupedCategoryWithPositiveNetCountsAsIncome() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, FLEXIBLE, null, null), ungrouped(GEHALT)),
                List.of(booking(GEHALT, "1500"), booking(LEBENSMITTEL, "-20"), booking(LEBENSMITTEL, "520")));

        assertThat(result.incomeTotal()).isEqualByComparingTo("2000.00");
        // No target and no expense left, so not in the budget table (R10)
        assertThat(result.categories()).extracting(CategoryLine::id).doesNotContain(LEBENSMITTEL);
        assertThat(result.expenses().actual()).isEqualByComparingTo("0.00");
        assertThat(result.income()).extracting(IncomeLine::id).containsExactly(LEBENSMITTEL, GEHALT);
        assertThat(income(result, LEBENSMITTEL).actual()).isEqualByComparingTo("500.00");
        assertThat(income(result, LEBENSMITTEL).share()).isEqualByComparingTo("0.2500");
    }

    @Test
    void ungroupedExpenseIsListedAndHasItsOwnGroupLine() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "800", null), ungrouped(GEHALT), ungrouped(NEBENJOB)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "-800"), booking(NEBENJOB, "-50")));

        assertThat(result.categories()).extracting(CategoryLine::id).containsExactly(WOHNEN, NEBENJOB);
        assertLine(result, NEBENJOB, null, "50.00", null);
        assertThat(category(result, NEBENJOB).group()).isNull();
        assertThat(result.groups()).extracting(GroupLine::group).containsExactly(FIXED, null);
        assertGroup(result, null, "0.00", "50.00", "-50.00");
        assertThat(result.expenses().actual()).isEqualByComparingTo("850.00");
        assertThat(result.surplus()).isEqualByComparingTo("1150.00");
    }

    @Test
    void incomeListShowsOnlyCategoriesWithPositiveNet() {
        DashboardResponse result = calculate(
                List.of(ungrouped(GEHALT), ungrouped(NEBENJOB)),
                List.of(booking(GEHALT, "2000"), booking(NEBENJOB, "-50")));

        assertThat(result.income()).extracting(IncomeLine::id).containsExactly(GEHALT);
        assertThat(income(result, GEHALT).share()).isEqualByComparingTo("1.0000");
    }

    // --- Budget table rows (R10) ---

    @Test
    void categoryWithoutTargetAndExpenseIsNotListed() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "800", null), category(FREIZEIT, FLEXIBLE, null, null),
                        ungrouped(GEHALT), ungrouped(NEBENJOB)),
                List.of(booking(GEHALT, "2000")));

        assertThat(result.categories()).extracting(CategoryLine::id).containsExactly(WOHNEN);
        assertThat(result.groups()).extracting(GroupLine::group).containsExactly(FIXED);
    }

    // --- Further rules ---

    @Test
    void differenceIsEmptyWithoutTarget() {
        DashboardResponse result = calculate(
                List.of(category(FREIZEIT, FLEXIBLE, null, null), ungrouped(GEHALT)),
                List.of(booking(FREIZEIT, "-75.10")));

        CategoryLine freizeit = category(result, FREIZEIT);
        assertThat(freizeit.target()).isNull();
        assertThat(freizeit.actual()).isEqualByComparingTo("75.10");
        assertThat(freizeit.difference()).isNull();
    }

    @Test
    void transactionsOutsideTheMonthAreIgnored() {
        DashboardResponse result = calculate(
                List.of(category(FREIZEIT, FLEXIBLE, null, null), ungrouped(GEHALT)),
                List.of(new TransactionData(FREIZEIT, new BigDecimal("-10"), LocalDate.of(2026, 9, 30)),
                        new TransactionData(FREIZEIT, new BigDecimal("-20"), LocalDate.of(2026, 10, 1)),
                        new TransactionData(FREIZEIT, new BigDecimal("-30"), LocalDate.of(2026, 10, 31)),
                        new TransactionData(FREIZEIT, new BigDecimal("-40"), LocalDate.of(2026, 11, 1))));

        assertThat(category(result, FREIZEIT).actual()).isEqualByComparingTo("50.00");
    }

    @Test
    void keepsInputOrderInExpenseAndIncomeLines() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "800", null), category(FREIZEIT, FLEXIBLE, "100", null),
                        ungrouped(GEHALT), ungrouped(NEBENJOB)),
                List.of(booking(NEBENJOB, "200"), booking(GEHALT, "2000")));

        assertThat(result.month()).isEqualTo("2026-10");
        assertThat(result.categories()).extracting(CategoryLine::id).containsExactly(WOHNEN, FREIZEIT);
        assertThat(result.income()).extracting(IncomeLine::id).containsExactly(GEHALT, NEBENJOB);
    }

    @Test
    void emptyMonthGivesZeroActuals() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "800", null), ungrouped(GEHALT)),
                List.of());

        assertThat(category(result, WOHNEN).actual()).isEqualByComparingTo("0.00");
        assertThat(category(result, WOHNEN).difference()).isEqualByComparingTo("800.00");
        assertThat(result.expenses().actual()).isEqualByComparingTo("0.00");
    }

    // --- Group totals (R10) ---

    @Test
    void groupsComeInFixedOrderWithSumsOfTheirCategories() {
        DashboardResponse result = calculate(
                List.of(
                        category(LEBENSMITTEL, FLEXIBLE, null, "0.15"),
                        category(WOHNEN, FIXED, "800", null),
                        category(FREIZEIT, FLEXIBLE, "100", null),
                        category(INVESTITIONEN, SAVINGS, "250", null),
                        ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "-780"), booking(LEBENSMITTEL, "-320.40"),
                        booking(FREIZEIT, "-75.10"), booking(INVESTITIONEN, "-250")));

        assertThat(result.groups()).extracting(GroupLine::group)
                .containsExactly(FIXED, FLEXIBLE, SAVINGS);
        assertGroup(result, FIXED, "800.00", "780.00", "20.00");
        assertGroup(result, FLEXIBLE, "400.00", "395.50", "4.50");
        assertGroup(result, SAVINGS, "250.00", "250.00", "0.00");
    }

    @Test
    void groupWithoutTargetsHasZeroTargetAndNegativeDifference() {
        DashboardResponse result = calculate(
                List.of(category(FREIZEIT, FLEXIBLE, null, null), ungrouped(GEHALT)),
                List.of(booking(FREIZEIT, "-75.10")));

        assertGroup(result, FLEXIBLE, "0.00", "75.10", "-75.10");
        assertThat(group(result, FLEXIBLE).target().scale()).isEqualTo(2);
    }

    @Test
    void groupWithoutListedCategoriesIsLeftOut() {
        DashboardResponse result = calculate(
                List.of(category(FREIZEIT, FLEXIBLE, null, null), ungrouped(GEHALT)),
                List.of(booking(GEHALT, "2000")));

        assertThat(result.groups()).isEmpty();
    }

    @Test
    void groupTotalsAddUpToExpensesTotals() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, FIXED, "1500", null), category(LEBENSMITTEL, FLEXIBLE, null, "0.40"),
                        category(VERSICHERUNGEN, FIXED, null, null), ungrouped(GEHALT),
                        ungrouped(NEBENJOB)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "-1500"), booking(LEBENSMITTEL, "-700"),
                        booking(LEBENSMITTEL, "25"), booking(VERSICHERUNGEN, "-50"), booking(NEBENJOB, "-12.34")));

        assertGroup(result, null, "0.00", "12.34", "-12.34");
        assertThat(result.groups().stream().map(GroupLine::target).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.expenses().target());
        assertThat(result.groups().stream().map(GroupLine::actual).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.expenses().actual());
        assertThat(result.groups().stream().map(GroupLine::difference).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.expenses().difference());
        assertThat(result.expenses().actual()).isEqualByComparingTo("2237.34");
    }

    // --- Reference fixture F1 (section 12) ---

    @Test
    void referenceFixtureF1MatchesSpec() {
        DashboardResponse result = calculate(
                List.of(
                        category(WOHNEN, FIXED, "900", null),
                        category(NEBENKOSTEN, FIXED, null, "0.10"),
                        category(VERSICHERUNGEN, FIXED, "100", null),
                        category(MOBILITAET, FIXED, "60", "0.50"),
                        category(KREDITE, FIXED, null, null),
                        category(LEBENSMITTEL, FLEXIBLE, "400", null),
                        category(FREIZEIT, FLEXIBLE, null, "0.05"),
                        category(GESCHENKE, FLEXIBLE, null, null),
                        category(INVESTITIONEN, SAVINGS, null, "0.20"),
                        ungrouped(GEHALT),
                        ungrouped(NEBENJOB)),
                List.of(
                        booking(GEHALT, "3000.00"),
                        booking(NEBENJOB, "200.00"),
                        booking(NEBENKOSTEN, "-200.00"),
                        booking(NEBENKOSTEN, "-140.00"),
                        booking(LEBENSMITTEL, "-120.30"),
                        booking(LEBENSMITTEL, "-200.20"),
                        booking(LEBENSMITTEL, "-130.00"),
                        booking(LEBENSMITTEL, "30.00"),
                        booking(MOBILITAET, "-45.99"),
                        booking(INVESTITIONEN, "-640.00"),
                        booking(GESCHENKE, "-20.00"),
                        // Occurrences of the recurring transactions of F1, booked as single transactions
                        new TransactionData(WOHNEN, new BigDecimal("-900.00"), LocalDate.of(2026, 10, 1)),
                        new TransactionData(VERSICHERUNGEN, new BigDecimal("-240.00"), LocalDate.of(2026, 10, 15)),
                        // Must be ignored
                        new TransactionData(LEBENSMITTEL, new BigDecimal("-999.00"), LocalDate.of(2026, 9, 30))));

        assertThat(result.incomeTotal()).isEqualByComparingTo("3200.00");

        assertLine(result, WOHNEN, "900.00", "900.00", "0.00");
        assertLine(result, NEBENKOSTEN, "320.00", "340.00", "-20.00");
        assertLine(result, VERSICHERUNGEN, "100.00", "240.00", "-140.00");
        assertLine(result, MOBILITAET, "60.00", "45.99", "14.01");
        assertLine(result, LEBENSMITTEL, "400.00", "420.50", "-20.50");
        assertLine(result, FREIZEIT, "160.00", "0.00", "160.00");
        assertLine(result, GESCHENKE, null, "20.00", null);
        assertLine(result, INVESTITIONEN, "640.00", "640.00", "0.00");

        assertThat(result.expenses().target()).isEqualByComparingTo("2580.00");
        assertThat(result.expenses().actual()).isEqualByComparingTo("2606.49");
        assertThat(result.expenses().difference()).isEqualByComparingTo("-26.49");
        assertThat(result.unplanned()).isEqualByComparingTo("620.00");

        assertThat(result.surplus()).isEqualByComparingTo("593.51");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Überschuss");
        assertThat(result.surplusShare()).isEqualByComparingTo("0.1855");

        assertThat(result.categories()).extracting(CategoryLine::id).doesNotContain(KREDITE);
        assertThat(result.expenses().targetShare()).isEqualByComparingTo("0.8063");
        assertThat(result.expenses().actualShare()).isEqualByComparingTo("0.8145");
        assertThat(result.expenses().differenceShare()).isEqualByComparingTo("-0.0083");
        assertThat(category(result, WOHNEN).targetShare()).isEqualByComparingTo("0.2813");
        assertThat(category(result, WOHNEN).actualShare()).isEqualByComparingTo("0.2813");

        assertThat(result.income()).extracting(IncomeLine::id, IncomeLine::name).containsExactly(
                tuple(GEHALT, "Kategorie " + GEHALT),
                tuple(NEBENJOB, "Kategorie " + NEBENJOB));
        assertThat(income(result, GEHALT).actual()).isEqualByComparingTo("3000.00");
        assertThat(income(result, GEHALT).share()).isEqualByComparingTo("0.9375");
        assertThat(income(result, NEBENJOB).share()).isEqualByComparingTo("0.0625");

        assertGroup(result, FIXED, "1380.00", "1525.99", "-145.99");
        assertGroup(result, FLEXIBLE, "560.00", "440.50", "119.50");
        assertGroup(result, SAVINGS, "640.00", "640.00", "0.00");
        assertThat(result.groups()).extracting(GroupLine::group).containsExactly(FIXED, FLEXIBLE, SAVINGS);
    }

    // --- Helpers ---

    private DashboardResponse calculate(List<CategoryData> categories, List<TransactionData> transactions) {
        return calculator.calculate(categories, transactions, OCTOBER);
    }

    private static CategoryData category(long id, CategoryGroup group, String amount, String percent) {
        return new CategoryData(id, "Kategorie " + id, group, decimal(amount), decimal(percent));
    }

    private static CategoryData ungrouped(long id) {
        return new CategoryData(id, "Kategorie " + id, null, null, null);
    }

    /** Signed amount: positive is income, negative is expense (R11). */
    private static TransactionData booking(long categoryId, String amount) {
        return new TransactionData(categoryId, new BigDecimal(amount), IN_OCTOBER);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static CategoryLine category(DashboardResponse result, long id) {
        return result.categories().stream().filter(line -> line.id() == id).findFirst().orElseThrow();
    }

    private static IncomeLine income(DashboardResponse result, long id) {
        return result.income().stream().filter(line -> line.id() == id).findFirst().orElseThrow();
    }

    private static GroupLine group(DashboardResponse result, CategoryGroup group) {
        return result.groups().stream().filter(line -> line.group() == group).findFirst().orElseThrow();
    }

    private static void assertGroup(DashboardResponse result, CategoryGroup group, String target, String actual,
            String difference) {
        GroupLine line = group(result, group);
        assertThat(line.target()).isEqualByComparingTo(target);
        assertThat(line.actual()).isEqualByComparingTo(actual);
        assertThat(line.difference()).isEqualByComparingTo(difference);
    }

    private static void assertLine(DashboardResponse result, long id, String target, String actual,
            String difference) {
        CategoryLine line = category(result, id);
        if (target == null) {
            assertThat(line.target()).isNull();
        } else {
            assertThat(line.target()).isEqualByComparingTo(target);
        }
        assertThat(line.actual()).isEqualByComparingTo(actual);
        if (difference == null) {
            assertThat(line.difference()).isNull();
        } else {
            assertThat(line.difference()).isEqualByComparingTo(difference);
        }
    }
}
