package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.plan.RhythmUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outlook from a month on (R14, spec section 5): the reserve pot and the forecast. monthlyReserve and
 * reserveBalance sum the reserve pot. forecast has exactly 12 months (the
 * month and the following 11). Amounts in reserves and forecast are costs, so positive. The look back is in {@link HistoryResponse} (decision 29).
 */
public record ReserveResponse(
        String month,
        BigDecimal monthlyReserve,
        BigDecimal reserveBalance,
        List<ReserveLine> reserves,
        List<ForecastMonth> forecast) {

    /**
     * A rare expense in the pot: nextDate is its next occurrence, saved what should be put aside by now, ratesLeft
     * the monthly rates still to put aside until it is due (0 in the month it is due, decision 37).
     */
    public record ReserveLine(
            long itemId, String description, String categoryName, BigDecimal amount, RhythmUnit rhythmUnit, int rhythmCount,
            LocalDate nextDate, BigDecimal monthly, BigDecimal saved, int ratesLeft) {
    }

    /** fromReserves is the part of total paid from the reserve pot (decision 37). */
    public record ForecastMonth(String month, BigDecimal total, BigDecimal fromReserves, List<ForecastEntry> entries) {
    }

    /**
     * A single expense (kind SINGLE, id) or a term of a planned item (kind PLANNED, itemId); fromReserves for
     * a term of an item with a spacing over one month, which the reserve pot pays (decision 37).
     */
    public record ForecastEntry(
            Kind kind, Long id, Long itemId, String description, LocalDate date, BigDecimal amount,
            boolean fromReserves) {
    }

    /** A booking or a term of a planned item. */
    public enum Kind {
        SINGLE, PLANNED
    }
}
