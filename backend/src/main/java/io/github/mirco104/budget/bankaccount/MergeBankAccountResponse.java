package io.github.mirco104.budget.bankaccount;

/** Ticket #139: the target account after the merge and what happened, for the confirmation. */
public record MergeBankAccountResponse(
        BankAccountResponse account,
        int movedTransactions,
        int deletedTransfers) {
}
