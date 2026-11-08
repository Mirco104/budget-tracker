package io.github.mirco104.budget.importer;

import io.github.mirco104.budget.bankaccount.Iban;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Transfers between own accounts (R27) and possible duplicates of bookings entered by hand (R30) among the new rows of
 * an import. No Spring dependencies.
 */
public final class ImportMatcher {

    /** Days between a transfer and its counterpart (R27). */
    static final int TRANSFER_DAYS = 3;
    /** Days between a row and a booking by hand with the same amount (R30). */
    static final int DUPLICATE_DAYS = 2;

    /** A new row of the file; accountId is the own account it goes to, null when a new account is created for it. */
    public record Row(int index, String source, Long accountId, LocalDate date, BigDecimal amount, String counterpartyIban) {

        public Row(int index, String source, Long accountId, LocalDate date, String amount, String counterpartyIban) {
            this(index, source, accountId, date, new BigDecimal(amount), counterpartyIban);
        }
    }

    /** A stored single transaction that is no transfer; imported is true when it came from an import. */
    public record Existing(long id, long accountId, LocalDate date, BigDecimal amount, boolean imported) {

        public Existing(long id, long accountId, LocalDate date, String amount, boolean imported) {
            this(id, accountId, date, new BigDecimal(amount), imported);
        }
    }

    public record OwnAccount(long id, String iban) {
    }

    /**
     * certain for the IBAN of an own account (R27 a); peerIndex is a row of the file and peerTransactionId a stored
     * booking with the opposite amount (R27 b), at most one of them.
     */
    public record Transfer(boolean certain, Integer peerIndex, Long peerTransactionId) {
    }

    private ImportMatcher() {
    }

    /** R27: the rows that are transfers, by index, without stored transfers as counterparts. */
    public static Map<Integer, Transfer> transfers(List<Row> rows, List<Existing> existing, List<OwnAccount> accounts) {
        return transfers(rows, existing, List.of(), accounts);
    }

    /**
     * R27: the rows that are transfers, by index. existing are the stored bookings that are no transfer; of
     * storedTransfers only those without their other side among them are counterparts (a transfer booked by hand whose
     * other side comes with the file).
     */
    public static Map<Integer, Transfer> transfers(List<Row> rows, List<Existing> existing, List<Existing> storedTransfers,
            List<OwnAccount> accounts) {
        List<Existing> candidates = new java.util.ArrayList<>(existing);
        candidates.addAll(openTransfers(storedTransfers));
        Map<Integer, Boolean> certain = new HashMap<>();
        for (Row row : rows) {
            String iban = Iban.normalize(row.counterpartyIban());
            boolean own = iban != null && accounts.stream()
                    .anyMatch(account -> iban.equals(account.iban()) && !Objects.equals(account.id(), row.accountId()));
            certain.put(row.index(), own);
        }

        Map<Integer, Integer> rowPeers = new HashMap<>();
        Map<Integer, Long> storedPeers = new HashMap<>();
        Set<Long> usedStored = new HashSet<>();
        // Sorted once and grouped by amount, so only candidates with the opposite amount are compared (in the same order)
        List<Row> sorted = byDate(rows);
        Map<BigDecimal, List<Row>> rowsByAmount = byAmount(sorted, Row::amount);
        Map<BigDecimal, List<Existing>> storedByAmount = byAmount(
                candidates.stream().sorted(Comparator.comparingLong(Existing::id)).toList(), Existing::amount);
        for (Row row : sorted) {
            if (rowPeers.containsKey(row.index())) {
                continue;
            }
            BigDecimal opposite = key(row.amount().negate());
            Row bestRow = null;
            long bestRowDays = Long.MAX_VALUE;
            for (Row other : rowsByAmount.getOrDefault(opposite, List.of())) {
                long days = days(row.date(), other.date());
                if (other.index() != row.index() && !rowPeers.containsKey(other.index())
                        && !other.source().equals(row.source()) && !sameAccount(row, other)
                        && days <= TRANSFER_DAYS && days < bestRowDays) {
                    bestRow = other;
                    bestRowDays = days;
                }
            }
            Existing bestStored = null;
            long bestStoredDays = Long.MAX_VALUE;
            for (Existing stored : storedByAmount.getOrDefault(opposite, List.of())) {
                long days = days(row.date(), stored.date());
                if (!usedStored.contains(stored.id()) && !Objects.equals(stored.accountId(), row.accountId())
                        && days <= TRANSFER_DAYS && days < bestStoredDays) {
                    bestStored = stored;
                    bestStoredDays = days;
                }
            }
            // The closest counterpart; on a tie the row of the file
            if (bestRow != null && bestRowDays <= bestStoredDays) {
                rowPeers.put(row.index(), bestRow.index());
                rowPeers.put(bestRow.index(), row.index());
            } else if (bestStored != null) {
                storedPeers.put(row.index(), bestStored.id());
                usedStored.add(bestStored.id());
            }
        }

        Map<Integer, Transfer> result = new LinkedHashMap<>();
        for (Row row : rows) {
            boolean sure = certain.get(row.index());
            Integer peerIndex = rowPeers.get(row.index());
            Long peerTransaction = storedPeers.get(row.index());
            if (sure || peerIndex != null || peerTransaction != null) {
                result.put(row.index(), new Transfer(sure, peerIndex, peerTransaction));
            }
        }
        return result;
    }

