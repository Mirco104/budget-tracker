package io.github.mirco104.budget.plan;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rule P13 from docs/spezifikation.md, section 19: a price change of a planned item. No Spring dependencies.
 */
public final class PriceChange {

    private PriceChange() {
    }

    /**
     * The new amount when the last two fulfilling bookings (oldest first) have the same amount and it differs from
     * the amount of the item; null otherwise.
     */
    public static BigDecimal newAmount(BigDecimal itemAmount, List<BigDecimal> fulfilled) {
        if (fulfilled.size() < 2) {
            return null;
        }
        BigDecimal last = fulfilled.get(fulfilled.size() - 1);
        BigDecimal before = fulfilled.get(fulfilled.size() - 2);
        return last.compareTo(before) == 0 && last.compareTo(itemAmount) != 0 ? last : null;
    }
}
