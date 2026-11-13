package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.common.NotFoundException;
import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.transaction.TransactionRepository;
import io.github.mirco104.budget.user.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Terms of the planned items of the signed-in user (P5), their status, automatic entries (P6), tick-off and skip (P7)
 * and the links to bookings (section 19.3).
 */
@Service
public class TermService {

    /** Bookings this many days around a period can fulfil its terms (largest tolerance plus a weekend). */
    private static final int MATCH_WINDOW_DAYS = 7;

    private final PlannedItemRepository itemRepository;
    private final ItemTermRepository termRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public TermService(
            PlannedItemRepository itemRepository, ItemTermRepository termRepository,
            TransactionRepository transactionRepository, CurrentUser currentUser, Clock clock) {
        this.itemRepository = itemRepository;
        this.termRepository = termRepository;
        this.transactionRepository = transactionRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** A term with its item version and its status (P5). */
    public record Evaluated(PlannedItem item, Fulfillment.Term term, Fulfillment.Status status, Long transactionId) {
    }

    private record Evaluation(List<Evaluated> terms, Set<Long> fixIds) {
    }

    /** P5: every term with its planned date from and to inclusive, by due date, with status. */
    @Transactional(readOnly = true)
    public List<Evaluated> terms(LocalDate from, LocalDate to) {
        return evaluate(from, to).terms();
    }

    /** P5: the ids of the bookings from and to inclusive that fulfil a term and are expenses (fix). */
    @Transactional(readOnly = true)
    public Set<Long> fixIds(LocalDate from, LocalDate to) {
        return evaluate(from.minusDays(MATCH_WINDOW_DAYS), to.plusDays(MATCH_WINDOW_DAYS)).fixIds();
    }

    private Evaluation evaluate(LocalDate from, LocalDate to) {
        Map<String, ItemTerm> rows = new HashMap<>();
        termRepository.findBetween(currentUser.get(), from, to)
                .forEach(row -> rows.put(key(row.getItemKey(), row.getTermDate()), row));
        List<Fulfillment.Term> terms = new ArrayList<>();
        Map<String, PlannedItem> itemOf = new HashMap<>();
        for (PlannedItem item : itemRepository.findAllOf(currentUser.get())) {
            TermSchedule schedule = item.schedule();
            if (schedule == null) {
                continue;
            }
            for (LocalDate date : schedule.termsBetween(from, to)) {
                ItemTerm row = rows.get(key(item.getItemKey(), date));
                Long linked = row == null || row.getTransaction() == null ? null : row.getTransaction().getId();
                Fulfillment.Term term = term(item, date, linked, row != null && linked == null);
                terms.add(term);
                itemOf.put(term.key(), item);
            }
        }
        List<Fulfillment.Booking> bookings = transactionRepository
                .findByOwnerAndDateBetweenOrderByDateDescIdDesc(
                        currentUser.get(), from.minusDays(MATCH_WINDOW_DAYS), to.plusDays(MATCH_WINDOW_DAYS))
                .stream()
                .map(t -> new Fulfillment.Booking(t.getId(), t.getDate(), t.getAmount(),
                        t.getCounterparty() != null ? t.getCounterparty() + " " + t.getDescription() : t.getDescription(),
                        t.getCategory() == null ? null : t.getCategory().getId()))
                .toList();
        Fulfillment.Result result = Fulfillment.of(terms, bookings, LocalDate.now(clock));
        List<Evaluated> evaluated = terms.stream()
                .map(term -> new Evaluated(itemOf.get(term.key()), term, result.status(term), result.fulfilledBy(term)))
                .sorted(Comparator.comparing((Evaluated e) -> e.term().dueDate()).thenComparing(e -> e.item().getName()))
                .toList();
        return new Evaluation(evaluated, result.fixIds());
    }

    /**
     * P6: enters every due term of an automatic item that is not fulfilled or handled yet, from the day the item was
     * created on. Saving items are entered as a transfer until the kind Sparen exists (AP 27f).
     */
    @Transactional
    public int enterDue() {
        LocalDate today = LocalDate.now(clock);
        List<PlannedItem> automatic = itemRepository.findAllOf(currentUser.get()).stream()
                .filter(item -> item.getEntryMode() == EntryMode.AUTO && item.schedule() != null)
                .toList();
        LocalDate first = automatic.stream().map(PlannedItem::getAutoFrom).min(Comparator.naturalOrder()).orElse(null);
        if (first == null || first.isAfter(today)) {
            return 0;
        }
        int entered = 0;
        for (Evaluated e : evaluate(first, today).terms()) {
            PlannedItem item = e.item();
            boolean open = e.status() == Fulfillment.Status.EXPECTED || e.status() == Fulfillment.Status.MISSED;
            if (open && item.getEntryMode() == EntryMode.AUTO && !e.term().date().isBefore(item.getAutoFrom())
                    && !e.term().dueDate().isAfter(today)) {
                Transaction booking = book(item, item.getAmount(), e.term().dueDate());
                booking.markAuto();
                link(item.getItemKey(), e.term().date(), booking);
                entered++;
            }
        }
        return entered;
    }

    /** P7 "Ist bezahlt": books the term with the planned or the given amount and date and links it. */
    @Transactional
    public Transaction pay(long itemId, LocalDate date, BigDecimal amount, LocalDate bookingDate) {
        PlannedItem item = termItem(itemId, date, "date");
        if (termRepository.findByOwnerAndItemKeyAndTermDate(currentUser.get(), item.getItemKey(), date).isPresent()) {
            throw new InvalidFieldException("date", "Dieser Termin ist schon erledigt.");
        }
        if (amount != null && amount.signum() == 0) {
            throw new InvalidFieldException("amount", "Der Betrag darf nicht 0 sein.");
        }
        LocalDate due = item.isWorkdaysOnly() ? TermSchedule.nextWorkingDay(date) : date;
        BigDecimal value = amount == null ? item.getAmount()
                : amount.abs().multiply(BigDecimal.valueOf(item.getAmount().signum())).setScale(2, RoundingMode.HALF_UP);
        Transaction booking = book(item, value, bookingDate == null ? due : bookingDate);
        link(item.getItemKey(), date, booking);
        return booking;
    }

    /** P7 "Rückgängig": deletes the booking of a paid term and the link; the term is expected again. */
    @Transactional
    public void unpay(long itemId, LocalDate date) {
        PlannedItem item = termItem(itemId, date, "date");
        ItemTerm row = termRepository.findByOwnerAndItemKeyAndTermDate(currentUser.get(), item.getItemKey(), date)
                .orElseThrow(() -> new NotFoundException("Der Termin ist nicht abgehakt."));
        Transaction booking = row.getTransaction();
        termRepository.delete(row);
        if (booking != null) {
            transactionRepository.delete(booking);
        }
    }

    /** P7 "Diesen Monat fällt es weg". */
    @Transactional
    public void skip(long itemId, LocalDate date) {
        PlannedItem item = termItem(itemId, date, "date");
        ItemTerm row = termRepository.findByOwnerAndItemKeyAndTermDate(currentUser.get(), item.getItemKey(), date)
                .orElseGet(() -> new ItemTerm(currentUser.get(), item.getItemKey(), date));
        if (row.getTransaction() != null) {
            throw new InvalidFieldException("date", "Der Termin ist schon bezahlt.");
        }
        row.skip();
        termRepository.save(row);
    }

    /** Takes back a skip; a deleted automatic entry counts as skipped too and is entered again (P6). */
    @Transactional
    public void unskip(long itemId, LocalDate date) {
        PlannedItem item = termItem(itemId, date, "date");
        termRepository.findByOwnerAndItemKeyAndTermDate(currentUser.get(), item.getItemKey(), date)
                .filter(row -> row.getTransaction() == null)
                .ifPresent(termRepository::delete);
    }

    /** The ids among the given ones of bookings linked to a term. */
    @Transactional(readOnly = true)
    public Set<Long> linkedIds(Collection<Long> transactionIds) {
        return transactionIds.isEmpty() ? Set.of() : new HashSet<>(termRepository.findLinkedTransactionIds(transactionIds));
    }

    /** P5: open terms (not handled) with their planned date from and to inclusive, by due date. */
    @Transactional(readOnly = true)
    public List<TermMatcher.Planned> openBetween(LocalDate from, LocalDate to) {
        Set<String> handled = new HashSet<>();
        termRepository.findBetween(currentUser.get(), from, to)
                .forEach(term -> handled.add(key(term.getItemKey(), term.getTermDate())));
        List<TermMatcher.Planned> open = new ArrayList<>();
        for (PlannedItem item : itemRepository.findAllOf(currentUser.get())) {
            TermSchedule schedule = item.schedule();
            if (schedule == null) {
                continue;
            }
            for (LocalDate date : schedule.termsBetween(from, to)) {
                if (!handled.contains(key(item.getItemKey(), date))) {
                    Fulfillment.Term term = term(item, date, null, false);
                    open.add(new TermMatcher.Planned(item.getId(), item.getItemKey(), date, term.dueDate(),
                            item.getAmount(), item.getName(), term.categoryId(), term.toleranceDays()));
                }
            }
        }
        open.sort(Comparator.comparing(TermMatcher.Planned::dueDate).thenComparingLong(TermMatcher.Planned::itemId));
        return open;
    }

    /**
     * Links the booking to the term of the item version on the planned date; 400 on the field when the item is
     * unknown or another user's (R15), the date is no term of it, or the term is handled already.
     */
    @Transactional
    public void link(long itemId, LocalDate date, Transaction transaction, String field) {
        PlannedItem item = termItem(itemId, date, field);
        if (termRepository.findByOwnerAndItemKeyAndTermDate(currentUser.get(), item.getItemKey(), date).isPresent()) {
            throw new InvalidFieldException(field, "Dieser Termin ist schon erledigt.");
        }
        link(item.getItemKey(), date, transaction);
    }

    /** Links without checks, for terms just calculated by the caller. */
    @Transactional
    public void link(UUID itemKey, LocalDate date, Transaction transaction) {
        ItemTerm term = new ItemTerm(currentUser.get(), itemKey, date);
        term.link(transaction);
        termRepository.save(term);
    }

    private PlannedItem termItem(long itemId, LocalDate date, String field) {
        PlannedItem item = itemRepository.findByIdAndOwner(itemId, currentUser.get())
                .orElseThrow(() -> new InvalidFieldException(field, "Den Posten gibt es nicht."));
        if (item.schedule() == null || !item.schedule().isTerm(date)) {
            throw new InvalidFieldException(field, "Der Posten hat an diesem Tag keinen Termin.");
        }
        return item;
    }

    // A saving item has no category: its booking is a transfer until the kind Sparen exists (AP 27f)
    private Transaction book(PlannedItem item, BigDecimal amount, LocalDate date) {
        return transactionRepository.save(new Transaction(
                currentUser.get(), amount, item.getName(), date, item.getCategory(), item.getAccount()));
    }

    static Fulfillment.Term term(PlannedItem item, LocalDate date, Long linked, boolean skipped) {
        LocalDate due = item.isWorkdaysOnly() ? TermSchedule.nextWorkingDay(date) : date;
        return new Fulfillment.Term(item.getId(), item.getItemKey(), date, due, item.getAmount(), item.getName(),
                item.getCategory() == null ? null : item.getCategory().getId(),
                TermSchedule.toleranceDays(item.getRhythmUnit(), item.getRhythmCount()), linked, skipped);
    }

    private static String key(UUID itemKey, LocalDate date) {
        return itemKey + "|" + date;
    }
}
