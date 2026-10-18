package io.github.mirco104.budget.dashboard;

import static io.github.mirco104.budget.category.CategoryGroup.FIXED;
import static io.github.mirco104.budget.category.CategoryGroup.FLEXIBLE;
import static io.github.mirco104.budget.category.CategoryGroup.REMAINDER;
import static io.github.mirco104.budget.category.CategoryGroup.SAVINGS;
import static io.github.mirco104.budget.category.CategoryType.EXPENSE;
import static io.github.mirco104.budget.category.CategoryType.INCOME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.mirco104.budget.category.CategoryGroup;
import io.github.mirco104.budget.dashboard.DashboardCalculator.CategoryData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.TransactionData;
import io.github.mirco104.budget.dashboard.DashboardCalculator.YearlyTransactionData;
import io.github.mirco104.budget.dashboard.DashboardResponse.CategoryLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.DueLine;
import io.github.mirco104.budget.dashboard.DashboardResponse.IncomeLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rules R1–R9 from docs/spezifikation.md, section 2. Test cases 1–7 follow section 7.
 */
class DashboardCalculatorTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate IN_OCTOBER = LocalDate.of(2026, 10, 15);

    private static final long WOHNEN = 1;
    private static final long VERSICHERUNGEN = 2;
    private static final long LEBENSMITTEL = 3;
    private static final long FREIZEIT = 4;
    private static final long INVESTITIONEN = 5;
    private static final long REST = 6;
    private static final long GEHALT = 7;
    private static final long NEBENJOB = 8;

    private final DashboardCalculator calculator = new DashboardCalculator();

    // --- Case 1: euro target only (R3, R5) ---

    @Test
    void euroTargetIsUsedAndDifferenceIsTargetMinusActual() {
        DashboardResponse result = calculate(
                List.of(expense(WOHNEN, FIXED, "800", null), rest(), income(GEHALT)),
                List.of(booking(GEHALT, "2000"), booking(WOHNEN, "750")),
                List.of());

        CategoryLine wohnen = category(result, WOHNEN);
        assertThat(wohnen.target()).isEqualByComparingTo("800.00");
        assertThat(wohnen.actual()).isEqualByComparingTo("750.00");
        assertThat(wohnen.difference()).isEqualByComparingTo("50.00");
    }

    // --- Case 2: percent target only, income > 0 (R3) ---

    @Test
    void percentTargetIsShareOfIncome() {
        DashboardResponse result = calculate(
                List.of(expense(LEBENSMITTEL, FLEXIBLE, null, "0.15"), rest(), income(GEHALT)),
                List.of(booking(GEHALT, "2400")),
                List.of());

        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("360.00");
    }

    @Test
    void percentTargetIsRoundedHalfUpToCents() {
        DashboardResponse result = calculate(
                List.of(expense(LEBENSMITTEL, FLEXIBLE, null, "0.3333"), rest(), income(GEHALT)),
                List.of(booking(GEHALT, "100.15")),
                List.of());

        // 100.15 * 0.3333 = 33.380 -> 33.38
        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("33.38");
    }

    // --- Case 3: euro and percent set -> euro wins (R3) ---

    @Test
    void euroTargetWinsOverPercent() {
        DashboardResponse result = calculate(
                List.of(expense(INVESTITIONEN, SAVINGS, "250", "0.10"), rest(), income(GEHALT)),
                List.of(booking(GEHALT, "2400")),
                List.of());

        assertThat(category(result, INVESTITIONEN).target()).isEqualByComparingTo("250.00");
    }

    // --- Case 4: remainder becomes negative when over-planned (R4) ---

    @Test
    void remainderTargetIsIncomeMinusOtherTargets() {
        DashboardResponse result = calculate(
                List.of(expense(WOHNEN, FIXED, "800", null), expense(LEBENSMITTEL, FLEXIBLE, null, "0.15"),
                        rest(), income(GEHALT)),
                List.of(booking(GEHALT, "2400")),
                List.of());

        assertThat(category(result, REST).target()).isEqualByComparingTo("1240.00");
    }

    @Test
    void remainderTargetIsNegativeWhenOverPlanned() {
        DashboardResponse result = calculate(
                List.of(expense(WOHNEN, FIXED, "1500", null), expense(LEBENSMITTEL, FLEXIBLE, null, "0.40"),
                        rest(), income(GEHALT)),
                List.of(booking(GEHALT, "2000")),
                List.of());

        // 2000 - 1500 - 800
        assertThat(category(result, REST).target()).isEqualByComparingTo("-300.00");
        assertThat(result.expenses().target()).isEqualByComparingTo("2000.00");
    }

    // --- Case 5: income = 0 -> no division by zero (R8, R9) ---

    @Test
    void zeroIncomeAndZeroExpensesGiveEmptyChangeAndZeroShares() {
        DashboardResponse result = calculate(
                List.of(expense(LEBENSMITTEL, FLEXIBLE, null, "0.15"), rest(), income(GEHALT), income(NEBENJOB)),
                List.of(),
                List.of());

        assertThat(result.incomeTotal()).isEqualByComparingTo("0.00");
        assertThat(category(result, LEBENSMITTEL).target()).isEqualByComparingTo("0.00");
        assertThat(category(result, REST).target()).isEqualByComparingTo("0.00");
        assertThat(result.changePercent()).isNull();
        assertThat(result.changeLabel()).isNull();
        assertThat(result.surplus()).isEqualByComparingTo("0.00");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Überschuss");
        assertThat(result.income()).extracting(IncomeLine::share)
                .allSatisfy(share -> assertThat(share).isEqualByComparingTo("0"));
    }

    @Test
    void zeroIncomeWithExpensesGivesMinusHundredPercentChange() {
        DashboardResponse result = calculate(
                List.of(expense(FREIZEIT, FLEXIBLE, null, null), rest(), income(GEHALT)),
                List.of(booking(FREIZEIT, "100")),
                List.of());

        assertThat(result.changePercent()).isEqualByComparingTo("-1.0000");
        assertThat(result.changeLabel()).isEqualTo("Reduzierung der Gesamteinsparung");
        assertThat(result.income()).singleElement()
                .satisfies(line -> assertThat(line.share()).isEqualByComparingTo("0"));
    }

    // --- Case 6: yearly transaction counts as amount / 12 (R1) ---

    @Test
    void yearlyTransactionCountsAsTwelfth() {
        DashboardResponse result = calculate(
                List.of(expense(VERSICHERUNGEN, FIXED, null, null), rest(), income(GEHALT)),
                List.of(booking(VERSICHERUNGEN, "10")),
                List.of(yearly(VERSICHERUNGEN, "600")));

        assertThat(category(result, VERSICHERUNGEN).actual()).isEqualByComparingTo("60.00");
    }

    @Test
    void yearlyAmountsAreSummedPerCategoryBeforeRounding() {
        DashboardResponse result = calculate(
                List.of(expense(VERSICHERUNGEN, FIXED, null, null), rest(), income(GEHALT)),
                List.of(),
                List.of(yearly(VERSICHERUNGEN, "100"), yearly(VERSICHERUNGEN, "100")));

        // 200 / 12 = 16.666 -> 16.67 (rounding each item would give 8.33 + 8.33 = 16.66)
        assertThat(category(result, VERSICHERUNGEN).actual()).isEqualByComparingTo("16.67");
    }

    @Test
    void yearlyIncomeCountsTowardsIncomeTotal() {
        DashboardResponse result = calculate(
                List.of(rest(), income(GEHALT)),
                List.of(booking(GEHALT, "2000")),
                List.of(yearly(GEHALT, "1200")));

        assertThat(result.incomeTotal()).isEqualByComparingTo("2100.00");
    }

    @Test
    void monthlyShareIsTwelfthRoundedHalfUpToCents() {
        assertThat(DashboardCalculator.monthlyShare(new BigDecimal("600"))).isEqualByComparingTo("50.00");
        assertThat(DashboardCalculator.monthlyShare(new BigDecimal("100"))).isEqualByComparingTo("8.33");
        assertThat(DashboardCalculator.monthlyShare(new BigDecimal("0.06"))).isEqualByComparingTo("0.01");
        assertThat(DashboardCalculator.monthlyShare(new BigDecimal("100")).scale()).isEqualTo(2);
    }

    // --- Case 9: yearly share per category, yearly costs total, due debits (R12) ---

    @Test
    void showsYearlySharePerCategoryAndYearlyExpensesTotal() {
        DashboardResponse result = calculate(
                List.of(expense(WOHNEN, FIXED, null, null), expense(VERSICHERUNGEN, FIXED, null, null), rest(),
                        income(GEHALT)),
                List.of(booking(VERSICHERUNGEN, "10"), booking(WOHNEN, "700")),
                List.of(yearly(VERSICHERUNGEN, "600"), yearly(GEHALT, "1200")));

        assertThat(category(result, VERSICHERUNGEN).actual()).isEqualByComparingTo("60.00");
        assertThat(category(result, VERSICHERUNGEN).actualYearly()).isEqualByComparingTo("50.00");
        assertThat(category(result, WOHNEN).actualYearly()).isEqualByComparingTo("0.00");
        assertThat(income(result, GEHALT).actualYearly()).isEqualByComparingTo("100.00");
        // Income is not a cost
        assertThat(result.yearlyExpensesShare()).isEqualByComparingTo("50.00");
    }

    @Test
    void yearlySharePerCategoryMatchesItsActualRounding() {
        DashboardResponse result = calculate(
                List.of(expense(VERSICHERUNGEN, FIXED, null, null), rest(), income(GEHALT)),
                List.of(),
                List.of(yearly(VERSICHERUNGEN, "100"), yearly(VERSICHERUNGEN, "100")));

        assertThat(category(result, VERSICHERUNGEN).actualYearly()).isEqualByComparingTo("16.67");
        assertThat(result.yearlyExpensesShare()).isEqualByComparingTo("16.67");
    }

    @Test
    void yearlyDebitIsDueInTheCalendarMonthOfItsDueDateEveryYear() {
        List<YearlyTransactionData> yearly = List.of(
                yearlyDue(VERSICHERUNGEN, "600", YearMonth.of(2025, 1), null, LocalDate.of(2025, 10, 15)));

        DueLine due = dueIn(yearly, OCTOBER).getFirst();
        assertThat(due.description()).isEqualTo("Jahresposten " + VERSICHERUNGEN);
        assertThat(due.categoryName()).isEqualTo("Ausgabe " + VERSICHERUNGEN);
        assertThat(due.amountPerYear()).isEqualByComparingTo("600.00");
        assertThat(due.dueDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(dueIn(yearly, OCTOBER.plusMonths(1))).isEmpty();
    }

    @Test
    void yearlyDebitIsNotDueOutsideItsValidity() {
        List<YearlyTransactionData> yearly = List.of(
                yearlyDue(VERSICHERUNGEN, "600", YearMonth.of(2025, 1), OCTOBER.minusMonths(1),
                        LocalDate.of(2025, 10, 15)));

        assertThat(dueIn(yearly, OCTOBER)).isEmpty();
    }

    @Test
    void yearlyDebitOnLeapDayIsDueOnFebruary28InOtherYears() {
        List<YearlyTransactionData> yearly = List.of(
                yearlyDue(VERSICHERUNGEN, "600", YearMonth.of(2024, 1), null, LocalDate.of(2024, 2, 29)));

        assertThat(dueIn(yearly, YearMonth.of(2027, 2)).getFirst().dueDate()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void yearlyTransactionWithoutDueDateIsNeverDue() {
        assertThat(dueIn(List.of(yearly(VERSICHERUNGEN, "600")), OCTOBER)).isEmpty();
    }

    @Test
    void yearlyTransactionCountsInItsFirstAndLastValidMonth() {
        List<YearlyTransactionData> yearly = List.of(yearly(VERSICHERUNGEN, "1200", OCTOBER, OCTOBER.plusMonths(2)));

        assertThat(versicherungenActual(yearly, OCTOBER)).isEqualByComparingTo("100.00");
        assertThat(versicherungenActual(yearly, OCTOBER.plusMonths(2))).isEqualByComparingTo("100.00");
    }

    @Test
    void yearlyTransactionIsIgnoredBeforeValidFromAndAfterValidTo() {
        List<YearlyTransactionData> yearly = List.of(yearly(VERSICHERUNGEN, "1200", OCTOBER, OCTOBER.plusMonths(2)));

        assertThat(versicherungenActual(yearly, OCTOBER.minusMonths(1))).isEqualByComparingTo("0.00");
        assertThat(versicherungenActual(yearly, OCTOBER.plusMonths(3))).isEqualByComparingTo("0.00");
    }

    @Test
    void openEndedYearlyTransactionCountsInLaterYears() {
        List<YearlyTransactionData> yearly = List.of(yearly(VERSICHERUNGEN, "1200", OCTOBER, null));

        assertThat(versicherungenActual(yearly, OCTOBER.plusYears(3))).isEqualByComparingTo("100.00");
    }

    // --- Case 7: negative surplus -> "Monatlicher Verlust" (R7, R8) ---

    @Test
    void negativeSurplusIsLabelledAsLoss() {
        DashboardResponse result = calculate(
                List.of(expense(WOHNEN, FIXED, null, null), rest(), income(GEHALT)),
                List.of(booking(GEHALT, "1000"), booking(WOHNEN, "1250")),
                List.of());

        assertThat(result.surplus()).isEqualByComparingTo("-250.00");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Verlust");
        assertThat(result.changePercent()).isEqualByComparingTo("-0.2000");
        assertThat(result.changeLabel()).isEqualTo("Reduzierung der Gesamteinsparung");
    }

    // --- Further rules ---

    @Test
    void differenceIsEmptyWithoutTarget() {
        DashboardResponse result = calculate(
                List.of(expense(FREIZEIT, FLEXIBLE, null, null), rest(), income(GEHALT)),
                List.of(booking(FREIZEIT, "75.10")),
                List.of());

        CategoryLine freizeit = category(result, FREIZEIT);
        assertThat(freizeit.target()).isNull();
        assertThat(freizeit.actual()).isEqualByComparingTo("75.10");
        assertThat(freizeit.difference()).isNull();
    }

    @Test
    void transactionsOutsideTheMonthAreIgnored() {
        DashboardResponse result = calculate(
                List.of(expense(FREIZEIT, FLEXIBLE, null, null), rest(), income(GEHALT)),
                List.of(new TransactionData(FREIZEIT, new BigDecimal("10"), LocalDate.of(2026, 9, 30)),
                        new TransactionData(FREIZEIT, new BigDecimal("20"), LocalDate.of(2026, 10, 1)),
                        new TransactionData(FREIZEIT, new BigDecimal("30"), LocalDate.of(2026, 10, 31)),
                        new TransactionData(FREIZEIT, new BigDecimal("40"), LocalDate.of(2026, 11, 1))),
                List.of());

        assertThat(category(result, FREIZEIT).actual()).isEqualByComparingTo("50.00");
    }

    @Test
    void separatesExpenseAndIncomeLinesInInputOrder() {
        DashboardResponse result = calculate(
                List.of(expense(WOHNEN, FIXED, null, null), expense(FREIZEIT, FLEXIBLE, null, null), rest(),
                        income(GEHALT), income(NEBENJOB)),
                List.of(),
                List.of());

        assertThat(result.month()).isEqualTo("2026-10");
        assertThat(result.categories()).extracting(CategoryLine::id).containsExactly(WOHNEN, FREIZEIT, REST);
        assertThat(result.income()).extracting(IncomeLine::id).containsExactly(GEHALT, NEBENJOB);
    }

    @Test
    void emptyMonthGivesZeroActuals() {
        DashboardResponse result = calculate(
                List.of(expense(WOHNEN, FIXED, "800", null), rest(), income(GEHALT)),
                List.of(),
                List.of());

        assertThat(category(result, WOHNEN).actual()).isEqualByComparingTo("0.00");
        assertThat(category(result, WOHNEN).difference()).isEqualByComparingTo("800.00");
        assertThat(result.expenses().actual()).isEqualByComparingTo("0.00");
    }

    // --- Reference fixture (invented numbers, checked against the sheet) ---

    @Test
    void referenceFixtureMatchesSheet() {
        DashboardResponse result = calculate(
                List.of(
                        expense(WOHNEN, FIXED, "800", null),
                        expense(VERSICHERUNGEN, FIXED, null, null),
                        expense(LEBENSMITTEL, FLEXIBLE, null, "0.15"),
                        expense(FREIZEIT, FLEXIBLE, null, null),
                        expense(INVESTITIONEN, SAVINGS, "250", "0.10"),
                        rest(),
                        income(GEHALT),
                        income(NEBENJOB)),
                List.of(
                        booking(GEHALT, "2000.00"),
                        booking(NEBENJOB, "400.00"),
                        booking(WOHNEN, "800.00"),
                        booking(LEBENSMITTEL, "320.40"),
                        booking(FREIZEIT, "75.10"),
                        booking(INVESTITIONEN, "250.00"),
                        booking(REST, "30.00")),
                List.of(yearly(VERSICHERUNGEN, "600.00")));

        assertThat(result.incomeTotal()).isEqualByComparingTo("2400.00");

        assertLine(result, WOHNEN, "800.00", "800.00", "0.00");
        assertLine(result, VERSICHERUNGEN, null, "50.00", null);
        assertLine(result, LEBENSMITTEL, "360.00", "320.40", "39.60");
        assertLine(result, FREIZEIT, null, "75.10", null);
        assertLine(result, INVESTITIONEN, "250.00", "250.00", "0.00");
        assertLine(result, REST, "990.00", "30.00", "960.00");

        assertThat(result.expenses().target()).isEqualByComparingTo("2400.00");
        assertThat(result.expenses().actual()).isEqualByComparingTo("1525.50");
        assertThat(result.expenses().difference()).isEqualByComparingTo("874.50");

        assertThat(result.surplus()).isEqualByComparingTo("874.50");
        assertThat(result.surplusLabel()).isEqualTo("Monatlicher Überschuss");
        assertThat(result.changePercent()).isEqualByComparingTo("0.5733");
        assertThat(result.changeLabel()).isEqualTo("Anstieg der Gesamteinsparung");

        assertThat(result.income()).extracting(IncomeLine::id, IncomeLine::name).containsExactly(
                tuple(GEHALT, "Einnahme " + GEHALT),
                tuple(NEBENJOB, "Einnahme " + NEBENJOB));
        assertThat(income(result, GEHALT).actual()).isEqualByComparingTo("2000.00");
        assertThat(income(result, GEHALT).share()).isEqualByComparingTo("0.8333");
        assertThat(income(result, NEBENJOB).share()).isEqualByComparingTo("0.1667");
    }

    // --- Helpers ---

    private DashboardResponse calculate(
            List<CategoryData> categories, List<TransactionData> transactions,
            List<YearlyTransactionData> yearly) {
        return calculator.calculate(categories, transactions, yearly, OCTOBER);
    }

    private static CategoryData expense(long id, CategoryGroup group, String amount, String percent) {
        return new CategoryData(id, "Ausgabe " + id, EXPENSE, group, decimal(amount), decimal(percent), false);
    }

    private static CategoryData rest() {
        return new CategoryData(REST, "Sonstiges (berechneter Rest)", EXPENSE, REMAINDER, null, null, true);
    }

    private static CategoryData income(long id) {
        return new CategoryData(id, "Einnahme " + id, INCOME, null, null, null, false);
    }

    private static TransactionData booking(long categoryId, String amount) {
        return new TransactionData(categoryId, new BigDecimal(amount), IN_OCTOBER);
    }

    private BigDecimal versicherungenActual(List<YearlyTransactionData> yearly, YearMonth month) {
        DashboardResponse result = calculator.calculate(
                List.of(expense(VERSICHERUNGEN, FIXED, null, null), rest(), income(GEHALT)), List.of(), yearly, month);
        return category(result, VERSICHERUNGEN).actual();
    }

    // Valid from the tested month onward, open-ended
    private static YearlyTransactionData yearly(long categoryId, String amountPerYear) {
        return yearly(categoryId, amountPerYear, OCTOBER, null);
    }

    private static YearlyTransactionData yearly(
            long categoryId, String amountPerYear, YearMonth validFrom, YearMonth validTo) {
        return yearlyDue(categoryId, amountPerYear, validFrom, validTo, null);
    }

    private static YearlyTransactionData yearlyDue(
            long categoryId, String amountPerYear, YearMonth validFrom, YearMonth validTo, LocalDate dueDate) {
        return new YearlyTransactionData(categoryId * 100, categoryId, "Jahresposten " + categoryId,
                new BigDecimal(amountPerYear), dueDate, validFrom, validTo);
    }

    private List<DueLine> dueIn(List<YearlyTransactionData> yearly, YearMonth month) {
        return calculator.calculate(
                List.of(expense(VERSICHERUNGEN, FIXED, null, null), rest(), income(GEHALT)), List.of(), yearly, month)
                .dueYearly();
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