    /** R30: per row index the id of a booking by hand with the same amount, at most 2 days away; each one once. */
    public static Map<Integer, Long> possibleDuplicates(List<Row> rows, List<Existing> existing) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        Set<Long> used = new HashSet<>();
        Map<BigDecimal, List<Existing>> byHand = byAmount(existing.stream()
                .filter(stored -> !stored.imported())
                .sorted(Comparator.comparingLong(Existing::id))
                .toList(), Existing::amount);
        for (Row row : byDate(rows)) {
            Existing best = null;
            long bestDays = Long.MAX_VALUE;
            for (Existing stored : byHand.getOrDefault(key(row.amount()), List.of())) {
                long days = days(row.date(), stored.date());
                if (!used.contains(stored.id()) && days <= DUPLICATE_DAYS && days < bestDays) {
                    best = stored;
                    bestDays = days;
                }
            }
            if (best != null) {
                result.put(row.index(), best.id());
                used.add(best.id());
            }
        }
        return result;
    }

    // R27: the stored transfers without another stored transfer as their other side (opposite amount, another account,
    // at most 3 days; the closest first, each once), because the app does not link the two sides
    private static List<Existing> openTransfers(List<Existing> storedTransfers) {
        List<Existing> sorted = storedTransfers.stream()
                .sorted(Comparator.comparing(Existing::date).thenComparingLong(Existing::id))
                .toList();
        Map<BigDecimal, List<Existing>> byAmount = byAmount(sorted, Existing::amount);
        Set<Long> paired = new HashSet<>();
        for (Existing transfer : sorted) {
            if (paired.contains(transfer.id())) {
                continue;
            }
            Existing best = null;
            long bestDays = Long.MAX_VALUE;
            for (Existing other : byAmount.getOrDefault(key(transfer.amount().negate()), List.of())) {
                long days = days(transfer.date(), other.date());
                if (!paired.contains(other.id()) && other.accountId() != transfer.accountId()
                        && days <= TRANSFER_DAYS && days < bestDays) {
                    best = other;
                    bestDays = days;
                }
            }
            if (best != null) {
                paired.add(transfer.id());
                paired.add(best.id());
            }
        }
        return sorted.stream().filter(transfer -> !paired.contains(transfer.id())).toList();
    }

    // Two sources the user put on the same account are no transfer between accounts
    private static boolean sameAccount(Row a, Row b) {
        return a.accountId() != null && a.accountId().equals(b.accountId());
    }

    // Equal amounts by compareTo (2.50 and 2.5) have the same key
    private static BigDecimal key(BigDecimal amount) {
        return amount.stripTrailingZeros();
    }

    // The items per amount, each list in the order of the given list
    private static <T> Map<BigDecimal, List<T>> byAmount(List<T> items, java.util.function.Function<T, BigDecimal> amount) {
        Map<BigDecimal, List<T>> result = new HashMap<>();
        for (T item : items) {
            result.computeIfAbsent(key(amount.apply(item)), k -> new java.util.ArrayList<>()).add(item);
        }
        return result;
    }

    private static List<Row> byDate(List<Row> rows) {
        return rows.stream().sorted(Comparator.comparing(Row::date).thenComparingInt(Row::index)).toList();
    }

    private static long days(LocalDate a, LocalDate b) {
        return Math.abs(ChronoUnit.DAYS.between(a, b));
    }
}
