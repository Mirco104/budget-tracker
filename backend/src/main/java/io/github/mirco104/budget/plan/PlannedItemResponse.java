package io.github.mirco104.budget.plan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * One version of a planned item. amount is the real amount per payment with its sign, monthlyShare its monthly share
 * without sign (P2), group follows from the fields (P4). validTo null = open-ended.
 */
public record PlannedItemResponse(
        long id,
        UUID itemKey,
        String name,
        BigDecimal amount,
        boolean saving,
        Long categoryId,
        String categoryName,
        long accountId,
        String accountName,
        RhythmUnit rhythmUnit,
        Integer rhythmCount,
        LocalDate anchorDate,
        EntryMode entryMode,
        boolean workdaysOnly,
        YearMonth validFrom,
        YearMonth validTo,
        PlanCalculator.Group group,
        BigDecimal monthlyShare) {

    static PlannedItemResponse from(PlannedItem item) {
        return new PlannedItemResponse(
                item.getId(),
                item.getItemKey(),
                item.getName(),
                item.getAmount(),
                item.isSaving(),
                item.getCategory() == null ? null : item.getCategory().getId(),
                item.getCategory() == null ? null : item.getCategory().getName(),
                item.getAccount().getId(),
                item.getAccount().getName(),
                item.getRhythmUnit(),
                item.getRhythmCount(),
                item.getAnchorDate(),
                item.getEntryMode(),
                item.isWorkdaysOnly(),
                item.getValidFrom(),
                item.getValidTo(),
                PlanCalculator.groupOf(item.data()),
                PlanCalculator.monthlyShare(item.getAmount(), item.getRhythmUnit(), item.getRhythmCount()));
    }
}
