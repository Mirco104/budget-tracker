package io.github.mirco104.budget.plan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rule P5 from docs/spezifikation.md, section 19: the status of terms and which bookings fulfil them. A linked
 * booking fulfils its term; the other terms are matched with the bookings not linked to any term (match of R28 with
 * the tolerance of the term). A term without a booking is expected until 5 days after its due date, then missed.
 * An expense that fulfils a term is fix. No Spring dependencies.
 */
public final class Fulfillment {

    /** Days after the due date until a term counts as missed (P5). */
    static final int MISSED_AFTER_DAYS = 5;

    /** Status of a term (P5). */
    public enum Status {
        EXPECTED, FULFILLED, MISSED, SKIPPED
    }

    /**
     * A term: date is the planned date (key with itemKey), dueDate after the working-day shift. linkedTransactionId
     * is the booking of its row, skipped is true for a skipped term or one whose automatic entry was deleted.
     */
    public record Term(
            long itemId, UUID itemKey, LocalDate date, LocalDate dueDate, BigDecimal amount, String name,
            Long categoryId, int toleranceDays, Long linkedTransactionId, boolean skipped) {

        String key() {
            return itemKey + "|" + date;
        }
    }

    /** A booking; categoryId is null exactly for a transfer (R27), which fulfils no term. */
    public record Booking(long id, LocalDate date, BigDecimal amount, String text, Long categoryId) {
    }

    /** Status and fulfilling booking per term, and the ids of the fix bookings. */
    public record Result(Map<String, Status> statuses, Map<String, Long> fulfilling, Set<Long> fixIds) {

        public Status status(Term term) {
            return statuses.get(term.key());
        }

        public Long fulfilledBy(Term term) {
            return fulfilling.get(term.key());
        }
    }

    private Fulfillment() {
    }

    public static Result of(List<Term> terms, List<Booking> bookings, LocalDate today) {
        Map<String, Status> statuses = new HashMap<>();
        Map<String, Long> fulfilling = new HashMap<>();
        Map<Long, Booking> byId = new HashMap<>();
        bookings.forEach(b -> byId.put(b.id(), b));
        Set<Long> linked = new HashSet<>();
        terms.stream().map(Term::linkedTransactionId).filter(id -> id != null).forEach(linked::add);

        List<TermMatcher.Planned> open = new ArrayList<>();
        Map<String, Term> openByKey = new HashMap<>();
        for (Term term : terms) {
            if (term.linkedTransactionId() != null) {
                statuses.put(term.key(), Status.FULFILLED);
                fulfilling.put(term.key(), term.linkedTransactionId());
            } else if (term.skipped()) {
                statuses.put(term.key(), Status.SKIPPED);
            } else {
                open.add(new TermMatcher.Planned(term.itemId(), term.itemKey(), term.date(), term.dueDate(),
                        term.amount(), term.name(), term.categoryId(), term.toleranceDays()));
                openByKey.put(term.key(), term);
            }
        }

        // Transfers fulfil no term (R27)
        List<Booking> free = bookings.stream()
                .filter(b -> !linked.contains(b.id()) && b.categoryId() != null)
                .toList();
        List<TermMatcher.Booking> candidates = new ArrayList<>();
        for (int i = 0; i < free.size(); i++) {
            Booking b = free.get(i);
            candidates.add(new TermMatcher.Booking(i, b.date(), b.amount(), b.text(), b.categoryId()));
        }
        TermMatcher.match(candidates, open).forEach((index, planned) -> {
            String key = planned.itemKey() + "|" + planned.date();
            statuses.put(key, Status.FULFILLED);
            fulfilling.put(key, free.get(index).id());
        });
        openByKey.forEach((key, term) -> statuses.computeIfAbsent(key, k ->
                term.dueDate().plusDays(MISSED_AFTER_DAYS).isBefore(today) ? Status.MISSED : Status.EXPECTED));

        Set<Long> fix = new HashSet<>();
        fulfilling.values().forEach(id -> {
            Booking b = byId.get(id);
            if (b != null && b.amount().signum() < 0) {
                fix.add(id);
            }
        });
        return new Result(statuses, fulfilling, fix);
    }
}
