package io.github.mirco104.budget.bankaccount;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** P12: the lower limit of an account and the expected balance. */
class LowerLimitTest {

    @Test
    void theLimitFollowsItsMode() {
        assertThat(AccountBalance.limit(LowerLimitMode.OFF, new BigDecimal("500"))).isNull();
        assertThat(AccountBalance.limit(LowerLimitMode.ZERO, null)).isEqualByComparingTo("0");
        assertThat(AccountBalance.limit(LowerLimitMode.CUSTOM, new BigDecimal("500"))).isEqualByComparingTo("500");
        assertThat(AccountBalance.limit(LowerLimitMode.CUSTOM, null)).isNull();
    }

    @Test
    void theExpectedBalanceAddsTheOpenTermsOfTheMonth() {
        assertThat(AccountBalance.expected(new BigDecimal("800"), List.of(new BigDecimal("-900"), new BigDecimal("-20"))))
                .isEqualByComparingTo("-120.00");
        assertThat(AccountBalance.expected(new BigDecimal("800"), List.of())).isEqualByComparingTo("800.00");
    }

    @Test
    void belowIsStrictlyLessThanTheLimit() {
        assertThat(AccountBalance.below(new BigDecimal("-0.01"), BigDecimal.ZERO)).isTrue();
        assertThat(AccountBalance.below(BigDecimal.ZERO, BigDecimal.ZERO)).isFalse();
        assertThat(AccountBalance.below(new BigDecimal("-100"), null)).isFalse();
    }
}
