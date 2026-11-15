package db.migration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Converts series into planned items (section 19.3, AP 27b, concept section 7). Every series becomes an item with
 * entry mode AUTO and a month rhythm; the parts of a split series share the item key. Past occurrences without an
 * exception become real bookings marked "auto" with a linked term, so earlier months keep their numbers. Suspended
 * occurrences become skipped terms, replaced occurrences (R28) linked terms. Category targets are not taken over
 * (decision 56). Written in Java because the occurrence dates cannot be generated in SQL for both H2 and PostgreSQL.
 * Never change this file once merged.
 */
public class V14__convert_series_to_items extends BaseJavaMigration {

    private record Series(
            long id, UUID seriesId, BigDecimal amount, String description, long categoryId, int months,
            LocalDate start, LocalDate end, long ownerId, long accountId) {
    }

    private record Mark(Long transactionId) {
    }

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        LocalDate today = LocalDate.now();
        List<Series> series = readSeries(connection);
        Map<Long, Map<LocalDate, Mark>> exceptions = readExceptions(connection);

        try (PreparedStatement item = connection.prepareStatement(
                     "insert into planned_items (owner_id, item_key, name, amount, saving, category_id, account_id,"
                             + " rhythm_unit, rhythm_count, anchor_date, entry_mode, workdays_only, valid_from, valid_to)"
                             + " values (?, ?, ?, ?, false, ?, ?, 'MONTH', ?, ?, 'AUTO', false, ?, ?)");
             PreparedStatement booking = connection.prepareStatement(
                     "insert into transactions (amount, description, booking_date, category_id, owner_id, account_id,"
                             + " transfer, auto) values (?, ?, ?, ?, ?, ?, false, true)",
                     new String[] {"id"});
             PreparedStatement term = connection.prepareStatement(
                     "insert into item_terms (owner_id, item_key, term_date, transaction_id, skipped)"
                             + " values (?, ?, ?, ?, ?)")) {
            for (Series s : series) {
                // An ended series without any occurrence has nothing to plan
                if (s.end() != null && s.start().isAfter(s.end())) {
                    continue;
                }
                List<LocalDate> occurrences = occurrences(s);
                item.setLong(1, s.ownerId());
                item.setObject(2, s.seriesId());
                item.setString(3, s.description());
                item.setBigDecimal(4, s.amount());
                item.setLong(5, s.categoryId());
                item.setLong(6, s.accountId());
                item.setInt(7, s.months());
                item.setDate(8, Date.valueOf(s.start()));
                item.setDate(9, Date.valueOf(YearMonth.from(s.start()).atDay(1)));
                // An end date ends the item in the month of its last occurrence, so the next part can start in
                // the following month without two versions in one month
                item.setDate(10, s.end() == null ? null : Date.valueOf(YearMonth.from(lastOccurrence(s)).atEndOfMonth()));
                item.executeUpdate();

                Map<LocalDate, Mark> ofSeries = exceptions.getOrDefault(s.id(), Map.of());
                for (LocalDate date : occurrences) {
                    Mark exception = ofSeries.get(date);
                    Long transactionId;
                    boolean skipped;
                    if (exception != null) {
                        transactionId = exception.transactionId();
                        skipped = transactionId == null;
                    } else if (!date.isAfter(today)) {
                        transactionId = insertBooking(booking, s, date);
                        skipped = false;
                    } else {
                        continue;
                    }
                    term.setLong(1, s.ownerId());
                    term.setObject(2, s.seriesId());
                    term.setDate(3, Date.valueOf(date));
                    term.setObject(4, transactionId);
                    term.setBoolean(5, skipped);
                    term.executeUpdate();
                }
            }
        }
    }

    // R12: occurrence k lies at start + k × interval months, up to two years ahead (for future exceptions)
    private static List<LocalDate> occurrences(Series s) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate horizon = LocalDate.now().plusYears(2);
        LocalDate last = s.end() == null || s.end().isAfter(horizon) ? horizon : s.end();
        for (long k = 0; ; k++) {
            LocalDate date = s.start().plusMonths(k * s.months());
            if (date.isAfter(last)) {
                return dates;
            }
            dates.add(date);
        }
    }

    // The last occurrence on or before the end date of an ended series
    private static LocalDate lastOccurrence(Series s) {
        LocalDate last = s.start();
        for (long k = 1; ; k++) {
            LocalDate date = s.start().plusMonths(k * s.months());
            if (date.isAfter(s.end())) {
                return last;
            }
            last = date;
        }
    }

    private static Long insertBooking(PreparedStatement booking, Series s, LocalDate date) throws SQLException {
        booking.setBigDecimal(1, s.amount());
        booking.setString(2, s.description());
        booking.setDate(3, Date.valueOf(date));
        booking.setLong(4, s.categoryId());
        booking.setLong(5, s.ownerId());
        booking.setLong(6, s.accountId());
        booking.executeUpdate();
        try (ResultSet keys = booking.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }

    private static List<Series> readSeries(Connection connection) throws SQLException {
        List<Series> series = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select id, series_id, amount, description, category_id, recurrence_interval, start_date,"
                             + " end_date, owner_id, account_id from recurring_transactions order by start_date, id")) {
            while (rows.next()) {
                Date end = rows.getDate("end_date");
                series.add(new Series(
                        rows.getLong("id"),
                        rows.getObject("series_id", UUID.class),
                        rows.getBigDecimal("amount"),
                        rows.getString("description"),
                        rows.getLong("category_id"),
                        months(rows.getString("recurrence_interval")),
                        rows.getDate("start_date").toLocalDate(),
                        end == null ? null : end.toLocalDate(),
                        rows.getLong("owner_id"),
                        rows.getLong("account_id")));
            }
        }
        return series;
    }

    private static Map<Long, Map<LocalDate, Mark>> readExceptions(Connection connection) throws SQLException {
        Map<Long, Map<LocalDate, Mark>> exceptions = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select recurring_id, occurrence_date, transaction_id from recurring_exceptions")) {
            while (rows.next()) {
                long transactionId = rows.getLong("transaction_id");
                Long linked = rows.wasNull() ? null : transactionId;
                exceptions.computeIfAbsent(rows.getLong("recurring_id"), id -> new HashMap<>())
                        .put(rows.getDate("occurrence_date").toLocalDate(), new Mark(linked));
            }
        }
        return exceptions;
    }

    private static int months(String interval) {
        return switch (interval) {
            case "MONTHLY" -> 1;
            case "QUARTERLY" -> 3;
            case "HALF_YEARLY" -> 6;
            case "YEARLY" -> 12;
            default -> throw new IllegalStateException("Unknown interval " + interval);
        };
    }
}
