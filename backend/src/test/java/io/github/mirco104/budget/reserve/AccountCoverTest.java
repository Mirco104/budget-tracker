package io.github.mirco104.budget.reserve;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ticket #145 (decision 100): what an account has to hold for its reserves and saving goals (P8, P11). */
class AccountCoverTest {

    @Test
    void theAccountHasToHoldItsReservesAndTheSavedAmountOfItsGoals() {
        assertThat(AccountCover.needed(new BigDecimal("100.00"), List.of(new BigDecimal("700.00"), new BigDecimal("50.00"))))
                .isEqualByComparingTo("850.00");
    }

    @Test
    void aGoalThatPaidOutMoreThanItGotNeedsNothing() {
        // A goal below 0 (paid from savings) does not lower what the reserves need
        assertThat(AccountCover.needed(new BigDecimal("100.00"), List.of(new BigDecimal("-300.00"))))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void missingIsWhatTheAppBalanceLacksAndNeverBelowZero() {
        BigDecimal missing = AccountCover.missing(new BigDecimal("850.00"), new BigDecimal("500.00"));
        assertThat(missing).isEqualByComparingTo("350.00");
        assertThat(missing.scale()).isEqualTo(2);
        assertThat(AccountCover.missing(new BigDecimal("850.00"), new BigDecimal("900.00"))).isEqualByComparingTo("0.00");
    }
}
