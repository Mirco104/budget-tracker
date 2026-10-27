package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.recurring.RecurrenceSchedule;
import io.github.mirco104.budget.recurring.RecurringInterval;
import io.github.mirco104.budget.reserve.ReserveResponse.CategoryAmount;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastEntry;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastMonth;
import io.github.mirco104.budget.reserve.ReserveResponse.HistoryMonth;
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

    /**
     * Income E and expense actual A of a past month (R2), with the income per category (N(k) > 0) and the
     * expenses per category (I(k) > 0).
     */
    public record PastMonth(
            YearMonth month, BigDecimal income, BigDecimal expenses,
            List<CategoryAmount> incomeCategories, List<CategoryAmount> expenseCategories) {
    }

    /**
     * pastMonths holds months before M (missing months count as 0); firstEntryMonth is the month of the
     * earliest transaction or series start, null without any entry.
     */
    public ReserveResponse calculate(List<SeriesData> series, List<SingleData> singles,
            List<PastMonth> pastMonths, YearMonth firstEntryMonth, YearMonth month) {
        // Average and look back: M-12 to M-1, but not before the first entry
        YearMonth from = month.minusMonths(AVERAGE_MONTHS);
        if (firstEntryMonth != null && firstEntryMonth.isAfter(from)) {
            from = firstEntryMonth;
        }
        int averageMonths = firstEntryMonth == null ? 0 : (int) Math.max(0, ChronoUnit.MONTHS.between(from, month));
        YearMonth first = from;
        List<HistoryMonth> history = IntStream.range(0, averageMonths)
                .mapToObj(i -> historyMonth(pastMonths, first.plusMonths(i)))
                .toList();
        BigDecimal averageIncome = average(history.stream().map(HistoryMonth::income), averageMonths);
        BigDecimal averageExpenses = average(history.stream().map(HistoryMonth::expenses), averageMonths);
        BigDecimal averageSurplus = average(history.stream().map(HistoryMonth::surplus), averageMonths);

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
        return new ReserveResponse(month.toString(), averageIncome, averageExpenses, averageSurplus, averageMonths,
                history, money(monthlyReserve), money(reserveBalance), reserves, forecast);
    }

    // Surplus E - A as in R7; a month without entries has 0 everywhere
    private static HistoryMonth historyMonth(List<PastMonth> pastMonths, YearMonth month) {
        return pastMonths.stream()
                .filter(m -> m.month().equals(month))
                .findFirst()
                .map(m -> new HistoryMonth(month.toString(), money(m.income()), money(m.expenses()),
                        money(m.income().subtract(m.expenses())), m.incomeCategories(), m.expenseCategories()))
                .orElseGet(() -> new HistoryMonth(month.toString(), money(BigDecimal.ZERO), money(BigDecimal.ZERO),
                        money(BigDecimal.ZERO), List.of(), List.of()));
    }

    private static BigDecimal average(Stream<BigDecimal> values, int months) {
        if (months == 0) {
            return null;
        }
        return values.reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(months), MONEY_SCALE, RoundingMode.HALF_UP);
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
