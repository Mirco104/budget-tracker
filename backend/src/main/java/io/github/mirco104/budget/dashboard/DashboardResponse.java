package io.github.mirco104.budget.dashboard;

import io.github.mirco104.budget.reserve.ReserveCalculator;
import java.math.BigDecimal;
import java.util.List;

/**
 * Calculated dashboard for one month (spec section 5). Fields without a value are null.
 * difference is the deviation from the target (R5, R6), unplanned is Ē(M) minus the targets and unplannedMinimum
 * Emin(M) minus the targets (R4, decision 36). averageIncome is Ē(M), minimumIncome is Emin(M) and averageExpenses
 * is Ā(M), all over averageIncomeMonths months from averageIncomeFrom to averageIncomeTo (R23), all null (months 0)
 * when no month counts. monthState tells past, current and future
 * months apart (R7). categories[].actual is the expense actual I(k), income[].actual is the net N(k) (R2, R9).
 * expenses.recurring is W, expenses.oneOff is A - W (R10). All amounts are in euros (decision 34).
 */
public record DashboardResponse(
        String month,
        MonthState monthState,
        BigDecimal incomeTotal,
        BigDecimal averageIncome,
        BigDecimal minimumIncome,
        BigDecimal averageExpenses,
        int averageIncomeMonths,
        String averageIncomeFrom,
        String averageIncomeTo,
        Totals expenses,
        BigDecimal unplanned,
        String unplannedLabel,
        BigDecimal unplannedMinimum,
        String unplannedMinimumLabel,
        BigDecimal surplus,
        String surplusLabel,
        List<CategoryLine> categories,
        List<IncomeLine> income,
        SavingCard saving,
        ExpectedCard expected,
        Totals budget,
        List<ReserveCalculator.Pot> reserves,
        BigDecimal fromReserves) {

    /** The cards Sparen and Erwartet (section 19, AP 27d); the calculator leaves them empty. */
    public DashboardResponse withCards(SavingCard savingCard, ExpectedCard expectedCard) {
        return new DashboardResponse(month, monthState, incomeTotal, averageIncome, minimumIncome, averageExpenses,
                averageIncomeMonths, averageIncomeFrom, averageIncomeTo, expenses, unplanned, unplannedLabel,
                unplannedMinimum, unplannedMinimumLabel, surplus, surplusLabel, categories, income, savingCard,
                expectedCard, budget, reserves, fromReserves);
    }

    /**
     * P8: the budget lines with the reserve pots: categoryLines and budgetTotals count the monthly share of a rare
     * item instead of its payment; the tiles (expenses, incomeTotal) keep the real bookings (P1). paidFromReserves is
     * the part of the expenses of the month paid from reserve pots or saving goals (27f4, null when nothing).
     */
    public DashboardResponse withBudget(
            List<CategoryLine> categoryLines, Totals budgetTotals, List<ReserveCalculator.Pot> pots,
            BigDecimal paidFromReserves) {
        return new DashboardResponse(month, monthState, incomeTotal, averageIncome, minimumIncome, averageExpenses,
                averageIncomeMonths, averageIncomeFrom, averageIncomeTo, expenses, unplanned, unplannedLabel,
                unplannedMinimum, unplannedMinimumLabel, surplus, surplusLabel, categoryLines, income, saving,
                expected, budgetTotals, pots, paidFromReserves);
    }

    /** P9: planned saving of the month (monthly shares), saving rate in percent (null without planned income). */
    public record SavingCard(BigDecimal planned, BigDecimal rate, List<SavingLine> items, BigDecimal actual) {
    }

    /** A saving item: its monthly share and what its bookings brought in the month (27f3). */
    public record SavingLine(long itemId, String name, BigDecimal monthly, BigDecimal actual) {
    }

    /** P5: terms of the month without a booking yet (expected or missed) and their sum with sign. */
    public record ExpectedCard(int count, BigDecimal total) {
    }


    /** The month compared with the month of today (R7). */
    public enum MonthState {
        PAST, CURRENT, FUTURE
    }

    public record Totals(
            BigDecimal target, BigDecimal actual, BigDecimal difference, BigDecimal recurring, BigDecimal oneOff) {
    }

    public record CategoryLine(long id, String name, BigDecimal target, BigDecimal actual, BigDecimal difference) {
    }

    public record IncomeLine(long id, String name, BigDecimal actual) {
    }
}
