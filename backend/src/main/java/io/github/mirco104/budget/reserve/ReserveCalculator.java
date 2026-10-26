package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.recurring.RecurrenceSchedule;
import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastEntry;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastMonth;
import io.github.mirco104.budget.reserve.ReserveResponse.ReserveLine;
import io.github.mirco104.budget.transaction.TransactionResponse.Kind;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Analysis of a month, rule R14 from docs/spezifikation.md, section 2. No Spring dependencies.
 * Rounds only the end results, not the parts they are summed from (spec section 12, "Rundung").
 */
public class ReserveCalculator {

    static final int FORECAST_MONTHS = 12;
    static final int AVERAGE_MONTHS = 12;

    private static final int MONEY_SCALE = 2;

    /** One series (R12); endDate is inclusive and may be null, exceptions are suspended occurrences (R13). */
    public record SeriesData(
            long id, String description, String categoryName, BigDecimal amount, RecurringInterval interval,
            LocalDate startDate, LocalDate endDate, Set<LocalDate> exceptions) {
    }

    /** A single transaction; amount is signed (R11). */
    public record SingleData(long id, String description, LocalDate date, BigDecimal amount) {
    }

    /** Expense actual A of a month (R2). */
    public record MonthExpenses(YearMonth month, BigDecimal expenses) {
    }

    /**
     * pastMonths holds A for months before M (missing months count as 0); firstEntryMonth is the month of the
     * earliest transaction or series start, null without any entry.
     */
    public ReserveResponse calculate(List<SeriesData> series, List<SingleData> singles,
            List<MonthExpenses> pastMonths, YearMonth firstEntryMonth, YearMonth month) {
        // Average: M-12 to M-1, but not before the first entry
        YearMonth from = month.minusMonths(AVERAGE_MONTHS);
        if (firstEntryMonth != null && firstEntryMonth.isAfter(from)) {
            from = firstEntryMonth;
        }
        int averageMonths = firstEntryMonth == null ? 0 : (int) Math.max(0, ChronoUnit.MONTHS.between(from, month));
        BigDecimal averageExpenses = null;
        if (averageMonths > 0) {
            YearMonth first = from;
            BigDecimal sum = pastMonths.stream()
                    .filter(m -> !m.month().isBefore(first) && m.month().isBefore(month))
                    .map(MonthExpenses::expenses)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            averageExpenses = sum.divide(BigDecimal.valueOf(averageMonths), MONEY_SCALE, RoundingMode.HALF_UP);
        }

        LocalDate forecastEnd = month.plusMonths(FORECAST_MONTHS - 1).atEndOfMonth();
        List<SeriesData> expenseSeries = series.stream()
                .filter(s -> s.amount().signum() < 0)
                .toList();

        List<ReserveLine> reserves = new ArrayList<>();
        BigDecimal monthlyReserve = BigDecimal.ZERO;
        BigDecimal reserveBalance = BigDecimal.ZERO;
        for (SeriesData s : expenseSeries) {
            int interval = s.interval().months();
            Optional<LocalDate> next = interval > 1
                    ? schedule(s).occurrencesBetween(month.atDay(1), forecastEnd).stream().findFirst()
                    : Optional.empty();
            if (next.isEmpty()) {
                continue;
            }
            long monthsUntilDue = ChronoUnit.MONTHS.between(month, YearMonth.from(next.get()));
            BigDecimal cost = s.amount().abs();
            BigDecimal monthly = cost.divide(BigDecimal.valueOf(interval), MathContext.DECIMAL64);
            BigDecimal saved = monthly.multiply(BigDecimal.valueOf(Math.max(0, interval - monthsUntilDue)));
            monthlyReserve = monthlyReserve.add(monthly);
            reserveBalance = reserveBalance.add(saved);
            reserves.add(new ReserveLine(
                    s.id(), s.description(), s.categoryName(), cost, s.interval(), next.get(),
                    money(monthly), money(saved)));
        }
        reserves.sort(Comparator.comparing(ReserveLine::nextDate).thenComparing(ReserveLine::recurringId));

        List<ForecastMonth> forecast = IntStream.range(0, FORECAST_MONTHS)
                .mapToObj(i -> forecastMonth(expenseSeries, singles, month.plusMonths(i)))
                .toList();
        return new ReserveResponse(month.toString(), averageExpenses, averageMonths,
                money(monthlyReserve), money(reserveBalance), reserves, forecast);
    }

    private static RecurrenceSchedule schedule(SeriesData s) {
        return new RecurrenceSchedule(s.startDate(), s.endDate(), s.interval(), s.exceptions());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static ForecastMonth forecastMonth(List<SeriesData> expenseSeries, List<SingleData> singles,
            YearMonth month) {
        Stream<ForecastEntry> occurrences = expenseSeries.stream()
                .flatMap(s -> schedule(s).occurrencesBetween(month.atDay(1), month.atEndOfMonth()).stream()
                        .map(date -> new ForecastEntry(
                                Kind.RECURRING, null, s.id(), s.description(), date, s.amount().abs())));
        Stream<ForecastEntry> singleExpenses = singles.stream()
                .filter(t -> t.amount().signum() < 0 && YearMonth.from(t.date()).equals(month))
                .map(t -> new ForecastEntry(Kind.SINGLE, t.id(), null, t.description(), t.date(), t.amount().abs()));
        List<ForecastEntry> entries = Stream.concat(occurrences, singleExpenses)
                .sorted(Comparator.comparing(ForecastEntry::date)
                        .thenComparing(ForecastEntry::kind)
                        .thenComparing(ForecastEntry::description))
                .toList();
        BigDecimal total = entries.stream()
                .map(ForecastEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ForecastMonth(month.toString(), money(total), entries);
    }
}
