package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.recurring.RecurrenceSchedule;
import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastMonth;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastOccurrence;
import io.github.mirco104.budget.reserve.ReserveResponse.SeriesLine;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Reserve analysis, rule R14 from docs/spezifikation.md, section 2. No Spring dependencies.
 * Only series with a negative amount count; single transactions are not part of the input.
 */
public class ReserveCalculator {

    static final int FORECAST_MONTHS = 12;

    private static final int MONEY_SCALE = 2;

    /** One series (R12); endDate is inclusive and may be null, exceptions are suspended occurrences (R13). */
    public record SeriesData(
            long id, String description, String categoryName, BigDecimal amount, RecurringInterval interval,
            LocalDate startDate, LocalDate endDate, Set<LocalDate> exceptions) {
    }

    public ReserveResponse calculate(List<SeriesData> series, YearMonth month) {
        List<SeriesData> expenses = series.stream()
                .filter(s -> s.amount().signum() < 0)
                .toList();
        List<SeriesData> active = expenses.stream()
                .filter(s -> isActiveIn(s, month))
                .toList();

        // Round only the sums, not each series (spec section 12, "Rundung")
        BigDecimal averageMonthlyCosts = sumOfMonthlyCosts(active);
        BigDecimal reserve = sumOfMonthlyCosts(active.stream()
                .filter(s -> s.interval().months() > 1)
                .toList());

        List<SeriesLine> lines = active.stream()
                .map(s -> new SeriesLine(
                        s.id(), s.description(), s.categoryName(), s.amount(), s.interval(),
                        RecurrenceSchedule.monthlyEquivalent(s.amount(), s.interval())))
                .toList();
        List<ForecastMonth> forecast = IntStream.range(0, FORECAST_MONTHS)
                .mapToObj(i -> forecastMonth(expenses, month.plusMonths(i)))
                .toList();
        return new ReserveResponse(month.toString(), averageMonthlyCosts, reserve, lines, forecast);
    }

    // R14: start not after the last day of the month, end (if set) not before the first day
    private static boolean isActiveIn(SeriesData s, YearMonth month) {
        return !s.startDate().isAfter(month.atEndOfMonth())
                && (s.endDate() == null || !s.endDate().isBefore(month.atDay(1)));
    }

    private static BigDecimal sumOfMonthlyCosts(List<SeriesData> series) {
        return series.stream()
                .map(s -> s.amount().abs().divide(BigDecimal.valueOf(s.interval().months()), MathContext.DECIMAL64))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static ForecastMonth forecastMonth(List<SeriesData> expenses, YearMonth month) {
        List<ForecastOccurrence> occurrences = expenses.stream()
                .flatMap(s -> new RecurrenceSchedule(s.startDate(), s.endDate(), s.interval(), s.exceptions())
                        .occurrencesBetween(month.atDay(1), month.atEndOfMonth()).stream()
                        .map(date -> new ForecastOccurrence(s.id(), s.description(), date, s.amount().abs())))
                .sorted(Comparator.comparing(ForecastOccurrence::date).thenComparing(ForecastOccurrence::recurringId))
                .toList();
        BigDecimal total = occurrences.stream()
                .map(ForecastOccurrence::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new ForecastMonth(month.toString(), total, occurrences);
    }
}
