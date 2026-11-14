package io.github.mirco104.budget.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** P13: two fulfilling bookings in a row with the same other amount are a price change. */
class PriceChangeTest {

    private static final BigDecimal ITEM = new BigDecimal("-12.99");

    @Test
    void twoEqualOtherAmountsInARowAreTheNewPrice() {
        assertThat(PriceChange.newAmount(ITEM, List.of(new BigDecimal("-12.99"), new BigDecimal("-14.99"),
                new BigDecimal("-14.99")))).isEqualByComparingTo("-14.99");
    }

    @Test
    void aSingleExceptionIsNoPriceChange() {
        assertThat(PriceChange.newAmount(ITEM, List.of(new BigDecimal("-12.99"), new BigDecimal("-14.99")))).isNull();
        assertThat(PriceChange.newAmount(ITEM, List.of(new BigDecimal("-14.99"), new BigDecimal("-12.99")))).isNull();
        assertThat(PriceChange.newAmount(ITEM, List.of(new BigDecimal("-13.99"), new BigDecimal("-14.99")))).isNull();
        assertThat(PriceChange.newAmount(ITEM, List.of())).isNull();
    }

    @Test
    void theSameAmountAsTheItemIsNoChange() {
        assertThat(PriceChange.newAmount(ITEM, List.of(new BigDecimal("-12.990"), new BigDecimal("-12.99")))).isNull();
    }
}
