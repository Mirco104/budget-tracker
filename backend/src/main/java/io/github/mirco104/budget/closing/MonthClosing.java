package io.github.mirco104.budget.closing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/**
 * Rule P10 and decision 65 from docs/spezifikation.md, section 19: what is left of a month and whether the bell asks
 * which saving goal gets it. No Spring dependencies.
 */
public final class MonthClosing {

    private MonthClosing() {
    }

    /** P10: Übrig = income − expenses − saving of the month (actuals, P1, expenses without sign). */
    public static BigDecimal leftover(BigDecimal income, BigDecimal expenses, BigDecimal saving) {
        return income.subtract(expenses).subtract(saving).setScale(2, RoundingMode.HALF_UP);
    }

    /** Decision 65: the bell asks only about the month before the current one. */
    public static YearMonth monthToAsk(YearMonth current) {
        return current.minusMonths(1);
    }

    /**
     * P10: the bell asks when the leftover is positive, the month is not closed yet and lies from the month of the
     * first booking on (firstEntryMonth null without any booking).
     */
    public static boolean asks(BigDecimal leftover, boolean closed, YearMonth month, YearMonth firstEntryMonth) {
        return leftover.signum() > 0 && !closed && firstEntryMonth != null && !month.isBefore(firstEntryMonth);
    }
}
