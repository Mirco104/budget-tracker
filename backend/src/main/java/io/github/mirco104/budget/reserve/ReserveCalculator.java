package io.github.mirco104.budget.reserve;

import io.github.mirco104.budget.plan.RhythmUnit;
import io.github.mirco104.budget.plan.TermSchedule;
import io.github.mirco104.budget.reserve.HistoryResponse.CategoryAmount;
import io.github.mirco104.budget.reserve.HistoryResponse.HistoryMonth;
import io.github.mirco104.budget.reserve.HistoryResponse.Total;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastEntry;
import io.github.mirco104.budget.reserve.ReserveResponse.ForecastMonth;
import io.github.mirco104.budget.reserve.ReserveResponse.ReserveLine;
import io.github.mirco104.budget.reserve.ReserveResponse.Kind;
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
 * Analysis, rule R14 from docs/spezifikation.md, section 2: the look back over a period of past months and the
 * outlook (reserve pot and forecast) from a month on. No Spring dependencies.
 * Rounds only the end results, not the parts they are summed from (spec section 12, "Rundung").
 */
public class ReserveCalculator {

    static final int FORECAST_MONTHS = 12;
    /** The longest look back in months (R14). */
    static final int MAX_PERIOD = 120;

    private static final int MONEY_SCALE = 2;

    /**
     * One planned item version with a rhythm (P5); handled are its term dates that already have a booking or are
     * skipped, so they are not forecast twice (P1).
     */
    public record SeriesData(
            long id, String description, String categoryName, BigDecimal amount, RhythmUnit unit, int count,
            TermSchedule schedule, Set<LocalDate> handled) {

        /** P8: a rare item has a spacing over one month. */
        boolean rare() {
            return unit == RhythmUnit.MONTH && count > 1;
        }
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
            List<CategoryData> incomeCategories, List<CategoryData> expenseCategories) {
    }

    /** The income N(k) or the expense actual I(k) of a category in a past month, positive, with its icon. */
    public record CategoryData(long id, String name, String icon, BigDecimal amount) {
    }

    /**
     * Look back from month {@code from} to month {@code to}, both included. Only months from the first entry on count
     * (firstEntryMonth: month of the earliest transaction or series start, null without any entry). pastMonths may
     * hold any months; missing months count as 0. Checking the period against today is up to the caller.
     */
    public HistoryResponse history(List<PastMonth> pastMonths, YearMonth firstEntryMonth, YearMonth from, YearMonth to) {
        YearMonth first = firstEntryMonth != null && firstEntryMonth.isAfter(from) ? firstEntryMonth : from;
        int months = firstEntryMonth == null || first.isAfter(to) ? 0 : (int) ChronoUnit.MONTHS.between(first, to) + 1;
        List<HistoryMonth> history = IntStream.range(0, months)
                .mapToObj(i -> historyMonth(pastMonths, first.plusMonths(i)))
                .toList();
        Total total = months == 0 ? null : new Total(
                sum(history.stream().map(HistoryMonth::income)),
                sum(history.stream().map(HistoryMonth::expenses)),
                sum(history.stream().map(HistoryMonth::surplus)));
        return new HistoryResponse(from.toString(), to.toString(), months,
                average(history.stream().map(HistoryMonth::income), months),
                average(history.stream().map(HistoryMonth::expenses), months),
                average(history.stream().map(HistoryMonth::surplus), months),
                total, history);
    }

    /** Reserve pot and forecast from the month on (the current month in the app, R14). */
    public ReserveResponse outlook(List<SeriesData> series, List<SingleData> singles, YearMonth month) {
        LocalDate forecastEnd = month.plusMonths(FORECAST_MONTHS - 1).atEndOfMonth();
        List<SeriesData> expenseSeries = series.stream()
                .filter(s -> s.amount().signum() < 0)
                .toList();

        List<ReserveLine> reserves = new ArrayList<>();
        BigDecimal monthlyReserve = BigDecimal.ZERO;
        BigDecimal reserveBalance = BigDecimal.ZERO;
        for (SeriesData s : expenseSeries) {
            int interval = s.count();
            Optional<LocalDate> next = s.rare()
                    ? s.schedule().termsBetween(month.atDay(1), forecastEnd).stream()
                            .filter(date -> !s.handled().contains(date)).findFirst()
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
                    s.id(), s.description(), s.categoryName(), cost, s.unit(), s.count(), next.get(),
                    money(monthly), money(saved), (int) Math.min(interval, monthsUntilDue)));
        }
        reserves.sort(Comparator.comparing(ReserveLine::nextDate).thenComparing(ReserveLine::itemId));

        List<ForecastMonth> forecast = IntStream.range(0, FORECAST_MONTHS)
                .mapToObj(i -> forecastMonth(expenseSeries, singles, month.plusMonths(i)))
                .toList();
        return new ReserveResponse(month.toString(), money(monthlyReserve), money(reserveBalance), reserves, forecast);
    }

    // Surplus E - A as in R7; a month without entries has 0 everywhere
    private static HistoryMonth historyMonth(List<PastMonth> pastMonths, YearMonth month) {
        PastMonth m = pastMonths.stream()
                .filter(past -> past.month().equals(month))
                .findFirst()
                .orElseGet(() -> new PastMonth(month, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of()));
        BigDecimal income = m.income();
        BigDecimal surplus = income.subtract(m.expenses());
        return new HistoryMonth(month.toString(), money(income), money(m.expenses()), money(surplus),
                categoryAmounts(m.incomeCategories()), categoryAmounts(m.expenseCategories()));
    }

    private static List<CategoryAmount> categoryAmounts(List<CategoryData> categories) {
        return categories.stream()
                .map(c -> new CategoryAmount(c.id(), c.name(), c.icon(), c.amount()))
                .toList();
    }

    private static BigDecimal average(Stream<BigDecimal> values, int months) {
        if (months == 0) {
            return null;
        }
        return values.reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(months), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(Stream<BigDecimal> values) {
        return money(values.reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static ForecastMonth forecastMonth(List<SeriesData> expenseSeries, List<SingleData> singles,
            YearMonth month) {
        Stream<ForecastEntry> occurrences = expenseSeries.stream()
                .flatMap(s -> s.schedule().termsBetween(month.atDay(1), month.atEndOfMonth()).stream()
                        .filter(date -> !s.handled().contains(date))
                        .map(date -> new ForecastEntry(
                                Kind.PLANNED, null, s.id(), s.description(), date, s.amount().abs(), s.rare())));
        Stream<ForecastEntry> singleExpenses = singles.stream()
                .filter(t -> t.amount().signum() < 0 && YearMonth.from(t.date()).equals(month))
                .map(t -> new ForecastEntry(
                        Kind.SINGLE, t.id(), null, t.description(), t.date(), t.amount().abs(), false));
        List<ForecastEntry> entries = Stream.concat(occurrences, singleExpenses)
                .sorted(Comparator.comparing(ForecastEntry::date)
                        .thenComparing(ForecastEntry::kind)
                        .thenComparing(ForecastEntry::description))
                .toList();
        BigDecimal total = entries.stream()
                .map(ForecastEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fromReserves = entries.stream()
                .filter(ForecastEntry::fromReserves)
                .map(ForecastEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ForecastMonth(month.toString(), money(total), money(fromReserves), entries);
    }
}
