package io.github.mirco104.budget.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * R38: budget suggestions of the setup wizard. From the bookings of the 6 full months before the current month (from
 * the month of the first booking in that window on), the net amount per group: the calculation category of a booking
 * with a category, otherwise the top part of its bank category. A negative net gives the average per month and a
 * proposal in whole euros. Groups that already have a budget item drop out. The caller passes only bookings that count
 * (no transfer, no saving, no booking that fulfils a term).
 */
public final class BudgetSuggester {

    static final int MONTHS = 6;
    private static final String BANK_SEPARATOR = " › ";

    /** A booking; categoryId and categoryName are the calculation category (R32) or null, bankCategory as stored. */
    public record Booking(LocalDate date, BigDecimal amount, Long categoryId, String categoryName, String bankCategory) {
    }

    /** A suggestion; categoryId or bankCategory is set, average with scale 2, proposal in whole euros. */
    public record Suggestion(
            String name, Long categoryId, String bankCategory, BigDecimal average, BigDecimal proposal, int months) {
    }

    private BudgetSuggester() {
    }

    public static List<Suggestion> suggest(List<Booking> bookings, YearMonth current, Set<Long> budgeted) {
        YearMonth from = current.minusMonths(MONTHS);
        YearMonth to = current.minusMonths(1);
        List<Booking> inWindow = bookings.stream()
                .filter(b -> {
                    YearMonth month = YearMonth.from(b.date());
                    return !month.isBefore(from) && !month.isAfter(to);
                })
                .toList();
        if (inWindow.isEmpty()) {
            return List.of();
        }
        YearMonth first = inWindow.stream().map(b -> YearMonth.from(b.date())).min(Comparator.naturalOrder()).orElseThrow();
        int months = (int) first.until(to, java.time.temporal.ChronoUnit.MONTHS) + 1;

        Map<String, Group> groups = new LinkedHashMap<>();
        for (Booking booking : inWindow) {
            Group group;
            if (booking.categoryId() != null) {
                if (budgeted.contains(booking.categoryId())) {
                    continue;
                }
                group = groups.computeIfAbsent("c" + booking.categoryId(),
                        k -> new Group(booking.categoryName(), booking.categoryId(), null));
            } else {
                String top = topPart(booking.bankCategory());
                if (top == null) {
                    continue;
                }
                group = groups.computeIfAbsent("b" + top, k -> new Group(top, null, top));
            }
            group.net = group.net.add(booking.amount());
        }

        List<Suggestion> result = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group.net.signum() >= 0) {
                continue;
            }
            BigDecimal average = group.net.negate().divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
            BigDecimal proposal = average.setScale(0, RoundingMode.HALF_UP);
            result.add(new Suggestion(group.name, group.categoryId, group.bankCategory, average, proposal, months));
        }
        result.sort(Comparator.comparing(Suggestion::average).reversed());
        return result;
    }

    // The part before " › " (R30), null for an empty bank category
    private static String topPart(String bankCategory) {
        if (bankCategory == null) {
            return null;
        }
        int at = bankCategory.indexOf(BANK_SEPARATOR);
        String top = (at < 0 ? bankCategory : bankCategory.substring(0, at)).trim();
        return top.isEmpty() ? null : top;
    }

    private static final class Group {
        private final String name;
        private final Long categoryId;
        private final String bankCategory;
        private BigDecimal net = BigDecimal.ZERO;

        private Group(String name, Long categoryId, String bankCategory) {
            this.name = name;
            this.categoryId = categoryId;
            this.bankCategory = bankCategory;
        }
    }
}
