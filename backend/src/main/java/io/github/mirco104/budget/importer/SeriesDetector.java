package io.github.mirco104.budget.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Monthly series among the new rows of an import, rule R31 from docs/spezifikation.md, section 2. No Spring
 * dependencies. A payment may move a few days around its day of the month (weekends, bank holidays).
 */
public final class SeriesDetector {

    /** Days a row may be away from the day of the month of the series (like R28). */
    static final int MAX_DAYS = 5;
    /** Largest difference to the median amount as a share of it (like R28). */
    static final BigDecimal MAX_SHARE = new BigDecimal("0.20");
    /** Months in a row a series needs. */
    static final int MIN_MONTHS = 3;

    /** A new row: key is the key of R29 (empty: the row is left out), amount is signed. */
    public record Row(int index, LocalDate date, BigDecimal amount, String key, String description) {
    }

    /**
     * A suggested monthly series: the rows it covers (by index, oldest first), its first occurrence, and the amount and
     * description of its latest row.
     */
    public record Suggestion(List<Integer> indices, LocalDate startDate, BigDecimal amount, String description) {
    }

    private SeriesDetector() {
    }

    /** R31: at most one series per key and sign, the longest run of months; ordered by the first row. */
    public static List<Suggestion> detect(List<Row> rows) {
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.key() != null && !row.key().isBlank() && row.amount().signum() != 0) {
                groups.computeIfAbsent(row.key() + "|" + row.amount().signum(), k -> new ArrayList<>()).add(row);
            }
        }
        List<Suggestion> result = new ArrayList<>();
        for (List<Row> group : groups.values()) {
            List<Row> similar = similarAmounts(group);
            if (similar.size() < MIN_MONTHS) {
                continue;
            }
            Run best = null;
            // Each day of the month that a row has is a candidate: the longest run wins, then the day most rows have
            // exactly, then the day of the latest row
            List<Row> newestFirst = similar.stream().sorted(Comparator.comparing(Row::date).reversed()).toList();
            for (Row candidate : newestFirst) {
                Run run = longestRun(similar, candidate.date().getDayOfMonth());
                if (run != null && (best == null || run.suggestion().indices().size() > best.suggestion().indices().size()
                        || run.suggestion().indices().size() == best.suggestion().indices().size()
                                && run.exact() > best.exact())) {
                    best = run;
                }
            }
            if (best != null) {
                result.add(best.suggestion());
            }
        }
        result.sort(Comparator.comparing(Suggestion::startDate));
        return result;
    }

    private record Run(Suggestion suggestion, long exact) {
    }

    // Rows within 20 % of the median amount of the group
    private static List<Row> similarAmounts(List<Row> group) {
        List<BigDecimal> amounts = group.stream().map(Row::amount).sorted().toList();
        BigDecimal median = amounts.get(amounts.size() / 2);
        BigDecimal limit = median.abs().multiply(MAX_SHARE);
        return group.stream()
                .filter(row -> row.amount().subtract(median).abs().compareTo(limit) <= 0)
                .sorted(Comparator.comparing(Row::date).thenComparingInt(Row::index))
                .toList();
    }

    // Each row goes to the month whose due date (the day, or the last day of a shorter month) is at most 5 days away;
    // the longest run of months in a row with exactly one row each
    private static Run longestRun(List<Row> rows, int day) {
        TreeMap<YearMonth, List<Row>> byMonth = new TreeMap<>();
        for (Row row : rows) {
            YearMonth own = YearMonth.from(row.date());
            for (YearMonth month : List.of(own.minusMonths(1), own, own.plusMonths(1))) {
                if (Math.abs(ChronoUnit.DAYS.between(row.date(), due(month, day))) <= MAX_DAYS) {
                    byMonth.computeIfAbsent(month, m -> new ArrayList<>()).add(row);
                    break;
                }
            }
        }
        List<YearMonth> best = List.of();
        List<YearMonth> current = new ArrayList<>();
        for (Map.Entry<YearMonth, List<Row>> entry : byMonth.entrySet()) {
            boolean single = entry.getValue().size() == 1;
            boolean next = !current.isEmpty() && current.getLast().plusMonths(1).equals(entry.getKey());
            if (!single) {
                current = new ArrayList<>();
                continue;
            }
            if (!next) {
                current = new ArrayList<>();
            }
            current.add(entry.getKey());
            if (current.size() > best.size()) {
                best = List.copyOf(current);
            }
        }
        if (best.size() < MIN_MONTHS) {
            return null;
        }
        List<Row> run = best.stream().map(month -> byMonth.get(month).getFirst()).toList();
        // A row of the group between the first and the last row of the run means more than one payment a month
        LocalDate first = run.getFirst().date();
        LocalDate last = run.getLast().date();
        boolean others = rows.stream().anyMatch(row -> !run.contains(row)
                && !row.date().isBefore(first) && !row.date().isAfter(last));
        if (others) {
            return null;
        }
        long exact = rows.stream().filter(row -> row.date().getDayOfMonth() == Math.min(day, row.date().lengthOfMonth())).count();
        Row latest = run.getLast();
        return new Run(new Suggestion(run.stream().map(Row::index).toList(), due(best.getFirst(), day), latest.amount(),
                latest.description()), exact);
    }

    private static LocalDate due(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
