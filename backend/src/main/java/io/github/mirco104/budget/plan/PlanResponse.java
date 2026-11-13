package io.github.mirco104.budget.plan;

import java.math.BigDecimal;
import java.util.List;

/**
 * The plan of one month (P2–P4, P9). All sums are monthly shares without sign; unplanned is negative when the month
 * is overplanned. averageIncome and minimumIncome are Ē(M) and Emin(M) (R23), only for orientation, null without a
 * month that counts. categories has one line per group and category with its sum; items are the versions valid in the
 * month.
 */
public record PlanResponse(
        String month,
        BigDecimal income,
        BigDecimal fixed,
        BigDecimal budgets,
        BigDecimal saving,
        BigDecimal unplanned,
        BigDecimal savingRate,
        BigDecimal planTotal,
        BigDecimal averageIncome,
        BigDecimal minimumIncome,
        int averageMonths,
        // 27f4: the months of Ē(M) and Emin(M), null without one
        String averageFrom,
        String averageTo,
        // 27f4: the planned income is higher than Emin(M); false without Emin(M)
        boolean incomeAboveMinimum,
        List<CategoryTotal> categories,
        List<PlannedItemResponse> items) {

    /** Sum of the items of one category in one group (fixed costs or budgets). */
    public record CategoryTotal(PlanCalculator.Group group, long categoryId, String categoryName, BigDecimal total) {
    }
}
