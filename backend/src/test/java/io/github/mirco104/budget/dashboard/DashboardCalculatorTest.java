package io.github.mirco104.budget.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.TransactionData;
import io.github.mirco104.budget.dashboard.DashboardResponse.CategoryLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.IncomeLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.MonthState;
import io.github.mirco104.budget.dashboard.IncomeAverage.Result;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rules R1–R11 and R23 from docs/spezifikation.md, section 2. Test cases follow section 7, the reference fixture follows
 * section 12 (F1).
 */
class DashboardCalculatorTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate IN_OCTOBER = LocalDate.of(2026, 10, 15);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

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
                List.of(category(WOHNEN, "800", null), noTarget(GEHALT)),
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
                List.of(category(LEBENSMITTEL, null, "0.15"), noTarget(GEHALT)),
                List.of(booking(GEHALT, "2400")));

        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("360.00");
    }

    @Test
    void percentTargetIsRoundedHalfUpToCents() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, null, "0.3333"), noTarget(GEHALT)),
                List.of(booking(GEHALT, "100.15")));

        // 100.15 * 0.3333 = 33.380 -> 33.38
        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("33.38");
    }

    // --- Case 3: euro and percent set -> euro wins (R3) ---

    @Test
    void euroTargetWinsOverPercent() {
        DashboardResponse result = calculate(
                List.of(category(INVESTITIONEN, "250", "0.10"), noTarget(GEHALT)),
                List.of(booking(GEHALT, "2400")));

        assertThat(category(result, INVESTITIONEN).target()).isEqualByComparingTo("250.00");
    }

    // --- Case 4: unplanned income, negative when over-planned; not part of the target total (R4, R6) ---

    @Test
    void unplannedIsIncomeMinusTargets() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "800", null), category(LEBENSMITTEL, null, "0.15"),
                        noTarget(GEHALT)),
                List.of(booking(GEHALT, "2400")));

        // 2400 - 800 - 360
        assertThat(result.unplanned()).isEqualByComparingTo("1240.00");
        assertThat(result.unplannedLabel()).isEqualTo("Nicht verplant");
        assertThat(result.expenses().target()).isEqualByComparingTo("1160.00");
    }

    @Test
    void unplannedIsNegativeWhenOverPlanned() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "1500", null), category(LEBENSMITTEL, null, "0.40"),
                        noTarget(GEHALT)),
                List.of(booking(GEHALT, "2000")));

        // 2000 - 1500 - 800
        assertThat(result.unplanned()).isEqualByComparingTo("-300.00");
        assertThat(result.unplannedLabel()).isEqualTo("Überplant");
        assertThat(result.expenses().target()).isEqualByComparingTo("2300.00");
    }

    @Test
    void differenceTotalIsTargetMinusActual() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "800", null), category(LEBENSMITTEL, "300", null),
                        noTarget(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "-800"), booking(LEBENSMITTEL, "-350")));

        assertThat(result.expenses().target()).isEqualByComparingTo("1100.00");
        assertThat(result.expenses().actual()).isEqualByComparingTo("1150.00");
        assertThat(result.expenses().difference()).isEqualByComparingTo("-50.00");
        assertThat(result.unplanned()).isEqualByComparingTo("900.00");
        assertThat(result.surplus()).isEqualByComparingTo("850.00");
    }

    // --- Case 5: income = 0 -> no division by zero, no income lines (R9) ---

    @Test
    void zeroIncomeAndZeroExpensesGiveNoIncomeLines() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, null, "0.15"), noTarget(GEHALT),
                        noTarget(NEBENJOB)),
                List.of());

        assertThat(result.incomeTotal()).isEqualByComparingTo("0.00");
        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("0.00");
        assertThat(result.unplanned()).isEqualByComparingTo("0.00");
        assertThat(result.unplannedLabel()).isEqualTo("Nicht verplant");
        assertThat(result.surplus()).isEqualByComparingTo("0.00");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Überschuss");
        assertThat(result.income()).isEmpty();
    }

    @Test
    void zeroIncomeKeepsEuroTargetsAndGivesNegativeUnplanned() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "900", null), category(LEBENSMITTEL, null, "0.15"),
                        noTarget(GEHALT)),
                List.of(booking(WOHNEN, "-100")));

        assertThat(category(result, WOHNEN).target()).isEqualByComparingTo("900.00");
        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("0.00");
        assertThat(result.unplanned()).isEqualByComparingTo("-900.00");
        assertThat(result.surplus()).isEqualByComparingTo("-100.00");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Verlust");
        assertThat(result.income()).isEmpty();
    }

    // --- Case 7: negative surplus -> "Monatlicher Verlust" (R7) ---

    @Test
    void negativeSurplusIsLabelledAsLoss() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, null, null), noTarget(GEHALT)),
                List.of(booking(GEHALT, "1000"), booking(WOHNEN, "-1250")));

        assertThat(result.surplus()).isEqualByComparingTo("-250.00");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Verlust");
    }

    // --- Case 9: the sign decides (R2, R9, R10, R11) ---

    @Test
    void refundLowersActualOfItsCategoryAndIsNoIncome() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, null, null), noTarget(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(LEBENSMITTEL, "-100"), booking(LEBENSMITTEL, "30")));

        assertThat(category(result, LEBENSMITTEL).actual()).isEqualByComparingTo("70.00");
        assertThat(result.incomeTotal()).isEqualByComparingTo("2000.00");
        assertThat(result.income()).extracting(IncomeLine::id).containsExactly(GEHALT);
    }

    @Test
    void expenseCategoryWithPositiveNetCountsAsIncome() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, null, null), noTarget(GEHALT)),
                List.of(booking(GEHALT, "1500"), booking(LEBENSMITTEL, "-20"), booking(LEBENSMITTEL, "520")));

        assertThat(result.incomeTotal()).isEqualByComparingTo("2000.00");
        // No target and no expense left, so not in the budget table (R10)
        assertThat(result.categories()).extracting(CategoryLine::id).doesNotContain(LEBENSMITTEL);
        assertThat(result.expenses().actual()).isEqualByComparingTo("0.00");
        assertThat(result.income()).extracting(IncomeLine::id).containsExactly(LEBENSMITTEL, GEHALT);
        assertThat(income(result, LEBENSMITTEL).actual()).isEqualByComparingTo("500.00");
    }

    @Test
    void expenseInAnIncomeCategoryIsListedWithoutTarget() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "800", null), noTarget(GEHALT), noTarget(NEBENJOB)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "-800"), booking(NEBENJOB, "-50")));

        assertThat(result.categories()).extracting(CategoryLine::id).containsExactly(WOHNEN, NEBENJOB);
        assertLine(result, NEBENJOB, null, "50.00", null);
        assertThat(result.expenses().actual()).isEqualByComparingTo("850.00");
        assertThat(result.surplus()).isEqualByComparingTo("1150.00");
    }

    @Test
    void incomeListShowsOnlyCategoriesWithPositiveNet() {
        DashboardResponse result = calculate(
                List.of(noTarget(GEHALT), noTarget(NEBENJOB)),
                List.of(booking(GEHALT, "2000"), booking(NEBENJOB, "-50")));

        assertThat(result.income()).extracting(IncomeLine::id).containsExactly(GEHALT);
        assertThat(income(result, GEHALT).actual()).isEqualByComparingTo("2000.00");
    }

    // --- Budget table rows (R10) ---

    @Test
    void categoryWithoutTargetAndExpenseIsNotListed() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "800", null), category(FREIZEIT, null, null),
                        noTarget(GEHALT), noTarget(NEBENJOB)),
                List.of(booking(GEHALT, "2000")));

        assertThat(result.categories()).extracting(CategoryLine::id).containsExactly(WOHNEN);
    }

    // R3: every category can have a target, also one that is typically used for income
    @Test
    void everyCategoryCanHaveATarget() {
        DashboardResponse result = calculate(
                List.of(category(NEBENJOB, "50", null), noTarget(GEHALT)),
                List.of(booking(GEHALT, "2000")));

        assertLine(result, NEBENJOB, "50.00", "0.00", "50.00");
        assertThat(result.expenses().target()).isEqualByComparingTo("50.00");
    }

    // --- Further rules ---

    @Test
    void differenceIsEmptyWithoutTarget() {
        DashboardResponse result = calculate(
                List.of(category(FREIZEIT, null, null), noTarget(GEHALT)),
                List.of(booking(FREIZEIT, "-75.10")));

        CategoryLine freizeit = category(result, FREIZEIT);
        assertThat(freizeit.target()).isNull();
        assertThat(freizeit.actual()).isEqualByComparingTo("75.10");
        assertThat(freizeit.difference()).isNull();
    }

    @Test
    void transactionsOutsideTheMonthAreIgnored() {
        DashboardResponse result = calculate(
                List.of(category(FREIZEIT, null, null), noTarget(GEHALT)),
                List.of(booking(FREIZEIT, "-10", LocalDate.of(2026, 9, 30)),
                        booking(FREIZEIT, "-20", LocalDate.of(2026, 10, 1)),
                        booking(FREIZEIT, "-30", LocalDate.of(2026, 10, 31)),
                        booking(FREIZEIT, "-40", LocalDate.of(2026, 11, 1))));

        assertThat(category(result, FREIZEIT).actual()).isEqualByComparingTo("50.00");
    }

    @Test
    void keepsInputOrderInExpenseAndIncomeLines() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "800", null), category(FREIZEIT, "100", null),
                        noTarget(GEHALT), noTarget(NEBENJOB)),
                List.of(booking(NEBENJOB, "200"), booking(GEHALT, "2000")));

        assertThat(result.month()).isEqualTo("2026-10");
        assertThat(result.categories()).extracting(CategoryLine::id).containsExactly(WOHNEN, FREIZEIT);
        assertThat(result.income()).extracting(IncomeLine::id).containsExactly(GEHALT, NEBENJOB);
    }

    @Test
    void emptyMonthGivesZeroActuals() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "800", null), noTarget(GEHALT)),
                List.of());

        assertThat(category(result, WOHNEN).actual()).isEqualByComparingTo("0.00");
        assertThat(category(result, WOHNEN).difference()).isEqualByComparingTo("800.00");
        assertThat(result.expenses().actual()).isEqualByComparingTo("0.00");
    }

    // --- Case 11: recurring and one-off expenses (R10) ---

    @Test
    void onlyOccurrencesWithNegativeAmountCountAsRecurring() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "900", null), noTarget(LEBENSMITTEL), noTarget(GEHALT)),
                List.of(booking(GEHALT, "1500"), occurrence(GEHALT, "500"), occurrence(WOHNEN, "-900"),
                        booking(LEBENSMITTEL, "-100")));

        assertThat(result.expenses().actual()).isEqualByComparingTo("1000.00");
        assertThat(result.expenses().recurring()).isEqualByComparingTo("900.00");
        assertThat(result.expenses().oneOff()).isEqualByComparingTo("100.00");
    }

    @Test
    void refundLowersTheOneOffPartFirst() {
        DashboardResponse result = calculate(
                List.of(noTarget(FREIZEIT), noTarget(GEHALT)),
                List.of(booking(GEHALT, "2000"), occurrence(FREIZEIT, "-100"), booking(FREIZEIT, "-50"),
                        booking(FREIZEIT, "30")));

        // I = 120, V = 100 -> W = 100, one-off 20
        assertThat(result.expenses().recurring()).isEqualByComparingTo("100.00");
        assertThat(result.expenses().oneOff()).isEqualByComparingTo("20.00");
    }

    @Test
    void refundBeyondTheOneOffPartLowersRecurringToTheActual() {
        DashboardResponse result = calculate(
                List.of(noTarget(FREIZEIT), noTarget(GEHALT)),
                List.of(booking(GEHALT, "2000"), occurrence(FREIZEIT, "-100"), booking(FREIZEIT, "-20"),
                        booking(FREIZEIT, "50")));

        // I = 70, V = 100 -> W = 70, one-off 0
        assertThat(result.expenses().recurring()).isEqualByComparingTo("70.00");
        assertThat(result.expenses().oneOff()).isEqualByComparingTo("0.00");
    }

    @Test
    void recurringExpenseInACategoryWithPositiveNetDoesNotCount() {
        DashboardResponse result = calculate(
                List.of(noTarget(NEBENJOB)),
                List.of(occurrence(NEBENJOB, "-30"), booking(NEBENJOB, "200")));

        assertThat(result.expenses().actual()).isEqualByComparingTo("0.00");
        assertThat(result.expenses().recurring()).isEqualByComparingTo("0.00");
        assertThat(result.expenses().oneOff()).isEqualByComparingTo("0.00");
    }

    @Test
    void recurringAndOneOffAddUpToExpenses() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "1500", null), category(LEBENSMITTEL, null, "0.40"),
                        noTarget(VERSICHERUNGEN), noTarget(GEHALT), noTarget(NEBENJOB)),
                List.of(booking(GEHALT, "2000"), occurrence(WOHNEN, "-1500"), booking(LEBENSMITTEL, "-700"),
                        booking(LEBENSMITTEL, "25"), occurrence(LEBENSMITTEL, "-9.99"),
                        occurrence(VERSICHERUNGEN, "-50"), booking(NEBENJOB, "-12.34")));

        assertThat(result.expenses().actual()).isEqualByComparingTo("2247.33");
        assertThat(result.expenses().recurring()).isEqualByComparingTo("1559.99");
        assertThat(result.expenses().recurring().add(result.expenses().oneOff()))
                .isEqualByComparingTo(result.expenses().actual());
        assertThat(result.expenses().oneOff().scale()).isEqualTo(2);
    }

    @Test
    void occurrencesOutsideTheMonthAreIgnored() {
        DashboardResponse result = calculate(
                List.of(noTarget(WOHNEN), noTarget(GEHALT)),
                List.of(occurrence(WOHNEN, "-900", LocalDate.of(2026, 9, 1)),
                        occurrence(WOHNEN, "-900", LocalDate.of(2026, 11, 1))));

        assertThat(result.expenses().recurring()).isEqualByComparingTo("0.00");
        assertThat(result.expenses().oneOff()).isEqualByComparingTo("0.00");
    }

    // --- Ø income as the basis (R3, R4, R23, decision 26) ---

    @Test
    void percentTargetUsesTheAverageIncomeNotTheIncomeOfTheMonth() {
        DashboardResponse result = calculate(
                List.of(category(LEBENSMITTEL, null, "0.10"), noTarget(GEHALT)),
                List.of(booking(GEHALT, "1000")),
                average("2850.00"), NOVEMBER);

        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("285.00");
    }

    @Test
    void withoutAverageIncomeAPercentTargetIsNoTargetAndEuroTargetsStay() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "900", null), category(LEBENSMITTEL, null, "0.15"),
                        category(MOBILITAET, "60", "0.50"), noTarget(GEHALT)),
                List.of(booking(GEHALT, "2000")),
                Result.EMPTY, NOVEMBER);

        assertThat(category(result, WOHNEN).target()).isEqualByComparingTo("900.00");
        assertThat(category(result, MOBILITAET).target()).isEqualByComparingTo("60.00");
        assertThat(result.categories()).extracting(CategoryLine::id).doesNotContain(LEBENSMITTEL);
        assertThat(result.expenses().target()).isEqualByComparingTo("960.00");
        assertThat(result.unplanned()).isNull();
        assertThat(result.unplannedLabel()).isNull();
        assertThat(result.averageIncome()).isNull();
        assertThat(result.averageIncomeMonths()).isZero();
        assertThat(result.averageIncomeFrom()).isNull();
        assertThat(result.averageExpenses()).isNull();
    }

    @Test
    void unplannedIsAverageIncomeMinusTargets() {
        DashboardResponse result = calculate(
                List.of(category(WOHNEN, "900", null), category(LEBENSMITTEL, null, "0.10"), noTarget(GEHALT)),
                List.of(booking(GEHALT, "500")),
                average("2000.00"), NOVEMBER);

        // 2000 - 900 - 200; the income of the month (500) does not matter
        assertThat(result.unplanned()).isEqualByComparingTo("900.00");
        assertThat(result.unplannedLabel()).isEqualTo("Nicht verplant");
    }

    @Test
    void averageIncomeComesWithItsPeriod() {
        DashboardResponse result = calculate(
                List.of(noTarget(GEHALT)),
                List.of(booking(GEHALT, "2400")),
                new Result(new BigDecimal("3000.00"), 9, YearMonth.of(2026, 1), YearMonth.of(2026, 9)), NOVEMBER);

        assertThat(result.averageIncome()).isEqualByComparingTo("3000.00");
        assertThat(result.averageIncomeMonths()).isEqualTo(9);
        assertThat(result.averageIncomeFrom()).isEqualTo("2026-01");
        assertThat(result.averageIncomeTo()).isEqualTo("2026-09");
    }

    // --- Ø expenses Ā(M) over the months of Ē(M) (R23, decision 34) ---

    @Test
    void averageExpensesArePassedThroughAndDoNotChangeTheMonth() {
        DashboardResponse result = calculator.calculate(
                List.of(category(WOHNEN, "900", null), noTarget(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "-900")),
                OCTOBER, average("2000.00"), new BigDecimal("1234.5"), NOVEMBER);

        assertThat(result.averageExpenses()).isEqualByComparingTo("1234.50");
        assertThat(result.averageExpenses().scale()).isEqualTo(2);
        assertThat(result.expenses().actual()).isEqualByComparingTo("900.00");
    }

    @Test
    void expensesOfAMonthAreTheSumOfTheExpenseActuals() {
        // Lebensmittel nets to -30, Nebenjob to +300 (income, not an expense); September does not count
        BigDecimal expenses = DashboardCalculator.expensesOf(List.of(
                booking(GEHALT, "2000"), booking(NEBENJOB, "300"), booking(LEBENSMITTEL, "-50"),
                booking(LEBENSMITTEL, "20"), occurrence(WOHNEN, "-900"),
                booking(WOHNEN, "-999", LocalDate.of(2026, 9, 30))), OCTOBER);

        assertThat(expenses).isEqualByComparingTo("930.00");
    }

    // --- Labels by month (R7, decision 27) ---

    @Test
    void pastMonthShowsSurplusOrLoss() {
        DashboardResponse surplus = calculate(List.of(noTarget(GEHALT)), List.of(booking(GEHALT, "100")),
                average("100.00"), NOVEMBER);
        DashboardResponse loss = calculate(List.of(noTarget(WOHNEN)), List.of(booking(WOHNEN, "-100")),
                average("100.00"), NOVEMBER);

        assertThat(surplus.monthState()).isEqualTo(MonthState.PAST);
        assertThat(surplus.surplusLabel()).isEqualTo("Monatlicher Überschuss");
        assertThat(loss.surplusLabel()).isEqualTo("Monatlicher Verlust");
    }

    @Test
    void currentMonthShowsWhatIsStillFreeOrOverrun() {
        DashboardResponse free = calculate(List.of(noTarget(GEHALT)), List.of(booking(GEHALT, "100")),
                average("100.00"), OCTOBER);
        DashboardResponse overrun = calculate(List.of(noTarget(WOHNEN)), List.of(booking(WOHNEN, "-100")),
                average("100.00"), OCTOBER);

        assertThat(free.monthState()).isEqualTo(MonthState.CURRENT);
        assertThat(free.surplusLabel()).isEqualTo("Noch frei");
        assertThat(overrun.surplusLabel()).isEqualTo("Überschritten");
        // The amount keeps its sign and is shown with it (R7, decision 34)
        assertThat(overrun.surplus()).isEqualByComparingTo("-100.00");
    }

    @Test
    void futureMonthAlsoShowsWhatIsStillFree() {
        DashboardResponse result = calculate(List.of(noTarget(GEHALT)), List.of(booking(GEHALT, "100")),
                average("100.00"), YearMonth.of(2026, 9));

        assertThat(result.monthState()).isEqualTo(MonthState.FUTURE);
        assertThat(result.surplusLabel()).isEqualTo("Noch frei");
    }

    @Test
    void incomeOfAMonthIsTheSumOfThePositiveNets() {
        BigDecimal income = DashboardCalculator.incomeOf(List.of(
                booking(GEHALT, "2000"), booking(NEBENJOB, "300"), booking(LEBENSMITTEL, "-50"),
                booking(LEBENSMITTEL, "20"), booking(GEHALT, "999", LocalDate.of(2026, 9, 30))), OCTOBER);

        assertThat(income).isEqualByComparingTo("2300.00");
    }

    // --- Reference fixture F1 (section 12) ---

    @Test
    void referenceFixtureF1MatchesSpec() {
        DashboardResponse result = calculate(
                List.of(
                        category(WOHNEN, "900", null),
                        category(NEBENKOSTEN, null, "0.10"),
                        category(VERSICHERUNGEN, "100", null),
                        category(MOBILITAET, "60", "0.50"),
                        category(KREDITE, null, null),
                        category(LEBENSMITTEL, "400", null),
                        category(FREIZEIT, null, "0.05"),
                        category(GESCHENKE, null, null),
                        category(INVESTITIONEN, null, "0.20"),
                        noTarget(GEHALT),
                        noTarget(NEBENJOB)),
                List.of(
                        // Since decision 26 the salary is a monthly series from 2026-01
                        occurrence(GEHALT, "3000.00", LocalDate.of(2026, 10, 1)),
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
                        // Occurrences of the recurring transactions of F1; Freizeit is suspended in October
                        occurrence(WOHNEN, "-900.00", LocalDate.of(2026, 10, 1)),
                        occurrence(VERSICHERUNGEN, "-240.00", LocalDate.of(2026, 10, 15)),
                        // Must be ignored
                        booking(LEBENSMITTEL, "-999.00", LocalDate.of(2026, 9, 30))),
                // R23: salary 3000.00 in 2026-01 to 2026-09; today is 2026-10-20; Ā = 9234.00 / 9
                new Result(new BigDecimal("3000.00"), 9, YearMonth.of(2026, 1), YearMonth.of(2026, 9)),
                new BigDecimal("1026.00"), OCTOBER);

        assertThat(result.incomeTotal()).isEqualByComparingTo("3200.00");
        assertThat(result.averageIncome()).isEqualByComparingTo("3000.00");
        assertThat(result.averageIncomeMonths()).isEqualTo(9);
        assertThat(result.averageExpenses()).isEqualByComparingTo("1026.00");

        assertLine(result, WOHNEN, "900.00", "900.00", "0.00");
        assertLine(result, NEBENKOSTEN, "300.00", "340.00", "-40.00");
        assertLine(result, VERSICHERUNGEN, "100.00", "240.00", "-140.00");
        assertLine(result, MOBILITAET, "60.00", "45.99", "14.01");
        assertLine(result, LEBENSMITTEL, "400.00", "420.50", "-20.50");
        assertLine(result, FREIZEIT, "150.00", "0.00", "150.00");
        assertLine(result, GESCHENKE, null, "20.00", null);
        assertLine(result, INVESTITIONEN, "600.00", "640.00", "-40.00");

        assertThat(result.expenses().target()).isEqualByComparingTo("2510.00");
        assertThat(result.expenses().actual()).isEqualByComparingTo("2606.49");
        assertThat(result.expenses().difference()).isEqualByComparingTo("-96.49");
        assertThat(result.unplanned()).isEqualByComparingTo("490.00");

        assertThat(result.surplus()).isEqualByComparingTo("593.51");
        assertThat(result.monthState()).isEqualTo(MonthState.CURRENT);
        assertThat(result.surplusLabel()).isEqualTo("Noch frei");

        assertThat(result.categories()).extracting(CategoryLine::id).doesNotContain(KREDITE);

        assertThat(result.income()).extracting(IncomeLine::id, IncomeLine::name).containsExactly(
                tuple(GEHALT, "Kategorie " + GEHALT),
                tuple(NEBENJOB, "Kategorie " + NEBENJOB));
        assertThat(income(result, GEHALT).actual()).isEqualByComparingTo("3000.00");
        assertThat(income(result, NEBENJOB).actual()).isEqualByComparingTo("200.00");

        assertThat(result.expenses().recurring()).isEqualByComparingTo("1140.00");
        assertThat(result.expenses().oneOff()).isEqualByComparingTo("1466.49");
    }

    // --- Helpers ---

    /**
     * October as a past month (today in November); Ē equals the income of October, so the cases from before
     * decision 26 keep their numbers. Cases about R23 pass their own average.
     */
    private DashboardResponse calculate(List<CategoryData> categories, List<TransactionData> transactions) {
        BigDecimal income = DashboardCalculator.incomeOf(transactions, OCTOBER);
        return calculate(categories, transactions,
                new Result(income, 12, YearMonth.of(2025, 10), YearMonth.of(2026, 9)), NOVEMBER);
    }

    private DashboardResponse calculate(List<CategoryData> categories, List<TransactionData> transactions,
            Result average, YearMonth currentMonth) {
        return calculate(categories, transactions, average, null, currentMonth);
    }

    private DashboardResponse calculate(List<CategoryData> categories, List<TransactionData> transactions,
            Result average, BigDecimal averageExpenses, YearMonth currentMonth) {
        return calculator.calculate(categories, transactions, OCTOBER, average, averageExpenses, currentMonth);
    }

    private static Result average(String amount) {
        return new Result(new BigDecimal(amount), 12, YearMonth.of(2025, 10), YearMonth.of(2026, 9));
    }

    private static CategoryData category(long id, String amount, String percent) {
        return new CategoryData(id, "Kategorie " + id, decimal(amount), decimal(percent));
    }

    private static CategoryData noTarget(long id) {
        return category(id, null, null);
    }

    /** Single transaction; signed amount: positive is income, negative is expense (R11). */
    private static TransactionData booking(long categoryId, String amount) {
        return booking(categoryId, amount, IN_OCTOBER);
    }

    private static TransactionData booking(long categoryId, String amount, LocalDate date) {
        return new TransactionData(categoryId, new BigDecimal(amount), date, false);
    }

    /** Occurrence of a recurring transaction (R12). */
    private static TransactionData occurrence(long categoryId, String amount) {
        return occurrence(categoryId, amount, IN_OCTOBER);
    }

    private static TransactionData occurrence(long categoryId, String amount, LocalDate date) {
        return new TransactionData(categoryId, new BigDecimal(amount), date, true);
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
