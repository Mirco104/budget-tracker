package io.github.mirco104.budget.category;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/**
 * The target in euro from the month onward; null means no target (R3). Percent is only an input helper in the
 * frontend since decision 36.
 */
public record UpdateTargetRequest(
        @DecimalMin(value = "0", message = "Der Soll-Betrag darf nicht negativ sein.")
        @Digits(integer = 10, fraction = 2, message = "Der Soll-Betrag darf höchstens 2 Nachkommastellen haben.")
        BigDecimal targetAmount) {
}
