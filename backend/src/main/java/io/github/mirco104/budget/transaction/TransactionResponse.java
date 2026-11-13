package io.github.mirco104.budget.transaction;

import io.github.mirco104.budget.category.Category;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A booking of the transaction list. Only real bookings appear (P1). A transfer (R27) has no category; auto marks an
 * entry the app made from the plan (P6); fix marks an expense that fulfils a term (P5).
 */
public record TransactionResponse(
        Long id,
        BigDecimal amount,
        String description,
        LocalDate date,
        Long categoryId,
        String categoryName,
        Long accountId,
        String accountName,
        boolean transfer,
        boolean auto,
        boolean fix,
        java.util.UUID reserveItemKey,
        Long savingGoalId,
        boolean saving,
        Long transferPeerId,
        String transferPeerAccountName) {

    public static TransactionResponse from(Transaction transaction, boolean fix) {
        Category category = transaction.getCategory();
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getDate(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                transaction.isTransfer(),
                transaction.isAuto(),
                fix,
                transaction.getReserveItemKey(),
                transaction.getSavingGoal() == null ? null : transaction.getSavingGoal().getId(),
                transaction.isSaving(),
                transaction.getTransferPeer() == null ? null : transaction.getTransferPeer().getId(),
                transaction.getTransferPeer() == null ? null : transaction.getTransferPeer().getAccount().getName());
    }
}
