package io.github.mirco104.budget.category;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/**
 * Both values may be null (no target). targetPercent is a fraction of income (0..1).
 */
public record UpdateTargetRequest(
        @DecimalMin(value = "0", message = "Der Soll-Betrag darf nicht negativ sein.")
        @Digits(integer = 10, fraction = 2, message = "Der Soll-Betrag darf höchstens 2 Nachkommastellen haben.")
        BigDecimal targetAmount,

        @DecimalMin(value = "0", message = "Der Prozentwert muss zwischen 0 und 100 % liegen.")
        @DecimalMax(value = "1", message = "Der Prozentwert muss zwischen 0 und 100 % liegen.")
        @Digits(integer = 1, fraction = 4, message = "Der Prozentwert darf höchstens 2 Nachkommastellen haben.")
        BigDecimal targetPercent) {
}
