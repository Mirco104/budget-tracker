package io.github.mirco104.budget.bankaccount;

/** used is true when a transaction or series is booked on the account, which then can only be archived (R26). */
public record BankAccountResponse(
        Long id, String name, String iban, boolean defaultAccount, boolean archived, boolean used, AccountRole role) {

    static BankAccountResponse from(BankAccount account, boolean used) {
        return new BankAccountResponse(account.getId(), account.getName(), account.getIban(),
                account.isDefaultAccount(), account.isArchived(), used, account.getRole());
    }
}
