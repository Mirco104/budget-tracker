package io.github.mirco104.budget.reserve;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Konten (P8, ticket #145, decision 101): what an account has to hold for the reserve pots and saving goals linked to
 * it, and what its app balance (P11) lacks for that. A goal counts with what it saved; one below 0 (paid out more than
 * it got) counts as 0, so it never lowers what the reserves need.
 */
public final class AccountCover {

    private AccountCover() {
    }

    /** The saved amount of the pots plus the saved amount of each goal, a goal at least 0. */
    public static BigDecimal needed(BigDecimal reserves, List<BigDecimal> goals) {
        BigDecimal sum = reserves;
        for (BigDecimal goal : goals) {
            sum = sum.add(goal.max(BigDecimal.ZERO));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** What the app balance lacks to cover the needed amount; 0 when it is covered. */
    public static BigDecimal missing(BigDecimal needed, BigDecimal balance) {
        return needed.subtract(balance).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
