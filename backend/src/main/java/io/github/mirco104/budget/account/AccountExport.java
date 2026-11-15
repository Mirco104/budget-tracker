package io.github.mirco104.budget.account;

import io.github.mirco104.budget.plan.EntryMode;
import io.github.mirco104.budget.plan.RhythmUnit;
import io.github.mirco104.budget.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** US16: all data of the signed-in user as JSON. No ids of other users, no password hash. */
public record AccountExport(
        Instant exportedAt,
        Account account,
        List<BankAccountExport> bankAccounts,
        List<CategoryExport> categories,
        List<TransactionExport> transactions,
        List<PlannedItemExport> plannedItems) {

    public record Account(String email, String displayName, Role role, Instant createdAt) {
    }

    public record BankAccountExport(String name, String iban, boolean defaultAccount, boolean archived) {
    }

    /** R32: parent is the name of the parent category, null for a top-level category. */
    public record CategoryExport(String name, String parent, boolean archived) {
    }

    public record TransactionExport(
            LocalDate date, BigDecimal amount, String description, String category, String account,
            boolean transfer, boolean auto, String counterparty, String bankCategory) {
    }

    /** One version of a planned item with the handled terms of the item (section 19.3). */
    public record PlannedItemExport(
            UUID itemKey, String name, BigDecimal amount, boolean saving, String category, String account,
            RhythmUnit rhythmUnit, Integer rhythmCount, LocalDate anchorDate, EntryMode entryMode,
            boolean workdaysOnly, YearMonth validFrom, YearMonth validTo, List<TermExport> terms) {
    }

    public record TermExport(LocalDate date, boolean linked, boolean skipped) {
    }
}
