package io.github.mirco104.budget.plan;

import io.github.mirco104.budget.common.InvalidFieldException;
import io.github.mirco104.budget.transaction.Transaction;
import io.github.mirco104.budget.user.CurrentUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Terms of the planned items of the signed-in user (P5) and their links to bookings (section 19.3). */
@Service
public class TermService {

    private final PlannedItemRepository itemRepository;
    private final ItemTermRepository termRepository;
    private final CurrentUser currentUser;

    public TermService(PlannedItemRepository itemRepository, ItemTermRepository termRepository, CurrentUser currentUser) {
        this.itemRepository = itemRepository;
        this.termRepository = termRepository;
        this.currentUser = currentUser;
    }

    /** P5: terms with their planned date from and to inclusive that are not handled yet, by due date. */
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
                    open.add(planned(item, date));
                }
            }
        }
        open.sort(Comparator.comparing(TermMatcher.Planned::dueDate).thenComparingLong(TermMatcher.Planned::itemId));
        return open;
    }

    /** The ids among the given ones of bookings linked to a term. */
    @Transactional(readOnly = true)
    public Set<Long> linkedIds(Collection<Long> transactionIds) {
        return transactionIds.isEmpty() ? Set.of() : new HashSet<>(termRepository.findLinkedTransactionIds(transactionIds));
    }

    /**
     * Links the booking to the term of the item version on the planned date; 400 on the field when the item is
     * unknown or another user's (R15), the date is no term of it, or the term is handled already.
     */
    @Transactional
    public void link(long itemId, LocalDate date, Transaction transaction, String field) {
        PlannedItem item = itemRepository.findByIdAndOwner(itemId, currentUser.get())
                .orElseThrow(() -> new InvalidFieldException(field, "Den Posten gibt es nicht."));
        if (item.schedule() == null || !item.schedule().isTerm(date)) {
            throw new InvalidFieldException(field, "Der Posten hat an diesem Tag keinen Termin.");
        }
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

    static TermMatcher.Planned planned(PlannedItem item, LocalDate date) {
        LocalDate due = item.isWorkdaysOnly() ? TermSchedule.nextWorkingDay(date) : date;
        return new TermMatcher.Planned(
                item.getId(), item.getItemKey(), date, due, item.getAmount(), item.getName(),
                item.getCategory() == null ? null : item.getCategory().getId(),
                TermSchedule.toleranceDays(item.getRhythmUnit(), item.getRhythmCount()));
    }

    private static String key(UUID itemKey, LocalDate date) {
        return itemKey + "|" + date;
    }
}
