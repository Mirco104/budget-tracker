package io.github.mirco104.budget.plan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A term of a planned item (P5): date is the planned date (with itemId the key for tick-off and skip), dueDate the
 * date after the working-day shift. transactionId is the booking that fulfils it, linked or matched.
 */
public record TermResponse(
        long itemId,
        UUID itemKey,
        String name,
        LocalDate date,
        LocalDate dueDate,
        BigDecimal amount,
        Long categoryId,
        String categoryName,
        long accountId,
        EntryMode entryMode,
        PlanCalculator.Group group,
        Fulfillment.Status status,
        Long transactionId,
        boolean linked) {

    static TermResponse from(TermService.Evaluated e) {
        PlannedItem item = e.item();
        return new TermResponse(
                item.getId(),
                item.getItemKey(),
                item.getName(),
                e.term().date(),
                e.term().dueDate(),
                item.getAmount(),
                item.getCategory() == null ? null : item.getCategory().getId(),
                item.getCategory() == null ? null : item.getCategory().getPath(),
                item.getAccount().getId(),
                item.getEntryMode(),
                PlanCalculator.groupOf(item.data()),
                e.status(),
                e.transactionId(),
                e.term().linkedTransactionId() != null);
    }
}
